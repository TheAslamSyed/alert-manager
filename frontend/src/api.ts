/** Thin fetch wrapper: every call goes to the gateway (/api) with the Keycloak access token. */
let accessToken: string | undefined;

export function setAccessToken(token: string | undefined) {
  accessToken = token;
}

export function getAccessToken() {
  return accessToken;
}

export class ApiError extends Error {
  constructor(public status: number, message: string) {
    super(message);
  }
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const res = await fetch('/api' + path, {
    method,
    headers: {
      ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
      ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
    },
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
  if (res.status === 204) return undefined as T;
  const text = await res.text();
  if (!res.ok) {
    let message = text;
    try {
      message = JSON.parse(text).detail ?? text;
    } catch { /* not JSON */ }
    throw new ApiError(res.status, message || res.statusText);
  }
  const type = res.headers.get('content-type') ?? '';
  return (type.includes('json') ? JSON.parse(text) : text) as T;
}

export const api = {
  get: <T = any>(path: string) => request<T>('GET', path),
  post: <T = any>(path: string, body?: unknown) => request<T>('POST', path, body ?? {}),
  put: <T = any>(path: string, body: unknown) => request<T>('PUT', path, body),
};

/** Roles come from the access token (realm_access.roles). Used only to hide buttons; the backend enforces. */
export function rolesFromToken(token?: string): string[] {
  if (!token) return [];
  try {
    const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
    return payload?.realm_access?.roles ?? [];
  } catch {
    return [];
  }
}
