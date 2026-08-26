const CLOUD_RUN_API = 'https://stockflow-core-api-100044030673.asia-southeast1.run.app';

const isLocalHost = ['localhost', '127.0.0.1'].includes(window.location.hostname);

/**
 * Local development uses Angular's proxy configuration.
 * Deployed builds call the public Cloud Run API.
 */
export const API_BASE_URL = isLocalHost ? '' : CLOUD_RUN_API;

/**
 * The Copilot Host is a separate service. Leave this empty when a reverse
 * proxy exposes it at the same origin; set window.STOCKFLOW_COPILOT_API_URL
 * for a separately hosted Copilot service.
 */
export const COPILOT_API_BASE_URL = (window as typeof window & { STOCKFLOW_COPILOT_API_URL?: string }).STOCKFLOW_COPILOT_API_URL ?? '';

/**
 * Route optimisation and carbon calculations run in a separate Python service.
 * Local development can provide the same global value or use port 8400.
 */
export const CARBON_API_BASE_URL = (window as typeof window & { STOCKFLOW_CARBON_API_URL?: string }).STOCKFLOW_CARBON_API_URL
  ?? (isLocalHost ? 'http://127.0.0.1:8400' : '');
