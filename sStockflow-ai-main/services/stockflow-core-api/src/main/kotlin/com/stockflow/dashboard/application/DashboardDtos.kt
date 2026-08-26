package com.stockflow.dashboard.application

data class DashboardOverviewView(
    val userName: String,
    val userRole: String,
    val asOf: String,
    val kpis: List<DashboardKpiView>,
    val riskTotal: Int,
    val riskBreakdown: List<DashboardRiskBreakdownView>,
    val topRisks: List<DashboardTopRiskView>,
    val demandForecast: DashboardChartSeriesView,
    val inventoryTrend: DashboardChartSeriesView,
    val recommendations: List<DashboardRecommendationView>,
    val networkMetrics: List<DashboardNetworkMetricView>,
    val copilotMessages: List<DashboardCopilotMessageView>
)

data class DashboardKpiView(
    val key: String,
    val label: String,
    val value: String,
    val change: String,
    val comparison: String,
    val direction: String,
    val intent: String,
    val icon: String,
    val accent: String
)

data class DashboardRiskBreakdownView(
    val label: String,
    val count: Int,
    val percentage: Int,
    val color: String
)

data class DashboardTopRiskView(
    val id: String,
    val product: String,
    val batch: String?,
    val warehouse: String,
    val type: String,
    val badgeClass: String,
    val quantity: String,
    val detail: String,
    val metric: String,
    val metricIntent: String,
    val icon: String
)

data class DashboardChartSeriesView(
    val labels: List<String>,
    val actual: List<Long>,
    val forecast: List<Long>,
    val values: List<Double>
)

data class DashboardRecommendationView(
    val title: String,
    val subtitle: String,
    val benefit: String,
    val icon: String
)

data class DashboardNetworkMetricView(
    val label: String,
    val value: String,
    val icon: String
)

data class DashboardCopilotMessageView(
    val role: String,
    val text: String,
    val timestamp: String
)
