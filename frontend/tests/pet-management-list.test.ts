import assert from "node:assert/strict";
import test from "node:test";

import { createPetManagementApi } from "../features/pet/api/petManagementApi.ts";
import { createApiClient } from "../lib/http/apiClient.ts";

test("#85-S1 관리 목록은 세션 쿠키와 함께 서버에서 프로필을 조회한다", async () => {
  const http = createApiClient({
    async fetch(url, options) {
      assert.equal(url, "/api/v1/pets");
      assert.equal(options?.method, "GET");
      assert.equal(options?.credentials, "include");
      return Response.json([
        {
          petId: 42,
          name: "보리",
          tag: "b0ri",
          species: "DOG",
          breed: "웰시코기",
          birthday: "2021-04-12",
          mode: "GARDEN",
          profilePhoto: { thumbnailUrl: "/api/v1/pets/42/profile-photo/9/thumbnail" },
        },
        {
          petId: "9007199254740993",
          name: "콩이",
          tag: "k0ng",
          species: "CAT",
          breed: null,
          birthday: null,
          mode: "ACTIVE",
          profilePhoto: null,
        },
      ]);
    },
  });

  const pets = await createPetManagementApi(http).listMine();
  assert.deepEqual(pets, [
    {
      id: "42",
      name: "보리",
      tag: "b0ri",
      species: "DOG",
      breed: "웰시코기",
      birthday: "2021-04-12",
      mode: "GARDEN",
      profilePhotoUrl: "/api/v1/pets/42/profile-photo/9/thumbnail",
    },
    {
      id: "9007199254740993",
      name: "콩이",
      tag: "k0ng",
      species: "CAT",
      breed: null,
      birthday: null,
      mode: "ACTIVE",
      profilePhotoUrl: null,
    },
  ]);
});

test("#85-S1 서버에 반려동물이 없으면 빈 목록을 반환한다", async () => {
  const http = createApiClient({ fetch: async () => Response.json([]) });
  assert.deepEqual(await createPetManagementApi(http).listMine(), []);
});

test("#85-S2 조회 실패를 전달하고 재시도하면 서버에서 다시 조회한다", async () => {
  let calls = 0;
  const http = createApiClient({
    async fetch() {
      calls += 1;
      return calls === 1
        ? Response.json({ code: "INTERNAL_ERROR", message: "잠시 후 다시 시도해 주세요." }, { status: 500 })
        : Response.json([]);
    },
  });
  const api = createPetManagementApi(http);

  await assert.rejects(api.listMine(), { code: "INTERNAL_ERROR" });
  assert.deepEqual(await api.listMine(), []);
  assert.equal(calls, 2);
});

test("#85-S3 응답 본문이 없어도 401은 로그인 필요 오류로 전달한다", async () => {
  const http = createApiClient({ fetch: async () => new Response(null, { status: 401 }) });
  await assert.rejects(createPetManagementApi(http).listMine(), { code: "UNAUTHORIZED" });
});
