import assert from "node:assert/strict";
import test from "node:test";
import { createPetManagementApi } from "../features/pet/api/petManagementApi.ts";
import { profileChanges } from "../features/pet/utils/petManagementEdit.ts";
import type { RegisterPetInput } from "../features/pet/types/ManagedPet.ts";
import type { ApiClient } from "../lib/http/apiClient.ts";

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
