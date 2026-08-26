package com.stockflow.forecasting.operations

import com.stockflow.forecasting.application.CreateForecastRunRequest
import com.stockflow.forecasting.application.ForecastingService
import com.stockflow.forecasting.persistence.ForecastRunStatus
import com.stockflow.security.TenantAccessContext
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.sql.ResultSet
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.UUID

@Service
class ForecastOperationsService(private val jdbc:JdbcTemplate,private val forecasting:ForecastingService) {
    fun jobs(actor:TenantAccessContext)=jdbc.query("SELECT * FROM forecast_job WHERE tenant_id=? ORDER BY created_at DESC LIMIT 100",{rs,_->rs.job()},actor.tenantId)
    fun schedules(actor:TenantAccessContext)=jdbc.query("SELECT * FROM forecast_schedule WHERE tenant_id=? ORDER BY schedule_name",{rs,_->rs.schedule()},actor.tenantId)
    fun alerts(actor:TenantAccessContext)=jdbc.query("SELECT * FROM forecast_governance_alert WHERE tenant_id=? ORDER BY acknowledged,severity DESC,created_at DESC LIMIT 100",{rs,_->rs.alert()},actor.tenantId)

    @Transactional
    fun queue(actor:TenantAccessContext,body:QueueForecastJobRequest):ForecastJobView {
        validateScope(actor,body.warehouseId)
        val id=UUID.randomUUID(); val now=LocalDateTime.now()
        jdbc.update("""INSERT INTO forecast_job(job_id,tenant_id,status,as_of_date,horizon_days,history_days,warehouse_id,sku_id,scheduled_for,created_by)
            VALUES (?,?,'QUEUED',?,?,?,?,?,?,?)""",id,actor.tenantId,body.asOfDate,body.horizonDays,body.historyDays,body.warehouseId,body.skuId,now,actor.userId)
        return requireJob(actor,id)
    }

