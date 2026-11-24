import type { Account } from "../types/account.ts";

/** 세션 부트스트랩 후 보호 화면을 열지, 로그인으로 막을지 정한다 (#52-S2) */
export function resolveProtectedAccess(
  sessionReady: boolean,
  account: Account | null,
): "loading" | "login-required" | "allowed" {
  if (!sessionReady) return "loading";
  if (!account) return "login-required";
  return "allowed";
}
