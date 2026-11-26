import test from "node:test";
import assert from "node:assert/strict";
import { createApiClient, ApiError } from "../lib/http/apiClient.ts";
import { createMemberApi } from "../features/member/api/memberApi.ts";
import { createPetApi } from "../features/pet/api/petApi.ts";
import { resolveProtectedAccess } from "../features/member/utils/access.ts";

type MockCall = {
  url: string;
  method: string;
  body?: string | null;
  credentials?: RequestCredentials;
};

function mockFetch(handler: (call: MockCall) => Response | Promise<Response>) {
  const calls: MockCall[] = [];
  const fetchImpl: typeof fetch = async (input, init) => {
    const url = String(input);
    const method = (init?.method ?? "GET").toUpperCase();
    const body =
      typeof init?.body === "string"
        ? init.body
        : init?.body == null
          ? null
          : String(init.body);
    const call: MockCall = {
      url,
      method,
      body,
      credentials: init?.credentials,
    };
    calls.push(call);
    return handler(call);
  };
  return { fetchImpl, calls };
}

function jsonResponse(status: number, body: unknown) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

test("#52-S1 로그인 세션이면 GET /members/me로 닉네임·이메일을 받는다", async () => {
  const { fetchImpl, calls } = mockFetch((call) => {
    assert.match(call.url, /\/api\/v1\/members\/me$/);
    assert.equal(call.method, "GET");
    assert.equal(call.credentials, "include");
    return jsonResponse(200, {
      id: 7,
      email: "a@example.com",
      nickname: "보리 보호자",
      role: "USER",
    });
  });
  const auth = createMemberApi(createApiClient({ fetch: fetchImpl }));
  const account = await auth.current();
  assert.deepEqual(account, {
    id: "7",
    email: "a@example.com",
    nickname: "보리 보호자",
    role: "USER",
  });
  assert.equal(calls.length, 1);
});

test("#52-S2 세션이 없으면 마이페이지 본문 대신 로그인이 필요하다", () => {
  assert.equal(resolveProtectedAccess(false, null), "loading");
  assert.equal(resolveProtectedAccess(true, null), "login-required");
  assert.equal(
    resolveProtectedAccess(true, {
      id: "1",
      email: "a@example.com",
      nickname: "보리 보호자",
    }),
    "allowed",
  );
});

test("#52-S2 쿠키가 없으면 current는 null이고 me 본문을 열 수 없다", async () => {
  const { fetchImpl } = mockFetch(() =>
    jsonResponse(401, { code: "UNAUTHORIZED", message: "로그인이 필요합니다." }),
  );
  const auth = createMemberApi(createApiClient({ fetch: fetchImpl }));
  assert.equal(await auth.current(), null);
  assert.equal(resolveProtectedAccess(true, await auth.current()), "login-required");
});

test("#52-S3 닉네임 저장은 PATCH /members/me이고 화면에 반영할 값을 돌려준다", async () => {
  const { fetchImpl, calls } = mockFetch((call) => {
    assert.equal(call.method, "PATCH");
    assert.match(call.url, /\/api\/v1\/members\/me$/);
    assert.equal(JSON.parse(call.body ?? "{}").nickname, "콩이 보호자");
    return jsonResponse(200, {
      id: 7,
      email: "a@example.com",
      nickname: "콩이 보호자",
      role: "USER",
    });
  });
  const auth = createMemberApi(createApiClient({ fetch: fetchImpl }));
  const account = await auth.update("콩이 보호자");
  assert.equal(account.nickname, "콩이 보호자");
  assert.equal(calls.length, 1);
});

test("#52-S4 같은 달 재변경은 409 CONFLICT이고 닉네임은 그대로다", async () => {
  let nickname = "첫번째";
  const { fetchImpl } = mockFetch((call) => {
    if (call.method === "PATCH") {
      const next = JSON.parse(call.body ?? "{}").nickname as string;
      if (nickname !== "첫번째" || next === "첫번째") {
        nickname = next;
        return jsonResponse(200, {
          id: 7,
          email: "a@example.com",
          nickname,
          role: "USER",
        });
      }
      return jsonResponse(409, {
        code: "CONFLICT",
        message: "닉네임은 한 달에 한 번만 바꿀 수 있어요.",
      });
    }
    return jsonResponse(200, {
      id: 7,
      email: "a@example.com",
      nickname,
      role: "USER",
    });
  });
  const http = createApiClient({ fetch: fetchImpl });
  const auth = createMemberApi(http);
  await auth.update("첫번째");
  await assert.rejects(() => auth.update("두번째"), (error: unknown) => {
    assert.ok(error instanceof ApiError);
    assert.equal(error.status, 409);
    assert.equal(error.code, "CONFLICT");
    return true;
  });
  assert.equal((await auth.current())?.nickname, "첫번째");
});

test("#52-S6 반려동물이 있으면 친구 수는 GET /pets 목록 길이다", async () => {
  const { fetchImpl, calls } = mockFetch(() =>
    jsonResponse(200, [
      { petId: 1, name: "콩이" },
      { petId: 2, name: "보리" },
    ]),
  );
  const pets = createPetApi(createApiClient({ fetch: fetchImpl }));
  const list = await pets.listMine();
  // 서버의 숫자 id는 문자열로 바꾼 아이 요약이 된다
  assert.deepEqual(list, [
    { id: "1", name: "콩이" },
    { id: "2", name: "보리" },
  ]);
  assert.equal(calls[0]!.url, "/api/v1/pets");
  assert.equal(calls[0]!.method, "GET");
});

test("#52-S7 반려동물이 없으면 친구 수는 0이다", async () => {
  const { fetchImpl } = mockFetch(() => jsonResponse(200, []));
  const pets = createPetApi(createApiClient({ fetch: fetchImpl }));
  assert.equal((await pets.listMine()).length, 0);
});

test("#52-S8 로그아웃은 POST /auth/logout이고 이후 me 접근이 막힌다", async () => {
  let loggedIn = true;
  const { fetchImpl, calls } = mockFetch((call) => {
    if (call.method === "POST" && call.url.includes("/auth/logout")) {
      assert.equal(call.credentials, "include");
      assert.equal(call.body, "{}");
      loggedIn = false;
      return new Response(null, { status: 204 });
    }
    if (call.url.includes("/members/me")) {
      if (!loggedIn) {
        return jsonResponse(401, {
          code: "UNAUTHORIZED",
          message: "로그인이 필요합니다.",
        });
      }
      return jsonResponse(200, {
        id: 7,
        email: "a@example.com",
        nickname: "보리 보호자",
        role: "USER",
      });
    }
    return jsonResponse(500, { message: "unexpected" });
  });
  const auth = createMemberApi(createApiClient({ fetch: fetchImpl }));
  assert.ok(await auth.current());
  await auth.signOut();
  assert.ok(calls.some((call) => call.url.includes("/auth/logout")));
  assert.equal(await auth.current(), null);
  assert.equal(resolveProtectedAccess(true, null), "login-required");
});
