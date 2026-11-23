import type { ApiClient } from "../../../../lib/http/apiClient.ts";
import { ApiError } from "../../../../lib/http/apiClient.ts";
import type { AuthRepository } from "../../application/auth.ts";
import type { Account, Credentials } from "../../domain/account.ts";

type MemberDto = {
  id: number | string;
  email: string;
  nickname: string;
  role?: "USER" | "ADMIN";
};

export class HttpAuthRepository implements AuthRepository {
  private readonly http: ApiClient;

  constructor(http: ApiClient) {
    this.http = http;
  }

  async current() {
    try {
      return toAccount(await this.http.get<MemberDto>("/members/me"));
    } catch (cause) {
      if (cause instanceof ApiError && cause.status === 401) return null;
      throw cause;
    }
  }

  async signUp(input: Credentials & { nickname: string }) {
    await this.http.post<MemberDto>("/auth/signup", {
      email: input.email,
      password: input.password,
      nickname: input.nickname,
    });
    return this.signIn(input);
  }

  async signIn(input: Credentials) {
    return toAccount(
      await this.http.post<MemberDto>("/auth/login", {
        email: input.email,
        password: input.password,
      }),
    );
  }

  async startDemo(): Promise<Account> {
    throw Error("서버 연동 모드에서는 데모 시작을 쓸 수 없어요.");
  }

  async resetPassword(): Promise<void> {
    throw Error("비밀번호 변경은 아직 서버에 연결되지 않았어요.");
  }

  async withdraw(): Promise<void> {
    throw Error("회원 탈퇴는 아직 서버에 연결되지 않았어요.");
  }

  async signInDemoProvider(): Promise<Account> {
    throw Error("소셜 로그인은 아직 서버에 연결되지 않았어요.");
  }

  async signOut() {
    await this.http.postEmpty("/auth/logout");
  }

  async update(nickname: string) {
    return toAccount(
      await this.http.patch<MemberDto>("/members/me", { nickname }),
    );
  }
  sessionSource(): "http" {
    return "http";
  }
}

function toAccount(dto: MemberDto): Account {
  return {
    id: String(dto.id),
    email: dto.email,
    nickname: dto.nickname,
    role: dto.role,
  };
}
