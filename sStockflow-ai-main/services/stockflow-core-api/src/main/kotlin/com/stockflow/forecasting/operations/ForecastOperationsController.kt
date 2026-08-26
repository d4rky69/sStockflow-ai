package com.stockflow.forecasting.operations

import com.stockflow.security.TenantAccessContext
import com.stockflow.security.TenantAuthorizationFilter
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@RestController @RequestMapping("/api/v1/forecast-operations")
class ForecastOperationsController(private val service:ForecastOperationsService){
 @GetMapping("/jobs") fun jobs(r:HttpServletRequest)=service.jobs(access(r))
 @PostMapping("/jobs") @ResponseStatus(HttpStatus.CREATED) fun queue(r:HttpServletRequest,@Valid @RequestBody b:QueueForecastJobRequest)=service.queue(access(r),b)
 @PostMapping("/jobs/process-next") fun process(r:HttpServletRequest)=service.processNext(access(r))
 @PostMapping("/jobs/{id}/cancel") fun cancel(r:HttpServletRequest,@PathVariable id:UUID)=service.cancel(access(r),id)
 @PostMapping("/jobs/{id}/retry") fun retry(r:HttpServletRequest,@PathVariable id:UUID)=service.retry(access(r),id)
 @GetMapping("/schedules") fun schedules(r:HttpServletRequest)=service.schedules(access(r))
 @PostMapping("/schedules") @ResponseStatus(HttpStatus.CREATED) fun schedule(r:HttpServletRequest,@Valid @RequestBody b:CreateForecastScheduleRequest)=service.createSchedule(access(r),b)
 @PostMapping("/schedules/{id}/active") fun active(r:HttpServletRequest,@PathVariable id:UUID,@RequestParam active:Boolean)=service.setScheduleActive(access(r),id,active)
 @GetMapping("/alerts") fun alerts(r:HttpServletRequest)=service.alerts(access(r))
 @PostMapping("/alerts/{id}/acknowledge") fun acknowledge(r:HttpServletRequest,@PathVariable id:UUID)=service.acknowledge(access(r),id)
 private fun access(r:HttpServletRequest)=r.getAttribute(TenantAuthorizationFilter.TENANT_ACCESS_ATTRIBUTE) as? TenantAccessContext?:throw ResponseStatusException(HttpStatus.FORBIDDEN,"Tenant security context is unavailable")
}
