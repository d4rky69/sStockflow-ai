export interface Kpi {
  key: string;
  label: string;
  value: string;
  change: string;
  comparison: string;
  direction: 'up' | 'down';
  intent: 'positive' | 'negative' | 'neutral';
  icon: string;
  accent: string;
}

export interface RiskBreakdown {
  label: string;
  count: number;
  percentage: number;
  color: string;
}

export interface TopRisk {
  id: string;
  product: string;
  batch?: string;
  warehouse: string;
  type: string;
  badgeClass: string;
  quantity: string;
  detail: string;
  metric: string;
  metricIntent: 'high' | 'good' | 'neutral';
  icon: string;
}

export interface Recommendation {
  title: string;
  subtitle: string;
  benefit: string;
  icon: string;
}

export interface ChartSeries {
  labels: string[];
  actual: number[];
  forecast: number[];
  values: number[];
}

export interface NetworkMetric {
  label: string;
  value: string;
  icon: string;
}

export interface CopilotMessage {
  role: 'user' | 'assistant';
  text: string;
  timestamp: string;
}

export interface DashboardOverview {
  userName: string;
  userRole: string;
  asOf: string;
  kpis: Kpi[];
  riskTotal: number;
  riskBreakdown: RiskBreakdown[];
  topRisks: TopRisk[];
  demandForecast: ChartSeries;
  inventoryTrend: ChartSeries;
  recommendations: Recommendation[];
  networkMetrics: NetworkMetric[];
  copilotMessages: CopilotMessage[];
}
