import assert from "node:assert/strict";
import test from "node:test";
import { ApplicationError } from "../lib/application/ApplicationError.ts";
import type { ManagedPet } from "../features/pet/types/ManagedPet.ts";
import {
  loadPetManagementList,
  petListScreen,
  type PetListSession,
  type PetListState,
} from "../features/pet/utils/petManagementList.ts";

const http: PetListSession = { ready: true, source: "http", accountId: "A" };

const pet: ManagedPet = {
  id: "42",
  name: "보리",
  species: "DOG",
  mode: "ACTIVE",
  tag: "b0ri",
  breed: null,
  birthday: null,
  profilePhotoUrl: null,
};

test("#85-S3 데모·미확정·로그아웃 상태에서는 목록 요청을 보내지 않는다", async () => {
  const cases = [
    { session: { ...http, source: "demo" }, screen: "demo" },
    { session: { ...http, source: null }, screen: "login-required" },
    { session: { ...http, accountId: null }, screen: "login-required" },
    { session: { ...http, ready: false }, screen: "loading" },
  ] as const;
  let calls = 0;
  for (const { session, screen } of cases) {
    assert.equal(petListScreen(session), screen);
    await loadPetManagementList(
      () => session,
      async () => { calls += 1; return [pet]; },
      () => assert.fail("서버 목록 상태가 표시되면 안 됩니다."),
    );
  }
  assert.equal(calls, 0);
});

const transitions = [
  { name: "데모 출처", session: { ...http, source: "demo" } },
  { name: "다른 HTTP 계정", session: { ...http, accountId: "B" } },
  { name: "확인되지 않은 출처", session: { ...http, source: null } },
  { name: "화면 이탈", session: null },
] as const;

for (const { name, session } of transitions) {
  test(`#85-S3 세션 전환(${name}) 뒤에는 이전 계정의 목록 응답을 폐기한다`, async () => {
    let current: PetListSession | null = http;
    const updates: PetListState[] = [];
    const response = Promise.withResolvers<ManagedPet[]>();
    const request = loadPetManagementList(() => current, () => response.promise,
      (state) => updates.push(state));

    assert.deepEqual(updates, [{ status: "loading" }]);
    current = session;
    response.resolve([pet]);
    await request;
    assert.deepEqual(updates, [{ status: "loading" }]);
  });
}

test("#85-S3 전환 뒤 이전 계정의 401도 현재 계정 화면에 적용하지 않는다", async () => {
  let current = http;
  const updates: PetListState[] = [];
  const response = Promise.withResolvers<ManagedPet[]>();
  const request = loadPetManagementList(() => current, () => response.promise,
    (state) => updates.push(state));

  current = { ...http, accountId: "B" };
  response.reject(new ApplicationError("UNAUTHORIZED", "로그인이 필요합니다."));
  await request;
  assert.deepEqual(updates, [{ status: "loading" }]);
});

test("#85-S3 같은 세션의 401은 로그인 필요 상태로 전환한다", async () => {
  const updates: PetListState[] = [];
  await loadPetManagementList(
    () => http,
    async () => { throw new ApplicationError("UNAUTHORIZED", "로그인이 필요합니다."); },
    (state) => updates.push(state),
  );
  assert.deepEqual(updates, [{ status: "loading" }, { status: "login-required" }]);
});

test("#85-S1 조회가 끝나기 전에는 로딩이고 빈 배열이 오면 빈 목록이 된다", async () => {
  const updates: PetListState[] = [];
  const response = Promise.withResolvers<ManagedPet[]>();
  const request = loadPetManagementList(() => http, () => response.promise,
    (state) => updates.push(state));

  assert.deepEqual(updates, [{ status: "loading" }]);
  response.resolve([]);
  await request;
  assert.deepEqual(updates, [{ status: "loading" }, { status: "ready", pets: [] }]);
});

test("#85-S2 실패 뒤 재시도가 성공하면 오류 없이 서버 목록을 표시한다", async () => {
  const updates: PetListState[] = [];
  let calls = 0;
  async function listMine() {
    calls += 1;
    if (calls === 1) throw new Error("조회 실패");
    return [pet];
  }

  for (let attempt = 0; attempt < 2; attempt += 1) {
    await loadPetManagementList(() => http, listMine, (state) => updates.push(state));
  }
  assert.deepEqual(updates, [
    { status: "loading" },
    { status: "error", message: "조회 실패" },
    { status: "loading" },
    { status: "ready", pets: [pet] },
  ]);
});
