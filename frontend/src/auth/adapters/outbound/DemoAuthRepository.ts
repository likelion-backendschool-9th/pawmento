import { createId } from "../../../shared/id.ts";
import type { AuthRepository } from "../../application/auth.ts";
import type { Account, Credentials } from "../../domain/account.ts";

// This is a tab-local UI demo, not server authentication. No password/token is persisted.
export class DemoAuthRepository implements AuthRepository {
  private accounts = new Map<string, { account: Account; password: string }>();
  private account: Account | null = null;
  async current() {
    return this.account;
  }
  async signUp(input: Credentials & { nickname: string }) {
    if (this.accounts.has(input.email))
      throw Error("이 탭에서 이미 가입한 이메일이에요.");
    const account = {
      id: createId(),
      email: input.email,
      nickname: input.nickname,
      demo: true as const,
    };
    this.accounts.set(input.email, { account, password: input.password });
    this.account = account;
    return account;
  }
  async signIn(input: Credentials) {
    const entry = this.accounts.get(input.email);
    if (!entry || entry.password !== input.password)
      throw Error(
        "이메일 또는 비밀번호를 확인해 주세요. 데모 계정은 가입한 탭에서만 로그인할 수 있어요.",
      );
    this.account = entry.account;
    return this.account;
  }
  async startDemo() {
    this.account = {
      id: "demo",
      email: "demo@pawmento.local",
      nickname: "보리네",
      role: "ADMIN",
      demo: true,
    };
    return this.account;
  }
  async resetPassword(input: Credentials) {
    const entry = this.accounts.get(input.email);
    if (!entry) throw Error("이 탭에서 가입한 데모 계정만 변경할 수 있어요.");
    entry.password = input.password;
  }
  async withdraw() {
    if (this.account) this.accounts.delete(this.account.email);
    this.account = null;
  }
  async signInDemoProvider(provider: "google" | "kakao") {
    this.account = {
      id: `demo-${provider}`,
      email: `${provider}@pawmento.local`,
      nickname: provider === "google" ? "구글 데모" : "카카오 데모",
      role: "USER",
      demo: true,
    };
    return this.account;
  }
  async signOut() {
    this.account = null;
  }
  async update(nickname: string) {
    if (!this.account) throw Error("로그인이 필요해요.");
    this.account = { ...this.account, nickname };
    const entry = this.accounts.get(this.account.email);
    if (entry) entry.account = this.account;
    return this.account;
  }
}
