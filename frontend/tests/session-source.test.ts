import test from "node:test";
import assert from "node:assert/strict";
import { createApiClient } from "../lib/http/apiClient.ts";
import { createMemberSession } from "../features/member/session.ts";
import { DemoAuthRepository } from "../mocks/handlers/DemoAuthRepository.ts";

const member = { id: 1, email: "dawit@example.com", nickname: "다윗", role: "USER" };

function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

// 쿠키 세션은 없고, 로그인·로그아웃은 성공하는 가짜 서버
function repository() {
  const fetchImpl: typeof fetch = async (input) => {
    const url = String(input);
    if (url.endsWith("/members/me")) return json(401, { code: "UNAUTHORIZED", message: "로그인이 필요합니다." });
    if (url.endsWith("/auth/login")) return json(200, member);
    if (url.endsWith("/auth/logout")) return new Response(null, { status: 204 });
    throw Error(`예상하지 못한 요청: ${url}`);
  };
  return createMemberSession(
    createApiClient({ fetch: fetchImpl }),
    new DemoAuthRepository(),
  );
}

test("#120-S1 쿠키 세션이 없으면 출처는 미확정(null)이다", async () => {
  const auth = repository();
  assert.equal(await auth.current(), null);
  assert.equal(auth.sessionSource(), null);
});

test("#120-S2 이메일로 로그인하면 출처는 http다", async () => {
  const auth = repository();
  await auth.signIn({ email: member.email, password: "correct-password" });
  assert.equal(auth.sessionSource(), "http");
});

test("#120-S3 데모를 시작하면 출처는 demo다", async () => {
  const auth = repository();
  const account = await auth.startDemo();
  assert.equal(account.role, "USER");
  assert.equal(auth.sessionSource(), "demo");
});

test("#120-S4 로그아웃하면 출처는 다시 null이다 (http·demo 모두)", async () => {
  const http = repository();
  await http.signIn({ email: member.email, password: "correct-password" });
  await http.signOut();
  assert.equal(http.sessionSource(), null);

  const demo = repository();
  await demo.startDemo();
  await demo.signOut();
  assert.equal(demo.sessionSource(), null);
});