import { ApiError, type ApiClient } from "../../../lib/http/apiClient.ts";
import type { Account, Credentials } from "../types/account.ts";

type MemberDto = {
  id: number | string;
  email: string;
  nickname: string;
  role?: "USER" | "ADMIN";
};

export function createMemberApi(http: ApiClient) {
  async function signIn(input: Credentials) {
    return toAccount(
      await http.post<MemberDto>("/auth/login", {
        email: input.email,
        password: input.password,
      }),
    );
  }

  return {
    async current() {
      try {
        return toAccount(await http.get<MemberDto>("/members/me"));
      } catch (cause) {
        if (cause instanceof ApiError && cause.status === 401) return null;
        throw cause;
      }
    },
    async signUp(input: Credentials & { nickname: string; verificationToken?: string }) {
      await http.post<MemberDto>("/auth/signup", {
        email: input.email,
        password: input.password,
        nickname: input.nickname,
        // 가입 인증 토큰. 서버가 확인하기 전(#279 PR 5)에도 함께 보내 두면 머지 순서와 상관없이 가입이 끊기지 않는다
        verificationToken: input.verificationToken,
      });
      return signIn(input);
    },
    signIn,
    async signOut() {
      await http.postEmpty("/auth/logout");
    },
    async update(nickname: string) {
      return toAccount(await http.patch<MemberDto>("/members/me", { nickname }));
    },
    async resetPassword(): Promise<void> {
      throw Error("비밀번호 변경은 아직 서버에 연결되지 않았어요.");
    },
    async withdraw(): Promise<void> {
      throw Error("회원 탈퇴는 아직 서버에 연결되지 않았어요.");
    },
  };
}

function toAccount(dto: MemberDto): Account {
  return {
    id: String(dto.id),
    email: dto.email,
    nickname: dto.nickname,
    role: dto.role,
  };
}
