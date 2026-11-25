import assert from "node:assert/strict";
import test from "node:test";
import { createPetManagementApi } from "../features/pet/api/petManagementApi.ts";
import { profileChanges, rebaseProfileInput } from "../features/pet/utils/petManagementEdit.ts";
import type { RegisterPetInput } from "../features/pet/types/ManagedPet.ts";
import { createApiClient, type ApiClient } from "../lib/http/apiClient.ts";

const original: RegisterPetInput = {
  name: "콩이",
  species: "DOG",
  breed: "푸들",
  birthday: "2015-03-02",
};

test("#85-S7 수정한 필드만 PATCH하고 품종 삭제는 빈 문자열을 전송한다", async () => {
  const requests: unknown[] = [];
  const http = {
    patch: async <T>(path: string, body: unknown): Promise<T> => {
      requests.push({ path, body });
      return undefined as T;
    },
  } as ApiClient;
  const api = createPetManagementApi(http);
  const changes = profileChanges(original, { ...original, name: " 콩콩 ", breed: "" });
  await api.edit("15", changes);
  assert.deepEqual(requests, [{ path: "/pets/15", body: { name: "콩콩", breed: "" } }]);
});

test("#85-S7 편집하지 않은 생일은 생략하고 기존 생일 지우기는 거절한다", () => {
  assert.deepEqual(profileChanges(original, original), {});
  assert.throws(
    () => profileChanges(original, { ...original, birthday: "" }),
    /기존 생일을 유지하거나/,
  );
});

test("#85-S8 수정 API는 HTTP 실패를 화면이 해석할 수 있는 앱 오류로 변환한다", async () => {
  for (const [status, code] of [
    [401, "UNAUTHORIZED"],
    [403, "FORBIDDEN"],
    [404, "NOT_FOUND"],
    [409, "CONFLICT"],
  ] as const) {
    const http = createApiClient({
      fetch: async () => Response.json({ code: "SERVER_CODE", message: "실패" }, { status }),
    });
    await assert.rejects(createPetManagementApi(http).edit("15", { name: "콩콩" }), {
      name: "ApplicationError",
      code,
      message: "실패",
    });
  }
});

test("#85-S8 최신 프로필과 병합해도 미저장 편집을 유지하고 변경한 필드만 다시 보낸다", () => {
  const input = { ...original, name: " 콩콩 ", breed: "" };
  const latest: RegisterPetInput = {
    name: "서버 이름",
    species: "CAT",
    breed: "서버 품종",
    birthday: "2016-04-05",
  };

  const merged = rebaseProfileInput(original, input, latest);
  assert.deepEqual(merged, { ...latest, name: " 콩콩 ", breed: "" });
  assert.deepEqual(profileChanges(latest, merged), { name: "콩콩", breed: "" });
});

test("#85-S8 앞뒤 공백만 바꾼 입력은 서버의 최신 이름이나 품종을 덮지 않는다", () => {
  const latest = { ...original, name: "서버 이름", breed: "서버 품종" };
  const input = { ...original, name: " 콩이 ", breed: " 푸들 " };

  assert.deepEqual(rebaseProfileInput(original, input, latest), latest);
});
