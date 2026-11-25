import assert from "node:assert/strict";
import test from "node:test";
import { createPetManagementApi } from "../features/pet/api/petManagementApi.ts";
import { createApiClient } from "../lib/http/apiClient.ts";

test("#85-S6 관리 상세는 URL의 반려동물 ID로 서버에서 직접 조회한다", async () => {
  const petId = "9007199254740993";
  const http = createApiClient({
    async fetch(url, options) {
      assert.equal(url, `/api/v1/pets/${petId}`);
      assert.equal(options?.method, "GET");
      assert.equal(options?.credentials, "include");
      return Response.json({
        petId, name: "보리", tag: "b0ri", species: "DOG", mode: "GARDEN",
        breed: "코기", birthday: "2021-04-12", profilePhoto: null,
      });
    },
  });

  assert.deepEqual(await createPetManagementApi(http).get(petId), {
    id: petId, name: "보리", tag: "b0ri", species: "DOG", mode: "GARDEN",
    breed: "코기", birthday: "2021-04-12", profilePhotoUrl: null,
  });
});

test("#85-S6 상세 오류의 로그인·접근 불가·대상 없음은 구분된다", async () => {
  for (const [status, code] of [[401, "UNAUTHORIZED"], [403, "FORBIDDEN"], [404, "NOT_FOUND"]] as const) {
    const http = createApiClient({ fetch: async () => new Response(null, { status }) });
    await assert.rejects(createPetManagementApi(http).get("42"), { code });
  }
});

test("#85-S6 유효하지 않은 상세 ID는 서버 요청을 보내지 않는다", async () => {
  const http = createApiClient({ fetch: async () => assert.fail("예상하지 않은 서버 요청") });
  await assert.rejects(createPetManagementApi(http).get(".."), { code: "NOT_FOUND" });
});
