import assert from "node:assert/strict";
import test from "node:test";
import { createFamilyApi } from "../features/family/api/familyApi.ts";
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
  return { api: createFamilyApi(http), calls };
}

test("#133-S24 가족 목록은 서버의 숫자 회원 id를 문자열로 바꾸고, 닉네임이 없는 회원도 그대로 둔다", async () => {
  const { api, calls } = server(() =>
    Response.json({
      owner: { memberId: 1, nickname: "다윗", tag: "0427" },
      members: [
        { memberId: 2, nickname: "수민", tag: "1093", joinedAt: "2026-11-25T03:00:00Z" },
        { memberId: 3, nickname: null, tag: null, joinedAt: "2026-11-25T04:00:00Z" },
      ],
    }),
  );

  assert.deepEqual(await api.list("42"), {
    owner: { memberId: "1", nickname: "다윗", tag: "0427" },
    members: [
      { memberId: "2", nickname: "수민", tag: "1093", joinedAt: "2026-11-25T03:00:00Z" },
      { memberId: "3", nickname: null, tag: null, joinedAt: "2026-11-25T04:00:00Z" },
    ],
  });
  assert.deepEqual(calls, [{ url: "/api/v1/families?petId=42", method: "GET", body: undefined }]);
});

test("#133-S24 초대 코드 발급은 petId를 숫자로 보내고 코드와 만료 시각을 그대로 돌려준다", async () => {
  const { api, calls } = server(() =>
    Response.json({ code: "K7Q2M9XA", expiresAt: "2026-11-26T03:00:00Z" }, { status: 201 }),
  );

  assert.deepEqual(await api.issueCode("42"), { code: "K7Q2M9XA", expiresAt: "2026-11-26T03:00:00Z" });
  assert.deepEqual(calls, [{ url: "/api/v1/families/invite-codes", method: "POST", body: { petId: 42 } }]);
});

test("#133-S24 숫자가 아닌 반려동물 id는 서버에 보내지 않고 NOT_FOUND다", async () => {
  const { api, calls } = server(() => assert.fail("예상하지 않은 서버 요청"));

  for (const petId of ["..", "0", "-1", "abc", ""]) {
    await assert.rejects(api.list(petId), { code: "NOT_FOUND" });
    await assert.rejects(api.issueCode(petId), { code: "NOT_FOUND" });
  }
  assert.equal(calls.length, 0);
});

test("#133-S25 코드로 참여는 앞뒤 공백을 지워 보내고, 반려동물 id를 문자열로 바꾼다", async () => {
  const { api, calls } = server(() => Response.json({ petId: 42, name: "보리", tag: "0001" }));

  assert.deepEqual(await api.join("  k7q2m9xa "), { id: "42", name: "보리", tag: "0001" });
  assert.deepEqual(calls, [{ url: "/api/v1/families/members", method: "POST", body: { code: "k7q2m9xa" } }]);
});

test("#133-S25 쓸 수 없는 코드는 서버의 code와 문구를 그대로 전한다", async () => {
  const { api } = server(() =>
    Response.json({ code: "NOT_FOUND", message: "사용할 수 없는 초대 코드입니다." }, { status: 404 }),
  );

  await assert.rejects(api.join("ZZZZZZZZ"), { code: "NOT_FOUND", message: "사용할 수 없는 초대 코드입니다." });
});

test("#133-S23 내가 가족인 반려동물 목록은 id를 문자열로 바꾸고 순서를 지킨다", async () => {
  const { api, calls } = server(() =>
    Response.json([
      { petId: 10, name: "보리", tag: "0001", joinedAt: "2026-11-24T03:00:00Z" },
      { petId: 20, name: "콩이", tag: "0002", joinedAt: "2026-11-25T03:00:00Z" },
    ]),
  );

  assert.deepEqual(await api.myPets(), [
    { id: "10", name: "보리", tag: "0001", joinedAt: "2026-11-24T03:00:00Z" },
    { id: "20", name: "콩이", tag: "0002", joinedAt: "2026-11-25T03:00:00Z" },
  ]);
  assert.deepEqual(calls, [{ url: "/api/v1/families/pets", method: "GET", body: undefined }]);
});

test("#133-S24 로그인이 풀리면 UNAUTHORIZED로 알린다", async () => {
  const { api } = server(() =>
    Response.json({ code: "UNAUTHORIZED", message: "로그인이 필요합니다." }, { status: 401 }),
  );

  await assert.rejects(api.list("42"), { code: "UNAUTHORIZED" });
  await assert.rejects(api.myPets(), { code: "UNAUTHORIZED" });
});

