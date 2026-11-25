import assert from "node:assert/strict";
import test from "node:test";
import { createApiClient } from "../lib/http/apiClient.ts";
import { createPetManagementApi } from "../features/pet/api/petManagementApi.ts";
import { createModeSubmission } from "../features/pet/utils/petManagementMode.ts";

const pet = { id: "15", mode: "ACTIVE" } as const;

test("#85-S9 확인한 대상의 모드만 PATCH하고 동일 모드는 보내지 않는다", async () => {
  const requests: unknown[] = [];
  const http = createApiClient({
    fetch: async (url, init) => {
      requests.push([url, init?.method, JSON.parse(String(init?.body))]);
      return Response.json({});
    },
  });
  const submit = createModeSubmission(() => true, createPetManagementApi(http).changeMode);

  assert.equal(await submit(pet, "ACTIVE"), undefined);
  assert.deepEqual(await submit(pet, "GARDEN"), { status: "saved" });
  assert.deepEqual(requests, [["/api/v1/pets/15/mode", "PATCH", { mode: "GARDEN" }]]);
  assert.equal(pet.mode, "ACTIVE");
});

test("#85-S10 진행 중인 변경은 중복 제출하지 않고 화면 이탈 뒤 응답을 버린다", async () => {
  const gate = Promise.withResolvers<void>();
  let active = true;
  let calls = 0;
  const submit = createModeSubmission(() => active, () => {
    calls += 1;
    return gate.promise;
  });

  const first = submit(pet, "GARDEN");
  assert.equal(await submit(pet, "GARDEN"), undefined);
  active = false;
  gate.resolve();
  assert.equal(await first, undefined);
  assert.equal(await submit(pet, "GARDEN"), undefined);
  assert.equal(calls, 1);
});

test("#85-S10 서버 권한·대상·충돌·통신 오류는 모드 저장 성공으로 처리하지 않는다", async () => {
  for (const status of [401, 403, 404, 409, null]) {
    const api = createPetManagementApi(createApiClient({
      fetch: async () => {
        if (status === null) throw new Error("연결이 끊겼어요.");
        return Response.json({ message: "저장 실패" }, { status });
      },
    }));
    const submit = createModeSubmission(() => true, api.changeMode);
    const result = await submit(pet, "GARDEN");
    assert.equal(result?.status, status === 401 ? "login-required" : "error");
    assert.equal(pet.mode, "ACTIVE");
  }
});
