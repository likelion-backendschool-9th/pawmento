import type { AuthRepository, SessionSource } from "../../application/auth.ts";
import type { Credentials } from "../../domain/account.ts";
import { HttpAuthRepository } from "./HttpAuthRepository.ts";
import type { ApiClient } from "../../../../lib/http/apiClient.ts";

/**
 * 쿠키 세션(HTTP)과 탭 로컬 데모를 한 AuthRepository로 묶는다.
 * 데모로 시작하면 데모 세션을 쓰고, 가입·로그인이면 HTTP를 쓴다.
 */
export class SessionAuthRepository implements AuthRepository {
  private readonly http: HttpAuthRepository;
  private readonly demo: AuthRepository;
  private source: SessionSource = null;

  constructor(api: ApiClient, demo: AuthRepository) {
    this.http = new HttpAuthRepository(api);
    this.demo = demo;
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

  /** 지금 세션의 출처. 앱 컨텍스트가 읽어 화면에 알린다 */
  sessionSource(): SessionSource {
    return this.source;
  }
}
