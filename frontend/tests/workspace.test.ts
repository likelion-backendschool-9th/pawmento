import test from "node:test";
import assert from "node:assert/strict";
import { createWorkspaceUseCases } from "../mocks/handlers/workspaceUseCases.ts";
import type { DiaryEntryInput } from "../features/diary/types/DiaryEntry.ts";
import type { PetInput } from "../features/pet/types/Pet.ts";
import type { Workspace } from "../features/shell/types/workspace.ts";
import { validateDate } from "../lib/date.ts";
import { createApiClient } from "../lib/http/apiClient.ts";
import { createMemberSession } from "../features/member/session.ts";
import { DemoAuthRepository } from "../mocks/handlers/DemoAuthRepository.ts";
import { BrowserWorkspaceRepository } from "../mocks/handlers/BrowserWorkspaceRepository.ts";
function setup() {
  const saved = new Map<string, Workspace>();
  let fail = false;
  let counter = 0;
  const repository = {
    load: async (id: string) =>
      structuredClone(
        saved.get(id) ?? {
          pets: [],
          diaries: [],
          questionAnswers: [],
          questionPrompts: [],
          selectedPetId: "",
        },
      ),
    save: async (id: string, state: Workspace) => {
      if (fail) throw Error("storage full");
      saved.set(id, structuredClone(state));
    },
  };
  return {
    useCases: createWorkspaceUseCases(repository, {
      id: () => String(++counter),
      now: () => new Date().toISOString(),
    }),
    fail: () => {
      fail = true;
    },
    recover: () => {
      fail = false;
    },
  };
}
const pet: PetInput = {
  name: "보리",
  species: "DOG",
  breed: "웰시코기",
  birthday: "2021-04-12",
  photoUrl: "",
};
const memory = (
  petId: string,
  overrides: Partial<DiaryEntryInput> = {},
): DiaryEntryInput => ({
  title: "함께 걸은 날",
  petId,
  recordedDate: "2026-11-15",
  body: "함께 걸은 하루",
  photoUrl: "",
  ...overrides,
});
test("#12-S1 가입·중복 이메일·잘못된 비밀번호·로그아웃과 탭 격리", async () => {
  const input = {
    email: "  TEST@example.com ",
    password: "local-demo-pass",
    confirmation: "local-demo-pass",
    nickname: "  보호자  ",
  };
  const requests: { path: string; body: Record<string, string> }[] = [];
  const auth = createMemberSession(
    createApiClient({
      fetch: async (target, init) => {
        const url = String(target);
        const path = url.endsWith("/auth/signup")
          ? "/auth/signup"
          : url.endsWith("/auth/login")
            ? "/auth/login"
            : url;
        requests.push({ path, body: JSON.parse(String(init?.body ?? "{}")) });
        return new Response(
          JSON.stringify({
            id: 3,
            email: "test@example.com",
            nickname: "보호자",
            role: "USER",
          }),
          { status: 200, headers: { "Content-Type": "application/json" } },
        );
      },
    }),
    new DemoAuthRepository(),
  );
  const account = await auth.signUp(input);
  assert.equal(account.email, "test@example.com");
  assert.equal(account.nickname, "보호자");
  assert.deepEqual(
    requests.map((request) => request.path),
    ["/auth/signup", "/auth/login"],
  );
  assert.equal(requests[0]?.body.email, "test@example.com");
  assert.equal(requests[0]?.body.nickname, "보호자");
  assert.equal(requests[0]?.body.password, input.password);
  const repository = new DemoAuthRepository();
  const normalized = {
    email: "test@example.com",
    password: input.password,
    nickname: "보호자",
  };
  const demoAccount = await repository.signUp(normalized);
  await assert.rejects(repository.signUp(normalized), /이미 가입/);
  await repository.signOut();
  assert.equal(await repository.current(), null);
  await assert.rejects(
    repository.signIn({ email: demoAccount.email, password: "incorrect" }),
    /확인/,
  );
  assert.equal(
    (
      await repository.signIn({
        email: demoAccount.email,
        password: input.password,
      })
    ).id,
    demoAccount.id,
  );
  assert.equal(await new DemoAuthRepository().current(), null);
  await repository.update("새 이름");
  await repository.signOut();
  assert.equal(
    (
      await repository.signIn({
        email: demoAccount.email,
        password: input.password,
      })
    ).nickname,
    "새 이름",
  );
});
test("#12-S1 가입 필수 값과 비밀번호 확인 검증", () => {
  const auth = createMemberSession(
    createApiClient({
      fetch: async () => {
        throw Error("형식 확인 전에 요청하면 안 돼요.");
      },
    }),
    new DemoAuthRepository(),
  );
  assert.throws(
    () =>
      auth.signUp({
        email: "invalid",
        password: "12345678",
        confirmation: "12345678",
        nickname: "이름",
      }),
    /이메일/,
  );
  assert.throws(
    () =>
      auth.signUp({
        email: "a@b.com",
        password: "12345678",
        confirmation: "different",
        nickname: "이름",
      }),
    /일치/,
  );
  assert.throws(
    () =>
      auth.signUp({
        email: "a@b.com",
        password: "12345678",
        confirmation: "12345678",
        nickname: " ",
      }),
    /닉네임/,
  );
  assert.throws(
    () => auth.signIn({ email: "a@b.com", password: "short" }),
    /8~72/,
  );
});
test("#12-S2 두 반려동물과 서로 다른 계정 간 기록 격리·선택·수정", async () => {
  const { useCases: uc } = setup();
  const a = await uc.savePet("owner", pet);
  const b = await uc.savePet("owner", { ...pet, name: "나비", species: "CAT" });
  await uc.diary.save("owner", memory(a.id));
  await uc.diary.save("owner", memory(b.id, { body: "나비의 하루" }));
  await uc.selectPet("owner", a.id);
  assert.equal((await uc.load("owner")).selectedPetId, a.id);
  assert.equal(
    (await uc.diary.list("owner", a.id)).entries[0].body,
    "함께 걸은 하루",
  );
  assert.equal(
    (await uc.diary.list("owner", b.id)).entries[0].body,
    "나비의 하루",
  );
  assert.equal((await uc.load("someone")).pets.length, 0);
  await uc.savePet("owner", { ...pet, name: "보리보리" }, a.id);
  assert.equal((await uc.load("owner")).pets[0].name, "보리보리");
  assert.equal((await uc.diary.list("owner", a.id)).total, 1);
  await assert.rejects(uc.selectPet("owner", "missing"), /찾을 수/);
});
test("#12-S2 잘못된 반려동물과 날짜 저장 거부", async () => {
  const { useCases: uc } = setup();
  await assert.rejects(uc.savePet("a", { ...pet, name: " " }), /이름/);
  await assert.rejects(
    uc.savePet("a", { ...pet, birthday: "2026-02-30" }),
    /날짜/,
  );
  assert.equal((await uc.load("a")).pets.length, 0);
});
test("#12-S3 저장 실패 후 원본 보존 및 재시도", async () => {
  const setupResult = setup();
  const uc = setupResult.useCases;
  const p = await uc.savePet("a", pet);
  const record = await uc.diary.save("a", memory(p.id));
  setupResult.fail();
  await assert.rejects(
    uc.diary.save("a", memory(p.id, { body: "변경 시도" }), record.id),
    /storage full/,
  );
  assert.equal(
    (await uc.diary.list("a", p.id)).entries[0].body,
    "함께 걸은 하루",
  );
  await assert.rejects(uc.diary.remove("a", p.id, record.id), /storage full/);
  assert.equal((await uc.diary.list("a", p.id)).total, 1);
  setupResult.recover();
  await uc.diary.save("a", memory(p.id, { body: "재시도 성공" }), record.id);
  assert.equal((await uc.diary.list("a", p.id)).entries[0].body, "재시도 성공");
});
test("#12-S3 다른 반려동물의 기록 수정·삭제 및 빈 일기 거부", async () => {
  const { useCases: uc } = setup();
  const a = await uc.savePet("owner", pet);
  const b = await uc.savePet("owner", { ...pet, name: "다른 친구" });
  const record = await uc.diary.save("owner", memory(a.id));
  await assert.rejects(
    uc.diary.save("owner", memory(b.id), record.id),
    /찾을 수/,
  );
  await assert.rejects(uc.diary.remove("owner", b.id, record.id), /찾을 수/);
  await assert.rejects(
    uc.diary.save("owner", memory(a.id, { body: " ", photoUrl: "" })),
    /이야기/,
  );
  await assert.rejects(
    uc.diary.save("owner", memory(a.id, { body: "x".repeat(3001) })),
    /3,000/,
  );
  await assert.rejects(
    uc.diary.save("owner", memory("missing")),
    /찾을 수|먼저 선택/,
  );
  assert.equal((await uc.diary.list("owner", a.id)).total, 1);
});
test("#12-S4 날짜 필터·페이지 추가 조회·날짜 검증", async () => {
  const { useCases: uc } = setup();
  const p = await uc.savePet("a", pet);
  for (let i = 1; i <= 14; i++)
    await uc.diary.save(
      "a",
      memory(p.id, { recordedDate: `2026-11-${String(i).padStart(2, "0")}` }),
    );
  const first = await uc.diary.list("a", p.id, { limit: 9 });
  const second = await uc.diary.list("a", p.id, { limit: 9, offset: 9 });
  assert.equal(first.entries.length, 9);
  assert.equal(first.hasMore, true);
  assert.equal(second.entries.length, 5);
  assert.equal(second.hasMore, false);
  assert.equal(
    new Set([...first.entries, ...second.entries].map((record) => record.id))
      .size,
    14,
  );
  assert.equal(first.entries[0].recordedDate, "2026-11-14");
  assert.equal(
    (await uc.diary.list("a", p.id, { date: "2026-11-01" })).total,
    1,
  );
  assert.equal(
    (await uc.diary.list("a", p.id, { date: "2026-11-30" })).total,
    0,
  );
  await assert.rejects(
    uc.diary.list("a", p.id, { date: "2026-02-29" }),
    /日付|날짜/,
  );
  assert.doesNotThrow(() => validateDate("2024-02-29"));
  assert.throws(() => validateDate("not-a-date"), /날짜/);
});
test("#12-S5 손상된 브라우저 데이터는 덮어쓰지 않고 오류 표시", async () => {
  const values = new Map<string, string>();
  const storage = {
    getItem: (key: string) => values.get(key) ?? null,
    setItem: (key: string, value: string) => {
      values.set(key, value);
    },
  };
  Object.defineProperty(globalThis, "localStorage", {
    value: storage,
    configurable: true,
  });
  try {
    const repo = new BrowserWorkspaceRepository();
    values.set("pawmento.workspace.v1.a", "broken json");
    await assert.rejects(repo.load("a"), /읽거나/);
    assert.equal(values.get("pawmento.workspace.v1.a"), "broken json");
    values.set(
      "pawmento.workspace.v1.a",
      JSON.stringify({
        pets: [null],
        diaries: [],
        questionAnswers: [],
        questionPrompts: [],
        selectedPetId: "",
      }),
    );
    await assert.rejects(repo.load("a"), /읽거나/);
    await repo.save("b", {
      pets: [],
      diaries: [],
      questionAnswers: [],
      questionPrompts: [],
      selectedPetId: "",
    });
    assert.equal((await repo.load("b")).pets.length, 0);
  } finally {
    Reflect.deleteProperty(globalThis, "localStorage");
  }
});

