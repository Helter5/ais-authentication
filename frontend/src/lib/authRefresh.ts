const API_URL = (import.meta.env.VITE_API_URL || 'http://localhost:8080/api').replace(/\/$/, '');

let refreshPromise: Promise<boolean> | null = null;

/**
 * Exchanges the refresh_token cookie for a fresh access token via POST /api/auth/refresh.
 * Shared by api.ts's 401 interceptor and AuthContext's initial session check so concurrent
 * callers (e.g. several API calls failing at once after the access token expires) trigger
 * exactly one refresh call instead of one per failure.
 */
export function refreshSession(): Promise<boolean> {
  if (!refreshPromise) {
    refreshPromise = fetch(`${API_URL}/auth/refresh`, { method: 'POST', credentials: 'include' })
      .then(res => res.ok)
      .catch(() => false)
      .finally(() => { refreshPromise = null; });
  }
  return refreshPromise;
}
