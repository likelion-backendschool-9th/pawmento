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

const SENT = { email: "a@test.com", expiresAt: "2026-11-27T18:57:56.705779835Z", retryAfter: "2026-11-27T18:53:56.705779835Z", expiresInSeconds: 300, retryAfterSeconds: 60 };
const CONFIRMED = { verificationToken: "4RMsep9qmqIY3povu_5l_CAnvCvdzE7O0moYqJ9870Y", expiresAt: "2026-11-27T19:25:33.754122421Z", expiresInSeconds: 1800 };

// 응답을 기다리게 해 두었다가 원할 때 돌려준다
function deferred() {
  let resolve!: (response: Response) => void;
  const promise = new Promise<Response>((done) => { resolve = done; });
  return { promise, resolve };
}

test("#279-S12 보내기는 가입 용도로 이메일을 보내고, 남은 초를 받은 순간의 기기 시계에 더한다", async () => {
  const { http, calls } = server(() => Response.json(SENT, { status: 202 }));
  const received = Date.parse("2026-11-28T12:00:00Z");

  const sent = await createEmailVerificationApi(http, () => received).send("a@test.com");

  assert.deepEqual(calls, [{ url: "/api/v1/auth/email-verifications", method: "POST", body: { email: "a@test.com", purpose: "SIGNUP" } }]);
  assert.equal(sent.expiresAt, received + 300_000);
  assert.equal(sent.resendAt, received + 60_000);
});

test("#279-S12 기기 시계가 서버보다 5분 빨라도 남은 시간은 서버가 준 그대로다", async () => {
  const { http } = server(() => Response.json(SENT, { status: 202 }));
  // 서버 기준 만료(18:57:56)보다 기기 시계가 이미 늦은 경우: 서버 시각과 바로 비교했다면 0:00이 된다
  const deviceNow = Date.parse("2026-11-27T18:57:56Z") + 5 * 60_000;

  const sent = await createEmailVerificationApi(http, () => deviceNow).send("a@test.com");

  assert.equal(sent.expiresAt - deviceNow, 300_000);
  assert.equal(sent.resendAt - deviceNow, 60_000);
});

test("#279-S12 확인은 번호를 함께 보내고 가입 토큰과 남은 초로 계산한 토큰 만료를 돌려준다", async () => {
  const { http, calls } = server(() => Response.json(CONFIRMED));
  const received = Date.parse("2026-11-28T12:00:00Z");

  const confirmed = await createEmailVerificationApi(http, () => received).confirm("a@test.com", "629503");

  assert.deepEqual(calls, [{
    url: "/api/v1/auth/email-verifications/confirm",
    method: "POST",
    body: { email: "a@test.com", purpose: "SIGNUP", code: "629503" },
  }]);
  assert.equal(confirmed.token, CONFIRMED.verificationToken);
  assert.equal(confirmed.expiresAt, received + 1_800_000);
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
  const now = () => Date.parse("2026-11-28T12:00:00Z");
  const verification = new ServerEmailVerification(createEmailVerificationApi(http, now), now);

  // 보내기 전에는 서버를 부르지 않고 거절한다
  await assert.rejects(verification.verify("a@test.com", "629503"), /먼저 인증 코드를 요청해 주세요/);
  assert.equal(calls.length, 0);

  const sent = await verification.send(" A@Test.com ");
  assert.deepEqual(calls[0].body, { email: "a@test.com", purpose: "SIGNUP" });
  assert.equal(sent.resendAt, now() + 60_000);
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
  const start = Date.parse("2026-11-28T12:00:00Z");
  let now = start;
  const { http } = server((call) => call.url.endsWith("/confirm") ? Response.json(CONFIRMED) : Response.json(SENT, { status: 202 }));
  const verification = new ServerEmailVerification(createEmailVerificationApi(http, () => now), () => now);

  await verification.send("a@test.com");
  await verification.verify("a@test.com", "629503");
  now = start + 1_800_000 - 1;
  assert.equal(verification.isVerified("a@test.com"), true);
  now = start + 1_800_000;
  assert.equal(verification.isVerified("a@test.com"), false);

  now = start;
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
  const verification = new ServerEmailVerification(createEmailVerificationApi(http));

  await verification.send("a@test.com");
  await assert.rejects(verification.send("a@test.com"), { status: 409 });
  await verification.verify("a@test.com", "629503");
  assert.equal(verification.isVerified("a@test.com"), true);
});

test("#279-S12 확인 응답을 기다리는 사이 초기화되면, 늦게 온 응답은 옛 이메일의 토큰을 되살리지 않는다", async () => {
  const late = deferred();
  const { http } = server((call) => call.url.endsWith("/confirm") ? late.promise as unknown as Response : Response.json(SENT, { status: 202 }));
  const verification = new ServerEmailVerification(createEmailVerificationApi(http));

  await verification.send("a@test.com");
  const confirming = verification.verify("a@test.com", "629503");
  // 화면에서 이메일을 바꾸거나 [다시 인증]을 눌렀다
  verification.reset();
  late.resolve(Response.json(CONFIRMED));

  await assert.rejects(confirming, /인증이 초기화됐어요/);
  assert.equal(verification.isVerified("a@test.com"), false);
  assert.equal(verification.token("a@test.com"), null);
  // 초기화 뒤라 보낸 적 없는 상태 그대로다
  await assert.rejects(verification.verify("a@test.com", "629503"), /먼저 인증 코드를 요청해 주세요/);
});

test("#279-S12 보내기 응답을 기다리는 사이 초기화되면, 늦게 온 응답으로 보낸 상태가 되지 않는다", async () => {
  const late = deferred();
  const { http, calls } = server((call) => call.url.endsWith("/confirm") ? Response.json(CONFIRMED) : late.promise as unknown as Response);
  const verification = new ServerEmailVerification(createEmailVerificationApi(http));

  const sending = verification.send("a@test.com");
  verification.reset();
  late.resolve(Response.json(SENT, { status: 202 }));

  await assert.rejects(sending, /인증이 초기화됐어요/);
  await assert.rejects(verification.verify("a@test.com", "629503"), /먼저 인증 코드를 요청해 주세요/);
  assert.equal(calls.filter((call) => call.url.endsWith("/confirm")).length, 0);
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
