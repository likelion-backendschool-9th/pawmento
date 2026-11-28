import type { createEmailVerificationApi } from "./api/emailVerificationApi.ts";
import { normalizeEmail } from "./utils/input.ts";

type Api = ReturnType<typeof createEmailVerificationApi>;

// 가입 폼마다 하나 만든다. 서버가 준 가입 토큰은 이 폼의 메모리에만 두고 저장소(localStorage 등)에 남기지 않는다 (#279)
export class ServerEmailVerification {
  private challenge: { email: string } | null = null;
  private verified: { email: string; token: string; expiresAt: number } | null = null;
  private readonly api: Api;
  private readonly now: () => number;
    // reset()마다 1씩 늘린다. 요청을 보낸 뒤 reset()됐으면 늦게 온 응답을 버린다
  private generation = 0;

  constructor(api: Api, now: () => number = Date.now) {
    this.api = api;
    this.now = now;
  }

  // 실패하면(409 다시 받기 간격, 이미 가입된 이메일 등) 서버 문구를 그대로 던지고 앞의 상태는 그대로 둔다
  async send(email: string) {
    const normalized = normalizeEmail(email);
    const started = this.generation;
    const sent = await this.api.send(normalized);
    if (started !== this.generation) throw Error("인증이 초기화됐어요. 다시 요청해 주세요.");
    this.challenge = { email: normalized };
    this.verified = null;
    return sent;
  }

  async verify(email: string, code: string) {
    const normalized = normalizeEmail(email);
    if (this.challenge?.email !== normalized) throw Error("먼저 인증 코드를 요청해 주세요.");
    const started = this.generation;
    const confirmed = await this.api.confirm(normalized, code.trim());
    // 확인 응답을 기다리는 사이 이메일을 바꿔 reset()됐으면, 옛 이메일의 토큰을 다시 넣지 않는다
    if (started !== this.generation) throw Error("인증이 초기화됐어요. 다시 요청해 주세요.");
    this.verified = { email: normalized, token: confirmed.token, expiresAt: confirmed.expiresAt };
  }

  isVerified(email: string) {
    return this.token(email) !== null;
  }

  // 확인한 이메일과 같고, 토큰이 만료되지 않았을 때만 가입에 쓸 토큰을 준다
  token(email: string) {
    const verified = this.verified;
    if (!verified || verified.email !== normalizeEmail(email) || this.now() >= verified.expiresAt) return null;
    return verified.token;
  }

  reset() {
    this.challenge = null;
    this.verified = null;
    this.verified = null;
  }
}