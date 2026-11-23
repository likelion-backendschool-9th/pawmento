import type { Account, Credentials } from "../domain/account.ts";
import { validateCredentials, validateNickname } from "../domain/account.ts";

// 지금 세션이 어디서 왔는지. null은 아직 로그인하지 않았거나 확인 전이다
export type SessionSource = "http" | "demo" | null;
export interface AuthRepository {
  current(): Promise<Account | null>;
  signUp(input: Credentials & { nickname: string }): Promise<Account>;
  signIn(input: Credentials): Promise<Account>;
  startDemo(): Promise<Account>;
  signOut(): Promise<void>;
  update(nickname: string): Promise<Account>;
  resetPassword(input: Credentials): Promise<void>;
  withdraw(): Promise<void>;
  signInDemoProvider(provider: "google" | "kakao"): Promise<Account>;
  sessionSource(): SessionSource;
}
export function createAuthUseCases(repository: AuthRepository) {
  return {
    current: () => repository.current(),
    resetPassword: (input: Credentials) => {
      validateCredentials(input);
      return repository.resetPassword({
        ...input,
        email: input.email.trim().toLowerCase(),
      });
    },
    withdraw: () => repository.withdraw(),
    signInDemoProvider: (provider: "google" | "kakao") =>
      repository.signInDemoProvider(provider),
    signUp: (
      input: Credentials & { nickname: string; confirmation: string },
    ) => {
      validateCredentials(input);
      validateNickname(input.nickname);
      if (input.password !== input.confirmation)
        throw Error("비밀번호 확인이 일치하지 않아요.");
      return repository.signUp({
        ...input,
        email: input.email.trim().toLowerCase(),
        nickname: input.nickname.trim(),
      });
    },
    signIn: (input: Credentials) => {
      validateCredentials(input);
      return repository.signIn({
        ...input,
        email: input.email.trim().toLowerCase(),
      });
    },
    startDemo: () => repository.startDemo(),
    signOut: () => repository.signOut(),
    update: (nickname: string) => {
      validateNickname(nickname);
      return repository.update(nickname.trim());
    },
    sessionSource: () => repository.sessionSource(),
  };
}