test("#133-S24 소유주의 내보내기는 그 가족의 id와 petId로 본문 없는 DELETE를 보낸다", async () => {
  const { api, calls } = server(() => new Response(null, { status: 204 }));

  await api.removeMember("42", "7");

  assert.deepEqual(calls, [{ url: "/api/v1/families/members/7?petId=42", method: "DELETE", body: undefined }]);
});

test("#133-S24 가족의 나가기는 /members/me와 petId로 본문 없는 DELETE를 보낸다", async () => {
  const { api, calls } = server(() => new Response(null, { status: 204 }));

  await api.leave("42");

  assert.deepEqual(calls, [{ url: "/api/v1/families/members/me?petId=42", method: "DELETE", body: undefined }]);
});

test("#133-S24 숫자가 아닌 가족 id나 반려동물 id는 서버에 보내지 않고 NOT_FOUND다", async () => {
  const { api, calls } = server(() => assert.fail("예상하지 않은 서버 요청"));

  // ../me 같은 값이 경로에 들어가 다른 API(나가기)를 부르지 않게 막는다
  for (const memberId of ["me", "../me", "0", "-1", "abc", ""]) {
    await assert.rejects(api.removeMember("42", memberId), { code: "NOT_FOUND" });
  }
  await assert.rejects(api.removeMember("..", "7"), { code: "NOT_FOUND" });
  await assert.rejects(api.leave("abc"), { code: "NOT_FOUND" });
  assert.equal(calls.length, 0);
});

test("#133-S24 내보내기·나가기 거절은 서버의 code와 문구를 그대로 전한다", async () => {
  for (const [status, code, message] of [
    [403, "FORBIDDEN", "이 반려동물의 소유주나 가족만 할 수 있습니다."],
    [404, "NOT_FOUND", "이 반려동물의 가족이 아닙니다."],
    [409, "CONFLICT", "소유주는 가족에서 빠질 수 없습니다. 먼저 소유주를 넘기거나 반려동물을 삭제해 주세요."],
  ] as const) {
    const { api } = server(() => Response.json({ code, message }, { status }));
    await assert.rejects(api.removeMember("42", "7"), { code, message });
    await assert.rejects(api.leave("42"), { code, message });
  }
});

test("#133-S24 안전한 정수 범위를 넘는 회원 id는 쓰지 않아(null), 서로 다른 두 가족이 같은 id로 섞이지 않는다", async () => {
  // JSON 숫자 9007199254740993은 읽는 순간 9007199254740992가 되어 두 회원이 구별되지 않는다
  const { api } = server(() =>
    new Response(
      '{"owner":{"memberId":1,"nickname":"다윗","tag":"0427"},"members":['
        + '{"memberId":9007199254740993,"nickname":"가","tag":"0001","joinedAt":"2026-11-25T03:00:00Z"},'
        + '{"memberId":9007199254740992,"nickname":"나","tag":"0002","joinedAt":"2026-11-25T04:00:00Z"},'
        + '{"memberId":9007199254740991,"nickname":"다","tag":"0003","joinedAt":"2026-11-25T05:00:00Z"}]}',
      { headers: { "Content-Type": "application/json" } },
    ),
  );

  const family = await api.list("42");

  assert.deepEqual(family.members.map((member) => member.memberId), [null, null, "9007199254740991"]);
});

test("#133-S24 서버가 회원 id를 문자열로 주면 큰 값도 그대로 정확히 쓴다", async () => {
  const { api } = server(() =>
    Response.json({
      owner: { memberId: "1", nickname: "다윗", tag: "0427" },
      members: [{ memberId: "9007199254740993", nickname: "가", tag: "0001", joinedAt: "2026-11-25T03:00:00Z" }],
    }),
  );

  const family = await api.list("42");

  assert.equal(family.owner.memberId, "1");
  assert.equal(family.members[0].memberId, "9007199254740993");
});

test("#133-S24 id를 알 수 없는 회원은 내보내기 요청을 만들 수 없다", async () => {
  const { api, calls } = server(() => assert.fail("예상하지 않은 서버 요청"));

  // 화면은 memberId가 null이면 버튼을 그리지 않는다. 혹시 빈 값이 넘어와도 요청을 보내지 않는다
  await assert.rejects(api.removeMember("42", ""), { code: "NOT_FOUND" });
  assert.equal(calls.length, 0);
});
