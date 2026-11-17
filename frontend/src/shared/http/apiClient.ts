import { ApplicationError } from "../application/ApplicationError.ts";

export type ApiErrorBody = {
  code?: string;
  message?: string;
};

export class ApiError extends ApplicationError {
  readonly status: number;

  constructor(status: number, code: string, message: string) {
    super(code, message);
    this.name = "ApiError";
    this.status = status;
  }
}

export type ApiClient = {
  get: <T>(path: string) => Promise<T>;
  post: <T>(path: string, body?: unknown) => Promise<T>;
  patch: <T>(path: string, body?: unknown) => Promise<T>;
  postEmpty: (path: string) => Promise<void>;
};

type FetchLike = typeof fetch;

export function createApiClient(options?: {
  baseUrl?: string;
  fetch?: FetchLike;
}): ApiClient {
  const baseUrl = options?.baseUrl ?? "/api/v1";
  const fetchImpl = options?.fetch ?? fetch;

  async function request<T>(
    path: string,
    init: RequestInit,
    empty = false,
  ): Promise<T> {
    const response = await fetchImpl(`${baseUrl}${path}`, {
      ...init,
      credentials: "include",
      headers: {
        Accept: "application/json",
        ...(init.body ? { "Content-Type": "application/json" } : {}),
        ...init.headers,
      },
    });

    if (response.status === 204 || empty) {
      if (!response.ok) await throwApiError(response);
      return undefined as T;
    }

    if (!response.ok) await throwApiError(response);

    if (response.status === 204) return undefined as T;
    const text = await response.text();
    if (!text) return undefined as T;
    return JSON.parse(text) as T;
  }

  return {
    get: <T>(path: string) => request<T>(path, { method: "GET" }),
    post: <T>(path: string, body?: unknown) =>
      request<T>(path, {
        method: "POST",
        body: body === undefined ? "{}" : JSON.stringify(body),
      }),
    patch: <T>(path: string, body?: unknown) =>
      request<T>(path, {
        method: "PATCH",
        body: JSON.stringify(body ?? {}),
      }),
    postEmpty: (path: string) =>
      request<void>(
        path,
        {
          method: "POST",
          body: "{}",
          headers: { "Content-Type": "application/json" },
        },
        true,
      ),
  };
}

async function throwApiError(response: Response): Promise<never> {
  let body: ApiErrorBody = {};
  try {
    body = (await response.json()) as ApiErrorBody;
  } catch {
    /* ignore non-JSON */
  }
  throw new ApiError(
    response.status,
    body.code ?? "UNKNOWN",
    body.message ?? defaultMessage(response.status),
  );
}

function defaultMessage(status: number) {
  if (status === 401) return "로그인이 필요합니다.";
  if (status === 409) return "요청이 충돌했어요.";
  if (status === 400) return "입력값을 확인해 주세요.";
  return "요청을 처리하지 못했어요.";
}
