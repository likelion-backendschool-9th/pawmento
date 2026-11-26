import { normalizeEmail } from "../../features/member/utils/input.ts";
import { DEMO_EMAIL_VERIFICATION } from "../fixtures/demoEmailVerification.ts";

// 인스턴스는 회원가입 폼마다 만들고 메모리에만 둔다. 실제 인증 증명으로 사용하지 않는다.
export class DemoEmailVerification {
  private challenge: { email: string; expiresAt: number; resendAt: number; verified: boolean } | null = null;
  private readonly now: () => number;
  constructor(now: () => number = Date.now) { this.now = now; }

  async send(email: string) {
    const normalized = normalizeEmail(email);
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(normalized)) throw Error("이메일 주소를 확인해 주세요.");
    if (this.challenge?.email === normalized && this.now() < this.challenge.resendAt) throw Error("잠시 후 인증 코드를 다시 요청해 주세요.");
    const expiresAt = this.now() + DEMO_EMAIL_VERIFICATION.durationMs;
    const resendAt = this.now() + DEMO_EMAIL_VERIFICATION.resendDelayMs;
    this.challenge = { email: normalized, expiresAt, resendAt, verified: false };
    return { expiresAt, resendAt, demoCode: DEMO_EMAIL_VERIFICATION.code };
  }

  async verify(email: string, code: string) {
    const challenge = this.challenge;
    if (!challenge || challenge.email !== normalizeEmail(email)) throw Error("먼저 인증 코드를 요청해 주세요.");
    if (this.now() >= challenge.expiresAt) throw Error("인증 코드가 만료됐어요. 다시 받아 주세요.");
    if (code.trim() !== DEMO_EMAIL_VERIFICATION.code) throw Error("인증 코드가 맞지 않아요. 다시 확인해 주세요.");
    challenge.verified = true;
  }

  isVerified(email: string) {
    return Boolean(this.challenge?.verified && this.challenge.email === normalizeEmail(email) && this.now() < this.challenge.expiresAt);
  }

  reset() { this.challenge = null; }
}
