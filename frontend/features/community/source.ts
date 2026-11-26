// #168 결정 뒤 정리
// 세션 출처로 커뮤니티 글을 어디서 가져올지 한곳에서 고른다(#104-S9).
// 데모로 시작한 계정은 서버 쿠키가 없어 글을 쓸 수 없으므로 브라우저 데모를, 가입·로그인한 계정은 글 API를 쓴다.
// 서버 요청이 실패해도 데모로 넘어가지 않는다. 두 출처의 글을 한 목록에 섞지 않는다.

import { isDemoAccount, type Account } from "../member/types/account.ts";
import type { PostApi } from "./api/postApi.ts";

export type CommunitySource<Demo> =
  | { kind: "server"; posts: PostApi }
  | { kind: "demo"; demo: Demo };

export function selectCommunitySource<Demo>(input: {
  account: Account | null | undefined;
  server: PostApi;
  demo: Demo;
}): CommunitySource<Demo> {
  return isDemoAccount(input.account)
    ? { kind: "demo", demo: input.demo }
    : { kind: "server", posts: input.server };
}
