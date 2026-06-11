/* Thin fetch wrapper for the ArgusGraph API. Non-2xx responses become ApiError
   carrying the RFC 9457 problem+json title/detail so views can toast them. */
const BASE = "/api/v1";

export class ApiError extends Error {
  constructor(title, detail, status) {
    super(detail || title);
    this.title = title;
    this.detail = detail;
    this.status = status;
  }
}

async function call(method, path, body) {
  let response;
  try {
    response = await fetch(BASE + path, {
      method,
      headers: body ? { "Content-Type": "application/json" } : {},
      body: body ? JSON.stringify(body) : undefined,
    });
  } catch (cause) {
    throw new ApiError("Backend unreachable", "Could not reach the ArgusGraph API.", 0);
  }
  if (response.ok) {
    return response.status === 200 || response.status === 201 ? response.json() : null;
  }
  let problem = {};
  try {
    problem = await response.json();
  } catch (ignored) {
    /* non-JSON error body */
  }
  throw new ApiError(problem.title || `HTTP ${response.status}`, problem.detail || "", response.status);
}

export const get = (path) => call("GET", path);
export const post = (path, body) => call("POST", path, body);
