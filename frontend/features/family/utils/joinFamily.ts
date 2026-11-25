import { ApplicationError } from "../../../lib/application/ApplicationError.ts";
import type { SessionSource } from "../../member/types/account.ts";
import { messageOf } from "../../shell/utils/presentation.ts";
import type { JoinedPet } from "../types/serverFamily.ts";

export type JoinSession = {
  ready: boolean;
  source: SessionSource;
  accountId: string | null;
};

export type JoinState =
  | { status: "idle" | "submitting" | "login-required" }
  | { status: "joined"; pet: JoinedPet }
  | { status: "error"; message: string };

export function joinScreen(session: JoinSession) {
  if (!session.ready) return "loading";
  return session.source === "http" && session.accountId ? "server" : "login-required";
}

// 요청을 시작한 서버 세션이 응답을 받을 때도 그대로일 때만 결과를 반영한다.
// 그사이 계정이 바뀌었거나 데모로 바뀌었거나 화면을 떠났으면(getSession이 null) 늦은 응답을 버린다.
export async function submitJoin(
  getSession: () => JoinSession | null,
  join: (code: string) => Promise<JoinedPet>,
  code: string,
  publish: (state: JoinState) => void,
) {
  const started = getSession();
  if (!started || joinScreen(started) !== "server") return;
  if (!code.trim()) {
    publish({ status: "error", message: "초대 코드를 입력해 주세요." });
    return;
  }

  publish({ status: "submitting" });
  let result: JoinState;
  try {
    result = { status: "joined", pet: await join(code) };
  } catch (cause) {
    result = cause instanceof ApplicationError && cause.code === "UNAUTHORIZED"
      ? { status: "login-required" }
      : { status: "error", message: messageOf(cause) };
  }

  const current = getSession();
  if (current && joinScreen(current) === "server" && current.accountId === started.accountId) {
    publish(result);
  }
}
