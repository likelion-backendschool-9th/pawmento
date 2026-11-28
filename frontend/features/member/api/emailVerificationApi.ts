import type { ApiClient } from "../../../lib/http/apiClient.ts";

type SentDto = { email: string; expiresInSeconds: number; retryAfterSeconds: number };
type ConfirmedDto = { verificationToken: string; expiresInSeconds: number };

// 가입 인증번호 보내기·확인 (#279). 이번에는 가입 용도만 쓴다.
// 서버 시각(expiresAt)을 기기 시계와 바로 비교하면 기기 시계가 틀릴 때 남은 시간이 틀어진다.
// 서버가 준 "남은 초"를 응답을 받은 순간의 기기 시계에 더해 쓴다
export function createEmailVerificationApi(http: ApiClient, now: () => number = Date.now) {
  return {
    async send(email: string) {
      const sent = await http.post<SentDto>("/auth/email-verifications", { email, purpose: "SIGNUP" });
      const received = now();
      return {
        expiresAt: received + sent.expiresInSeconds * 1000,
        resendAt: received + sent.retryAfterSeconds * 1000,
      };
    },
    async confirm(email: string, code: string) {
      const confirmed = await http.post<ConfirmedDto>("/auth/email-verifications/confirm", {
        email,
        purpose: "SIGNUP",
        code,
      });
      return { token: confirmed.verificationToken, expiresAt: now() + confirmed.expiresInSeconds * 1000 };
    },
  };
}