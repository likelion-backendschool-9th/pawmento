import type { ApiClient } from "../../../lib/http/apiClient.ts";

type SentDto = { email: string; expiresAt: string; retryAfter: string };
type ConfirmedDto = { verificationToken: string; expiresAt: string };

// 서버 시각은 나노초까지 온다("…:56.705779835Z"). 브라우저마다 소수점 넷째 자리부터 읽는 방식이 달라 밀리초까지만 읽는다
function toMillis(instant: string) {
  return Date.parse(instant.replace(/(\.\d{3})\d+/, "$1"));
}

// 가입 인증번호 보내기·확인 (#279). 이번에는 가입 용도만 쓴다
export function createEmailVerificationApi(http: ApiClient) {
  return {
    async send(email: string) {
      const sent = await http.post<SentDto>("/auth/email-verifications", { email, purpose: "SIGNUP" });
      return { expiresAt: toMillis(sent.expiresAt), resendAt: toMillis(sent.retryAfter) };
    },
    async confirm(email: string, code: string) {
      const confirmed = await http.post<ConfirmedDto>("/auth/email-verifications/confirm", {
        email,
        purpose: "SIGNUP",
        code,
      });
      return { token: confirmed.verificationToken, expiresAt: toMillis(confirmed.expiresAt) };
    },
  };
}