    @Transactional
    fun createSchedule(actor:TenantAccessContext,body:CreateForecastScheduleRequest):ForecastScheduleView {
        validateScope(actor,body.warehouseId)
        val cadence=body.cadence.uppercase(); if(cadence !in setOf("DAILY","WEEKLY")) bad("Cadence must be DAILY or WEEKLY")
        if(cadence=="WEEKLY"&&body.dayOfWeek==null) bad("dayOfWeek is required for weekly schedules")
        val zone=try{ZoneId.of(body.timezone)}catch(_:Exception){bad("Unknown timezone '${body.timezone}'")}
        val id=UUID.randomUUID(); val next=nextRun(cadence,body.dayOfWeek,body.runHour,body.runMinute,zone,LocalDateTime.now(zone))
        jdbc.update("""INSERT INTO forecast_schedule(schedule_id,tenant_id,schedule_name,cadence,day_of_week,run_hour,run_minute,timezone,horizon_days,history_days,warehouse_id,sku_id,active,next_run_at,created_by)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",id,actor.tenantId,body.scheduleName.trim(),cadence,body.dayOfWeek,body.runHour,body.runMinute,body.timezone,body.horizonDays,body.historyDays,body.warehouseId,body.skuId,body.active,next,actor.userId)
        return jdbc.query("SELECT * FROM forecast_schedule WHERE schedule_id=?",{rs,_->rs.schedule()},id).first()
    }

    @Transactional
    fun setScheduleActive(actor:TenantAccessContext,id:UUID,active:Boolean):ForecastScheduleView {
        val changed=jdbc.update("UPDATE forecast_schedule SET active=?,updated_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND schedule_id=?",active,actor.tenantId,id)
        if(changed!=1) notFound("Schedule was not found")
        return jdbc.query("SELECT * FROM forecast_schedule WHERE schedule_id=?",{rs,_->rs.schedule()},id).first()
    }

    @Transactional
    fun cancel(actor:TenantAccessContext,id:UUID):ForecastJobView {
        val changed=jdbc.update("UPDATE forecast_job SET status='CANCELLED',completed_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND job_id=? AND status='QUEUED'",actor.tenantId,id)
        if(changed!=1) throw ResponseStatusException(HttpStatus.CONFLICT,"Only a queued job can be cancelled")
        return requireJob(actor,id)
    }

    @Transactional
    fun retry(actor:TenantAccessContext,id:UUID):ForecastJobView {
        val old=requireJob(actor,id); if(old.status !in setOf("FAILED","CANCELLED","COMPLETED_WITH_ERRORS")) throw ResponseStatusException(HttpStatus.CONFLICT,"Only failed, cancelled or completed-with-errors jobs can be retried")
        if(old.attemptNumber>=10) throw ResponseStatusException(HttpStatus.CONFLICT,"Maximum retry attempts reached")
        val next=UUID.randomUUID()
        jdbc.update("""INSERT INTO forecast_job(job_id,tenant_id,parent_job_id,status,as_of_date,horizon_days,history_days,warehouse_id,sku_id,attempt_number,scheduled_for,created_by)
            VALUES (?,?,?,'QUEUED',?,?,?,?,?,?,CURRENT_TIMESTAMP,?)""",next,actor.tenantId,id,old.asOfDate,old.horizonDays,old.historyDays,old.warehouseId,old.skuId,old.attemptNumber+1,actor.userId)
        return requireJob(actor,next)
    }

    @Transactional
    fun acknowledge(actor:TenantAccessContext,id:UUID):ForecastGovernanceAlertView {
        val changed=jdbc.update("UPDATE forecast_governance_alert SET acknowledged=TRUE,acknowledged_by=?,acknowledged_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND alert_id=?",actor.userId,actor.tenantId,id)
        if(changed!=1) notFound("Alert was not found")
        return jdbc.query("SELECT * FROM forecast_governance_alert WHERE alert_id=?",{rs,_->rs.alert()},id).first()
    }

    fun processNext(actor:TenantAccessContext):ForecastJobView? { materializeDueSchedules(); return processOne(actor.tenantId) }

    @Scheduled(fixedDelayString="\${stockflow.forecast.operations.poll-ms:30000}",initialDelayString="\${stockflow.forecast.operations.initial-delay-ms:45000}")
    fun scheduledPoll(){ try { materializeDueSchedules(); processOne(null) } catch(_:Exception) { } }

    @Transactional
    fun materializeDueSchedules() {
        val due=jdbc.query("SELECT * FROM forecast_schedule WHERE active=TRUE AND next_run_at<=CURRENT_TIMESTAMP ORDER BY next_run_at FOR UPDATE SKIP LOCKED LIMIT 20",{rs,_->rs.scheduleRow()})
        due.forEach { s ->
            jdbc.update("""INSERT INTO forecast_job(job_id,tenant_id,schedule_id,status,horizon_days,history_days,warehouse_id,sku_id,scheduled_for,created_by)
                VALUES (?,?,?,'QUEUED',?,?,?,?,?,?) ON CONFLICT (schedule_id,scheduled_for) DO NOTHING""",UUID.randomUUID(),s.tenantId,s.id,s.horizon,s.history,s.warehouse,s.sku,s.next,s.createdBy)
            val zone=ZoneId.of(s.timezone); val next=nextRun(s.cadence,s.day,s.hour,s.minute,zone,s.next.plusMinutes(1))
            jdbc.update("UPDATE forecast_schedule SET next_run_at=?,updated_at=CURRENT_TIMESTAMP WHERE schedule_id=?",next,s.id)
        }
    }

    private fun processOne(tenantId:String?):ForecastJobView? {
        val job=claim(tenantId)?:return null
        return try {
            val run=forecasting.createRun(job.tenantId,CreateForecastRunRequest(job.asOfDate,job.horizonDays,job.historyDays,job.warehouseId,job.skuId))
            val status=if(run.status==ForecastRunStatus.COMPLETED)"COMPLETED" else if(run.status==ForecastRunStatus.COMPLETED_WITH_ERRORS)"COMPLETED_WITH_ERRORS" else "FAILED"
            jdbc.update("UPDATE forecast_job SET forecast_run_id=?,status=?,completed_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP,error_message=? WHERE job_id=?",run.forecastRunId,status,run.message,job.jobId)
            governance(job,run.forecastRunId,run.asOfDate,run.positionsFailed)
            jdbc.query("SELECT * FROM forecast_job WHERE job_id=?",{rs,_->rs.job()},job.jobId).first()
        } catch(error:Exception) {
            jdbc.update("UPDATE forecast_job SET status='FAILED',completed_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP,error_message=? WHERE job_id=?",(error.message?:"Forecast execution failed").take(2000),job.jobId)
            alert(job.tenantId,job.jobId,null,"RUN_FAILURE","CRITICAL",error.message?:"Forecast execution failed")
            jdbc.query("SELECT * FROM forecast_job WHERE job_id=?",{rs,_->rs.job()},job.jobId).first()
        }
    }

    private fun claim(tenantId:String?):JobRow? {
        val filter=if(tenantId==null)"" else "AND tenant_id=?"
        val sql="""UPDATE forecast_job SET status='RUNNING',started_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE job_id=(SELECT job_id FROM forecast_job WHERE status='QUEUED' AND scheduled_for<=CURRENT_TIMESTAMP $filter ORDER BY scheduled_for,created_at FOR UPDATE SKIP LOCKED LIMIT 1) RETURNING *"""
        return (if(tenantId==null)jdbc.query(sql,{rs,_->rs.jobRow()}) else jdbc.query(sql,{rs,_->rs.jobRow()},tenantId)).firstOrNull()
    }

    private fun governance(job:JobRow,runId:UUID,asOf:java.time.LocalDate,failed:Int){
        val age=java.time.temporal.ChronoUnit.DAYS.between(asOf,java.time.LocalDate.now())
        if(age>7) alert(job.tenantId,job.jobId,runId,"STALE_DATA",if(age>30)"CRITICAL" else "WARNING","Forecast source data is $age days old")
        val low=jdbc.queryForObject("SELECT COUNT(DISTINCT warehouse_id||':'||sku_id) FROM forecast_result WHERE forecast_run_id=? AND confidence='LOW'",Long::class.java,runId)?:0
        if(low>0) alert(job.tenantId,job.jobId,runId,"LOW_CONFIDENCE","WARNING","$low forecast positions have low confidence")
        if(failed>0) alert(job.tenantId,job.jobId,runId,"POSITION_FAILURES","WARNING","$failed forecast positions failed or were ineligible")
    }
    private fun alert(tenant:String,job:UUID,run:UUID?,type:String,severity:String,message:String)=jdbc.update("INSERT INTO forecast_governance_alert(alert_id,tenant_id,forecast_job_id,forecast_run_id,alert_type,severity,message) VALUES (?,?,?,?,?,?,?)",UUID.randomUUID(),tenant,job,run,type,severity,message.take(1000))
    private fun requireJob(actor:TenantAccessContext,id:UUID)=jdbc.query("SELECT * FROM forecast_job WHERE tenant_id=? AND job_id=?",{rs,_->rs.job()},actor.tenantId,id).firstOrNull()?:notFound("Forecast job was not found")
    private fun validateScope(actor:TenantAccessContext,warehouse:String?){if(warehouse!=null&&actor.warehouseIds.isNotEmpty()&&warehouse !in actor.warehouseIds)throw ResponseStatusException(HttpStatus.FORBIDDEN,"Caller is not authorised for warehouse '$warehouse'")}
    private fun nextRun(cadence:String,day:Int?,hour:Int,minute:Int,zone:ZoneId,from:LocalDateTime):LocalDateTime { var candidate=from.toLocalDate().atTime(hour,minute); if(cadence=="WEEKLY")candidate=candidate.with(TemporalAdjusters.nextOrSame(DayOfWeek.of(day!!))); if(!candidate.isAfter(from))candidate=if(cadence=="DAILY")candidate.plusDays(1) else candidate.plusWeeks(1); return candidate }
    private fun bad(message:String):Nothing=throw ResponseStatusException(HttpStatus.BAD_REQUEST,message)
    private fun notFound(message:String):Nothing=throw ResponseStatusException(HttpStatus.NOT_FOUND,message)
    private fun ResultSet.job()=ForecastJobView(UUID.fromString(getString("job_id")),getString("schedule_id")?.let(UUID::fromString),getString("parent_job_id")?.let(UUID::fromString),getString("forecast_run_id")?.let(UUID::fromString),getString("status"),getDate("as_of_date")?.toLocalDate(),getInt("horizon_days"),getObject("history_days")?.let{getInt("history_days")},getString("warehouse_id"),getString("sku_id"),getInt("attempt_number"),getTimestamp("scheduled_for").toLocalDateTime(),getTimestamp("started_at")?.toLocalDateTime(),getTimestamp("completed_at")?.toLocalDateTime(),getString("created_by"),getString("error_message"))
    private fun ResultSet.schedule()=ForecastScheduleView(UUID.fromString(getString("schedule_id")),getString("schedule_name"),getString("cadence"),getObject("day_of_week")?.let{getInt("day_of_week")},getInt("run_hour"),getInt("run_minute"),getString("timezone"),getInt("horizon_days"),getObject("history_days")?.let{getInt("history_days")},getString("warehouse_id"),getString("sku_id"),getBoolean("active"),getTimestamp("next_run_at").toLocalDateTime(),getString("created_by"))
    private fun ResultSet.alert()=ForecastGovernanceAlertView(UUID.fromString(getString("alert_id")),UUID.fromString(getString("forecast_job_id")),getString("forecast_run_id")?.let(UUID::fromString),getString("alert_type"),getString("severity"),getString("message"),getBoolean("acknowledged"),getTimestamp("created_at").toLocalDateTime())
    private fun ResultSet.jobRow()=JobRow(UUID.fromString(getString("job_id")),getString("tenant_id"),getDate("as_of_date")?.toLocalDate(),getInt("horizon_days"),getObject("history_days")?.let{getInt("history_days")},getString("warehouse_id"),getString("sku_id"))
    private fun ResultSet.scheduleRow()=ScheduleRow(UUID.fromString(getString("schedule_id")),getString("tenant_id"),getString("cadence"),getObject("day_of_week")?.let{getInt("day_of_week")},getInt("run_hour"),getInt("run_minute"),getString("timezone"),getInt("horizon_days"),getObject("history_days")?.let{getInt("history_days")},getString("warehouse_id"),getString("sku_id"),getTimestamp("next_run_at").toLocalDateTime(),getString("created_by"))
    private data class JobRow(val jobId:UUID,val tenantId:String,val asOfDate:java.time.LocalDate?,val horizonDays:Int,val historyDays:Int?,val warehouseId:String?,val skuId:String?)
    private data class ScheduleRow(val id:UUID,val tenantId:String,val cadence:String,val day:Int?,val hour:Int,val minute:Int,val timezone:String,val horizon:Int,val history:Int?,val warehouse:String?,val sku:String?,val next:LocalDateTime,val createdBy:String)
}
