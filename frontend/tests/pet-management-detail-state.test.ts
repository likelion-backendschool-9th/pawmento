import assert from "node:assert/strict";
import test from "node:test";

import type { ManagedPet } from "../features/pet/types/ManagedPet.ts";
import {
  loadPetManagementDetail,
  petDetailScreen,
  type PetDetailSession,
  type PetDetailState,
} from "../features/pet/utils/petManagementDetail.ts";
import { ApplicationError } from "../lib/application/ApplicationError.ts";

const http: PetDetailSession = { ready: true, source: "http", accountId: "A", petId: "42" };
const pet: ManagedPet = {
  id: "42", name: "보리", species: "DOG", mode: "ACTIVE", tag: "b0ri",
  breed: null, birthday: null, profilePhotoUrl: null,
};

test("#85-S3 데모·미확정 출처·로그아웃 상태에서는 상세 요청을 보내지 않는다", async () => {
  const cases = [
    { session: { ...http, source: "demo" }, screen: "login-required" },
    { session: { ...http, source: null }, screen: "login-required" },
    { session: { ...http, accountId: null }, screen: "login-required" },
    { session: { ...http, ready: false }, screen: "loading" },
  ] as const;

  for (const { session, screen } of cases) {
    assert.equal(petDetailScreen(session), screen);
    await loadPetManagementDetail(
      () => session,
      async () => assert.fail("서버 상세 요청을 보내면 안 됩니다."),
      () => assert.fail("서버 상세 상태가 표시되면 안 됩니다."),
    );
  }
});

const transitions = [
  { name: "데모 출처", session: { ...http, source: "demo" } },
  { name: "다른 HTTP 계정", session: { ...http, accountId: "B" } },
  { name: "다른 petId", session: { ...http, petId: "43" } },
  { name: "화면 이탈", session: null },
] as const;

for (const { name, session } of transitions) {
  test(`#85-S3/S6 전환(${name}) 후 이전 프로필 응답을 폐기한다`, async () => {
    let current: PetDetailSession | null = http;
    const updates: PetDetailState[] = [];
    const response = Promise.withResolvers<ManagedPet>();
    const pending = loadPetManagementDetail(
      () => current,
      (petId) => {
        assert.equal(petId, "42");
        return response.promise;
      },
      (state) => updates.push(state),
    );

    assert.deepEqual(updates, [{ status: "loading" }]);
    current = session;
    response.resolve(pet);
    await pending;
    assert.deepEqual(updates, [{ status: "loading" }]);
  });
}

test("#85-S3 같은 HTTP 세션의 401만 로그인 필요 상태에 반영한다", async () => {
  let current = http;
  const updates: PetDetailState[] = [];
  const previous = Promise.withResolvers<ManagedPet>();
  const unauthorized = new ApplicationError("UNAUTHORIZED", "로그인이 필요합니다.");
  const pending = loadPetManagementDetail(
    () => current, () => previous.promise, (state) => updates.push(state),
  );
  current = { ...http, accountId: "B" };
  previous.reject(unauthorized);
  await pending;
  assert.deepEqual(updates, [{ status: "loading" }]);

  await loadPetManagementDetail(
    () => current,
    async () => { throw unauthorized; },
    (state) => updates.push(state),
  );
  assert.deepEqual(updates.slice(1), [{ status: "loading" }, { status: "login-required" }]);
});

test("#85-S6 상세 접근 불가와 대상 없음을 구분한다", async () => {
  const cases = [
    { code: "FORBIDDEN", message: "이 반려동물의 프로필을 볼 권한이 없어요." },
    { code: "NOT_FOUND", message: "반려동물을 찾을 수 없어요." },
  ];
  for (const { code, message } of cases) {
    const updates: PetDetailState[] = [];
    await loadPetManagementDetail(
      () => http,
      async () => { throw new ApplicationError(code, "서버 오류 응답"); },
      (state) => updates.push(state),
    );
    assert.deepEqual(updates, [{ status: "loading" }, { status: "error", message }]);
  }
});

test("#85-S6 일시적인 조회 오류는 재시도를 제공하고 성공한 프로필을 표시한다", async () => {
  const updates: PetDetailState[] = [];
  let calls = 0;
  async function get(petId: string) {
    assert.equal(petId, "42");
    if (++calls === 1) throw new Error("조회 실패");
    return pet;
  }

  for (let attempt = 0; attempt < 2; attempt += 1) {
    await loadPetManagementDetail(() => http, get, (state) => updates.push(state));
  }
  assert.deepEqual(updates, [
    { status: "loading" },
    { status: "error", message: "조회 실패", retry: true },
    { status: "loading" },
    { status: "ready", pet },
  ]);
});
