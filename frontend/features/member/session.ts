// #168 결정 뒤 정리
// 쿠키 세션(HTTP)과 탭 로컬 데모를 한곳에서 섞는다.
// 데모로 시작하면 데모 세션을 쓰고, 가입·로그인이면 HTTP를 쓴다.

import type { ApiClient } from "../../lib/http/apiClient.ts";
import { createMemberApi } from "./api/memberApi.ts";
import type { Account, Credentials, SessionSource } from "./types/account.ts";
import {
  normalizeEmail,
  validateCredentials,
  validateNickname,
} from "./utils/input.ts";

type DemoMember = {
  current(): Promise<Account | null>;
  startDemo(input?: { fresh?: boolean; nickname?: string }): Promise<Account>;
  resetPassword(input: Credentials): Promise<void>;
  withdraw(): Promise<void>;
  signInDemoProvider(provider: "google" | "kakao"): Promise<Account>;
  signOut(): Promise<void>;
  update(nickname: string): Promise<Account>;
};

export function createMemberSession(http: ApiClient, demo: DemoMember) {
  const api = createMemberApi(http);
  let source: SessionSource = null;

  return {
    current() {
      if (source === "demo") return demo.current();
      return api.current().then((account) => {
        if (account) {
          source = "http";
          return account;
        }
        source = null;
        return null;
      });
    },
    signUp(input: Credentials & { nickname: string; confirmation: string }) {
      validateCredentials(input);
      validateNickname(input.nickname);
      if (input.password !== input.confirmation)
        throw Error("비밀번호 확인이 일치하지 않아요.");
      return api
        .signUp({
          email: normalizeEmail(input.email),
          password: input.password,
          nickname: input.nickname.trim(),
        })
        .then((account) => {
          source = "http";
          return account;
        });
    },
    signIn(input: Credentials) {
      validateCredentials(input);
      return api
        .signIn({
          ...input,
          email: normalizeEmail(input.email),
        })
        .then((account) => {
          source = "http";
          return account;
        });
    },
    startDemo(input?: { fresh?: boolean; nickname?: string }) {
      return demo.startDemo(input).then((account) => {
        source = "demo";
        return account;
      });
    },
    resetPassword(input: Credentials) {
      validateCredentials(input);
      const normalized = {
        ...input,
        email: normalizeEmail(input.email),
      };
      if (source === "demo") return demo.resetPassword(normalized);
      return api.resetPassword();
    },
    withdraw() {
      if (source === "demo") {
        return demo.withdraw().then(() => {
          source = null;
        });
      }
      return api.withdraw();
    },
    signInDemoProvider(provider: "google" | "kakao") {
      return demo.signInDemoProvider(provider).then((account) => {
        source = "demo";
        return account;
      });
    },
    signOut() {
      const pending = source === "demo" ? demo.signOut() : api.signOut();
      return pending.then(() => {
        source = null;
      });
    },
    update(nickname: string) {
      validateNickname(nickname);
      const trimmed = nickname.trim();
      if (source === "demo") return demo.update(trimmed);
      return api.update(trimmed);
    },
    sessionSource(): SessionSource {
      return source;
    },
  };
}
