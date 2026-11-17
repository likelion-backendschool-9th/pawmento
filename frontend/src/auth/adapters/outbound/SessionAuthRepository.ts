import type { AuthRepository } from "../../application/auth.ts";
import type { Credentials } from "../../domain/account.ts";
import { DemoAuthRepository } from "./DemoAuthRepository.ts";
import { HttpAuthRepository } from "./HttpAuthRepository.ts";
import type { ApiClient } from "../../../shared/http/apiClient.ts";

/**
 * 쿠키 세션(HTTP)과 탭 로컬 데모를 한 AuthRepository로 묶는다.
 * 데모로 시작하면 데모 세션을 쓰고, 가입·로그인이면 HTTP를 쓴다.
 */
export class SessionAuthRepository implements AuthRepository {
  private readonly http: HttpAuthRepository;
  private readonly demo = new DemoAuthRepository();
  private source: "http" | "demo" | null = null;

  constructor(api: ApiClient) {
    this.http = new HttpAuthRepository(api);
  }

  async current() {
    if (this.source === "demo") return this.demo.current();
    const account = await this.http.current();
    if (account) {
      this.source = "http";
      return account;
    }
    this.source = null;
    return null;
  }

  async signUp(input: Credentials & { nickname: string }) {
    const account = await this.http.signUp(input);
    this.source = "http";
    return account;
  }

  async signIn(input: Credentials) {
    const account = await this.http.signIn(input);
    this.source = "http";
    return account;
  }

  async startDemo() {
    const account = await this.demo.startDemo();
    this.source = "demo";
    return account;
  }

  async resetPassword(input: Credentials) {
    if (this.source === "demo") return this.demo.resetPassword(input);
    return this.http.resetPassword();
  }

  async withdraw() {
    if (this.source === "demo") {
      await this.demo.withdraw();
      this.source = null;
      return;
    }
    return this.http.withdraw();
  }

  async signInDemoProvider(provider: "google" | "kakao") {
    const account = await this.demo.signInDemoProvider(provider);
    this.source = "demo";
    return account;
  }

  async signOut() {
    if (this.source === "demo") {
      await this.demo.signOut();
    } else {
      await this.http.signOut();
    }
    this.source = null;
  }

  async update(nickname: string) {
    if (this.source === "demo") return this.demo.update(nickname);
    return this.http.update(nickname);
  }

  /** 테스트·화면에서 현재 세션 출처를 구분할 때 쓴다 */
  sessionSource() {
    return this.source;
  }
}
