import assert from "node:assert/strict";
import test from "node:test";

import { createPetManagementApi } from "../features/pet/api/petManagementApi.ts";
import type { RegisterPetInput } from "../features/pet/types/ManagedPet.ts";
import { createApiClient } from "../lib/http/apiClient.ts";

const input: RegisterPetInput = { name: " 보리 ", species: "DOG", breed: "", birthday: "" };

test("#85-S4 등록은 서버에 한 번 요청하고 응답의 ID·태그·모드를 사용한다", async () => {
  let calls = 0;
  const http = createApiClient({
    async fetch(url, options) {
      calls += 1;
      assert.equal(url, "/api/v1/pets");
      assert.equal(options?.method, "POST");
      assert.equal(options?.credentials, "include");
      assert.deepEqual(JSON.parse(String(options?.body)), { name: "보리", species: "DOG" });
      return Response.json({
        petId: 85, name: "보리", tag: "b0ri", species: "DOG", breed: null,
        birthday: null, mode: "ACTIVE", profilePhoto: null,
      }, { status: 201 });
    },
  });

  const registered = await createPetManagementApi(http).register(input);
  assert.deepEqual([registered.id, registered.tag, registered.mode], ["85", "b0ri", "ACTIVE"]);
  assert.equal(calls, 1);
});

test("#85-S5 빈 이름·21자 이름·미래 생일은 요청을 보내기 전에 거절한다", async () => {
  let calls = 0;
  const http = createApiClient({
    async fetch() {
      calls += 1;
      return new Response(null, { status: 500 });
    },
  });
  const api = createPetManagementApi(http);

  for (const invalid of [{ name: " " }, { name: "보".repeat(21) }, { birthday: "2999-01-01" }]) {
    await assert.rejects(api.register({ ...input, ...invalid }), { code: "VALIDATION" });
  }
  assert.equal(calls, 0);
});

test("#85-S5 서버 검증 오류를 안내에 사용할 수 있게 전달한다", async () => {
  const http = createApiClient({
    fetch: async () => Response.json({ code: "VALIDATION", message: "서버 입력 오류" }, { status: 400 }),
  });
  await assert.rejects(createPetManagementApi(http).register(input), {
    code: "VALIDATION", message: "서버 입력 오류",
  });
});

test("#85-S5 응답을 못 받은 등록은 다시 전송하지 않는다", async () => {
  let calls = 0;
  const http = createApiClient({
    async fetch() {
      calls += 1;
      throw new TypeError("network unavailable");
    },
  });

  await assert.rejects(createPetManagementApi(http).register(input), { code: "PET_REGISTRATION_UNCONFIRMED" });
  assert.equal(calls, 1);
});
