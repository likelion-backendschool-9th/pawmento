import assert from "node:assert/strict";
import test from "node:test";
import { createEmailVerificationApi } from "../features/member/api/emailVerificationApi.ts";
import { createMemberApi } from "../features/member/api/memberApi.ts";
import { ServerEmailVerification } from "../features/member/emailVerification.ts";
import { createApiClient } from "../lib/http/apiClient.ts";

type Call = { url: string; method?: string; body?: unknown };

function server(respond: (call: Call) => Response) {
  const calls: Call[] = [];
  const http = createApiClient({
    async fetch(url, options) {
      const call = {
        url: String(url),
        method: options?.method,
        body: options?.body ? JSON.parse(String(options.body)) : undefined,
      };
      calls.push(call);
      return respond(call);
    },
  });
  return { http, calls };
}

const SENT = { email: "a@test.com", expiresAt: "2026-11-27T18:57:56.705779835Z", retryAfter: "2026-11-27T18:53:56.705779835Z" };
const CONFIRMED = { verificationToken: "4RMsep9qmqIY3povu_5l_CAnvCvdzE7O0moYqJ9870Y", expiresAt: "2026-11-27T19:25:33.754122421Z" };

test("#279-S12 보내기는 가입 용도로 이메일을 보내고, 나노초까지 오는 서버 시각을 밀리초로 읽는다", async () => {
  const { http, calls } = server(() => Response.json(SENT, { status: 202 }));

  const sent = await createEmailVerificationApi(http).send("a@test.com");

  assert.deepEqual(calls, [{ url: "/api/v1/auth/email-verifications", method: "POST", body: { email: "a@test.com", purpose: "SIGNUP" } }]);
  assert.equal(sent.expiresAt, Date.parse("2026-11-27T18:57:56.705Z"));
  assert.equal(sent.resendAt, Date.parse("2026-11-27T18:53:56.705Z"));
  assert.ok(Number.isFinite(sent.expiresAt) && Number.isFinite(sent.resendAt));
});

test("#279-S12 확인은 번호를 함께 보내고 가입 토큰과 토큰 만료를 돌려준다", async () => {
  const { http, calls } = server(() => Response.json(CONFIRMED));

  const confirmed = await createEmailVerificationApi(http).confirm("a@test.com", "629503");

  assert.deepEqual(calls, [{
    url: "/api/v1/auth/email-verifications/confirm",
    method: "POST",
    body: { email: "a@test.com", purpose: "SIGNUP", code: "629503" },
  }]);
  assert.equal(confirmed.token, CONFIRMED.verificationToken);
  assert.equal(confirmed.expiresAt, Date.parse("2026-11-27T19:25:33.754Z"));
});

test("#279-S12 서버의 409·400 문구는 그대로 화면으로 전달된다", async () => {
  const { http } = server((call) => call.url.endsWith("/confirm")
    ? Response.json({ code: "VALIDATION", message: "인증번호가 맞지 않거나 만료됐어요." }, { status: 400 })
    : Response.json({ code: "CONFLICT", message: "잠시 후 다시 요청해 주세요." }, { status: 409 }));
  const api = createEmailVerificationApi(http);

  await assert.rejects(api.send("a@test.com"), { status: 409, message: "잠시 후 다시 요청해 주세요." });
  await assert.rejects(api.confirm("a@test.com", "000000"), { status: 400, message: "인증번호가 맞지 않거나 만료됐어요." });
});