test("#12-S7 모드 전환과 프로필 수정 후 모든 기록 보존", async () => {
  const { useCases: uc } = setup();
  const p = await uc.savePet("a", pet);
  const record = await uc.diary.save("a", memory(p.id));
  await uc.changeMode("a", p.id, "GARDEN");
  await uc.savePet("a", { ...pet, name: "보리보리" }, p.id);
  assert.equal((await uc.load("a")).pets[0].mode, "GARDEN");
  assert.equal((await uc.diary.list("a", p.id)).entries[0].id, record.id);
  await uc.changeMode("a", p.id, "ACTIVE");
  assert.equal((await uc.diary.list("a", p.id)).total, 1);
  await assert.rejects(uc.deletePet("a", p.id), /기록이 있는/);
});

test("#12-S7 반려동물마다 모드를 독립 저장하고 선택을 바꿔도 기록을 보존한다", async () => {
  const { useCases: uc } = setup();
  const a = await uc.savePet("owner", { ...pet, name: "보리" });
  const b = await uc.savePet("owner", { ...pet, name: "코코" });
  await uc.diary.save("owner", memory(a.id));
  await uc.diary.save("owner", memory(b.id));
  const before = (await uc.load("owner")).diaries;
  await uc.changeMode("owner", b.id, "GARDEN");
  await uc.selectPet("owner", a.id);
  let state = await uc.load("owner");
  assert.equal(state.pets.find((pet) => pet.id === a.id)!.mode, "ACTIVE");
  assert.equal(state.pets.find((pet) => pet.id === b.id)!.mode, "GARDEN");
  assert.equal(state.selectedPetId, a.id);
  await uc.changeMode("owner", a.id, "GARDEN");
  await uc.changeMode("owner", b.id, "ACTIVE");
  await uc.selectPet("owner", b.id);
  state = await uc.load("owner");
  assert.equal(state.pets.find((pet) => pet.id === a.id)!.mode, "GARDEN");
  assert.equal(state.pets.find((pet) => pet.id === b.id)!.mode, "ACTIVE");
  assert.equal(state.selectedPetId, b.id);
  assert.deepEqual(state.diaries, before);
});
