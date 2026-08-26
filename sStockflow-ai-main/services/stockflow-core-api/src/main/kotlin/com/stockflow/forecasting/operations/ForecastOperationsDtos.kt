package com.stockflow.forecasting.operations

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class QueueForecastJobRequest(val asOfDate:LocalDate?=null,@field:Min(7) @field:Max(90) val horizonDays:Int=30,@field:Min(28) @field:Max(365) val historyDays:Int?=null,@field:Size(max=64) val warehouseId:String?=null,@field:Size(max=80) val skuId:String?=null)
data class CreateForecastScheduleRequest(@field:NotBlank @field:Size(max=160) val scheduleName:String,val cadence:String="DAILY",@field:Min(1) @field:Max(7) val dayOfWeek:Int?=null,@field:Min(0) @field:Max(23) val runHour:Int=2,@field:Min(0) @field:Max(59) val runMinute:Int=0,@field:Size(max=80) val timezone:String="Asia/Kolkata",@field:Min(7) @field:Max(90) val horizonDays:Int=30,@field:Min(28) @field:Max(365) val historyDays:Int?=null,val warehouseId:String?=null,val skuId:String?=null,val active:Boolean=true)
data class ForecastScheduleView(val scheduleId:UUID,val scheduleName:String,val cadence:String,val dayOfWeek:Int?,val runHour:Int,val runMinute:Int,val timezone:String,val horizonDays:Int,val historyDays:Int?,val warehouseId:String?,val skuId:String?,val active:Boolean,val nextRunAt:LocalDateTime,val createdBy:String)
data class ForecastJobView(val jobId:UUID,val scheduleId:UUID?,val parentJobId:UUID?,val forecastRunId:UUID?,val status:String,val asOfDate:LocalDate?,val horizonDays:Int,val historyDays:Int?,val warehouseId:String?,val skuId:String?,val attemptNumber:Int,val scheduledFor:LocalDateTime,val startedAt:LocalDateTime?,val completedAt:LocalDateTime?,val createdBy:String,val errorMessage:String?)
data class ForecastGovernanceAlertView(val alertId:UUID,val forecastJobId:UUID,val forecastRunId:UUID?,val alertType:String,val severity:String,val message:String,val acknowledged:Boolean,val createdAt:LocalDateTime)