test("#279-S12 서버 인증은 보낸 이메일로만 확인하고, 확인하면 그 이메일에 한해 토큰을 준다", async () => {
  const { http, calls } = server((call) => call.url.endsWith("/confirm") ? Response.json(CONFIRMED) : Response.json(SENT, { status: 202 }));
  const verification = new ServerEmailVerification(createEmailVerificationApi(http), () => Date.parse("2026-11-27T18:55:00Z"));

  // 보내기 전에는 서버를 부르지 않고 거절한다
  await assert.rejects(verification.verify("a@test.com", "629503"), /먼저 인증 코드를 요청해 주세요/);
  assert.equal(calls.length, 0);

  const sent = await verification.send(" A@Test.com ");
  assert.deepEqual(calls[0].body, { email: "a@test.com", purpose: "SIGNUP" });
  assert.equal(sent.resendAt, Date.parse("2026-11-27T18:53:56.705Z"));
  assert.equal(verification.isVerified("a@test.com"), false);
  assert.equal(verification.token("a@test.com"), null);

  // 다른 이메일로는 확인하지 않는다
  await assert.rejects(verification.verify("b@test.com", "629503"));
  assert.equal(calls.length, 1);

  await verification.verify("A@TEST.com", " 629503 ");
  assert.deepEqual(calls[1].body, { email: "a@test.com", purpose: "SIGNUP", code: "629503" });
  assert.equal(verification.isVerified("a@test.com"), true);
  assert.equal(verification.token("A@test.COM"), CONFIRMED.verificationToken);
  assert.equal(verification.token("b@test.com"), null);
});

test("#279-S12 토큰이 만료되면 인증이 풀리고, 다시 받거나 초기화해도 풀린다", async () => {
  let now = Date.parse("2026-11-27T18:55:00Z");
  const { http } = server((call) => call.url.endsWith("/confirm") ? Response.json(CONFIRMED) : Response.json(SENT, { status: 202 }));
  const verification = new ServerEmailVerification(createEmailVerificationApi(http), () => now);

  await verification.send("a@test.com");
  await verification.verify("a@test.com", "629503");
  now = Date.parse("2026-11-27T19:25:33.753Z");
  assert.equal(verification.isVerified("a@test.com"), true);
  now = Date.parse("2026-11-27T19:25:33.754Z");
  assert.equal(verification.isVerified("a@test.com"), false);

  now = Date.parse("2026-11-27T18:55:00Z");
  await verification.verify("a@test.com", "629503");
  await verification.send("a@test.com");
  assert.equal(verification.isVerified("a@test.com"), false);

  await verification.verify("a@test.com", "629503");
  verification.reset();
  assert.equal(verification.isVerified("a@test.com"), false);
  await assert.rejects(verification.verify("a@test.com", "629503"), /먼저 인증 코드를 요청해 주세요/);
});

test("#279-S12 다시 받기가 409로 거절돼도 앞서 보낸 번호로는 계속 확인할 수 있다", async () => {
  let sends = 0;
  const { http } = server((call) => {
    if (call.url.endsWith("/confirm")) return Response.json(CONFIRMED);
    sends += 1;
    return sends === 1
      ? Response.json(SENT, { status: 202 })
      : Response.json({ code: "CONFLICT", message: "잠시 후 다시 요청해 주세요." }, { status: 409 });
  });
  const verification = new ServerEmailVerification(createEmailVerificationApi(http), () => Date.parse("2026-11-27T18:55:00Z"));

  await verification.send("a@test.com");
  await assert.rejects(verification.send("a@test.com"), { status: 409 });
  await verification.verify("a@test.com", "629503");
  assert.equal(verification.isVerified("a@test.com"), true);
});

test("#279-S12 가입 요청은 인증 토큰을 함께 보내고, 토큰이 없으면 그 필드를 보내지 않는다", async () => {
  const member = { id: 1, email: "a@test.com", nickname: "보호자" };
  const { http, calls } = server(() => Response.json(member));
  const api = createMemberApi(http);

  await api.signUp({ email: "a@test.com", password: "password123", nickname: "보호자", verificationToken: "token-1" });
  await api.signUp({ email: "a@test.com", password: "password123", nickname: "보호자" });

  const signups = calls.filter((call) => call.url === "/api/v1/auth/signup");
  assert.deepEqual(signups[0].body, { email: "a@test.com", password: "password123", nickname: "보호자", verificationToken: "token-1" });
  assert.deepEqual(signups[1].body, { email: "a@test.com", password: "password123", nickname: "보호자" });
});
