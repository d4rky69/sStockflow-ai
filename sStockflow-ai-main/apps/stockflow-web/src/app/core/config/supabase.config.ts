interface StockFlowRuntimeConfig {
  supabaseUrl?: string;
  supabasePublishableKey?: string;
}

declare global {
  interface Window {
    __stockflowConfig?: StockFlowRuntimeConfig;
  }
}

const runtimeConfig = window.__stockflowConfig ?? {};
const url = runtimeConfig.supabaseUrl?.trim() ?? '';
const publishableKey = runtimeConfig.supabasePublishableKey?.trim() ?? '';

export const SUPABASE_CONFIG = {
  url,
  publishableKey,
  configured: /^https:\/\/[a-z0-9-]+\.supabase\.co$/i.test(url) && publishableKey.length > 20
} as const;
