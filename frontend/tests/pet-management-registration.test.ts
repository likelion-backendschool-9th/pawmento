import assert from "node:assert/strict";
import test from "node:test";

import { createPetManagementApi } from "../features/pet/api/petManagementApi.ts";
import type { ManagedPet, RegisterPetInput } from "../features/pet/types/ManagedPet.ts";
import {
  createPetRegistrationSubmission,
  type RegistrationState,
} from "../features/pet/utils/petRegistrationSubmission.ts";
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

const pet: ManagedPet = {
  id: "85", name: "보리", tag: "b0ri", species: "DOG", breed: null,
  birthday: null, mode: "ACTIVE", profilePhotoUrl: null,
};

test("#85-S5 진행 중 두 번째 제출을 무시하고 성공 처리를 한 번만 한다", async () => {
  let calls = 0;
  const response = Promise.withResolvers<ManagedPet>();
  const form = registrationForm(async () => {
    calls += 1;
    return response.promise;
  });

  const pending = form.submit();
  await form.submit();
  assert.equal(calls, 1);
  assert.deepEqual(form.states, [{ status: "saving" }]);
  assert.deepEqual(form.saved, []);

  response.resolve(pet);
  await pending;
  assert.deepEqual(form.states, [{ status: "saving" }, { status: "saved" }]);
  assert.deepEqual(form.saved, [pet]);
});

test("#85-S5 400이면 오류를 표시하고 같은 입력으로 다시 제출할 수 있다", async () => {
  const requested: unknown[] = [];
  const http = createApiClient({
    async fetch(_url, options) {
      requested.push(JSON.parse(String(options?.body)));
      return Response.json({ code: "VALIDATION", message: "서버 입력 오류" }, { status: 400 });
    },
  });
  const form = registrationForm(createPetManagementApi(http).register);

  await form.submit();
  assert.deepEqual(form.states, [{ status: "saving" }, { status: "idle", message: "서버 입력 오류" }]);
  assert.deepEqual(form.saved, []);
  await form.submit();
  assert.deepEqual(requested, [
    { name: "보리", species: "DOG" },
    { name: "보리", species: "DOG" },
  ]);
  assert.deepEqual(form.saved, []);
});

test("#85-S5 등록 결과가 불명확하면 목록 확인을 안내하고 재제출을 막는다", async () => {
  let calls = 0;
  const http = createApiClient({
    async fetch() {
      calls += 1;
      throw new TypeError("network unavailable");
    },
  });
  const form = registrationForm(createPetManagementApi(http).register);

  await form.submit();
  await form.submit();
  assert.equal(calls, 1);
  assert.deepEqual(form.states, [
    { status: "saving" },
    { status: "unconfirmed", message: "등록 결과를 확인하지 못했어요. 다시 등록하기 전에 목록을 확인해 주세요." },
  ]);
  assert.deepEqual(form.saved, []);
});

test("#85-S3 활성 출처가 demo면 서버 등록을 시작하지 않는다", async () => {
  let calls = 0;
  const form = registrationForm(async () => {
    calls += 1;
    return pet;
  });
  form.changeSession({ source: "demo", accountId: "A" });

  await form.submit();
  assert.equal(calls, 0);
  assert.deepEqual(form.states, []);
  assert.deepEqual(form.saved, []);
});

test("#85-S3 출처·계정 전환 또는 화면 이탈 후 늦은 등록 결과를 폐기한다", async () => {
  for (const session of [
    { source: "demo", accountId: "A" },
    { source: "http", accountId: "B" },
    null,
  ] as const) {
    const response = Promise.withResolvers<ManagedPet>();
    const form = registrationForm(() => response.promise);
    const pending = form.submit();
    form.changeSession(session);
    response.resolve(pet);
    await pending;
    assert.deepEqual(form.states, [{ status: "saving" }]);
    assert.deepEqual(form.saved, []);
  }
});

test("#85-S3 같은 HTTP 세션의 401은 로그인 안내를 표시한다", async () => {
  const http = createApiClient({
    fetch: async () => Response.json({ code: "UNAUTHORIZED", message: "로그인이 필요해요." }, { status: 401 }),
  });
  const form = registrationForm(createPetManagementApi(http).register);

  await form.submit();
  assert.deepEqual(form.states, [
    { status: "saving" },
    { status: "login-required", message: "로그인이 필요해요." },
  ]);
  assert.deepEqual(form.saved, []);
});

function registrationForm(register: (input: RegisterPetInput) => Promise<ManagedPet>) {
  type Session = ReturnType<Parameters<typeof createPetRegistrationSubmission>[0]>;
  let session: Session = { source: "http", accountId: "A" };
  const submit = createPetRegistrationSubmission(() => session);
  const states: RegistrationState[] = [];
  const saved: ManagedPet[] = [];
  return {
    states,
    saved,
    changeSession(next: Session) { session = next; },
    submit: () => submit(input, register, (state) => states.push(state), (result) => saved.push(result)),
  };
}
