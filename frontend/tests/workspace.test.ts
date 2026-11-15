import test from "node:test";
import assert from "node:assert/strict";
import { createWorkspaceUseCases } from "../src/workspace/application/useCases.ts";
import { validateDate } from "../src/workspace/domain/models.ts";
import type {
  MemoryInput,
  Workspace,
  PetInput,
} from "../src/workspace/domain/models.ts";
import { createAuthUseCases } from "../src/auth/application/auth.ts";
import { DemoAuthRepository } from "../src/auth/adapters/outbound/DemoAuthRepository.ts";
import { BrowserWorkspaceRepository } from "../src/workspace/adapters/outbound/BrowserWorkspaceRepository.ts";
function setup() {
  const saved = new Map<string, Workspace>();
  let fail = false;
  let counter = 0;
  const repository = {
    load: async (id: string) =>
      structuredClone(
        saved.get(id) ?? { pets: [], records: [], selectedPetId: "" },
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
  overrides: Partial<MemoryInput> = {},
): MemoryInput => ({
  petId,
  type: "TEXT",
  recordedDate: "2026-11-15",
  body: "함께 걸은 하루",
  question: "오늘 가장 기억에 남은 순간은?",
  photoUrl: "",
  ...overrides,
});
test("#12-S1 가입·중복 이메일·잘못된 비밀번호·로그아웃과 탭 격리", async () => {
  const repository = new DemoAuthRepository();
  const auth = createAuthUseCases(repository);
  const input = {
    email: "  TEST@example.com ",
    password: "local-demo-pass",
    confirmation: "local-demo-pass",
    nickname: "  보호자  ",
  };
  const account = await auth.signUp(input);
  assert.equal(account.email, "test@example.com");
  assert.equal(account.nickname, "보호자");
  await assert.rejects(auth.signUp(input), /이미 가입/);
  await auth.signOut();
  assert.equal(await auth.current(), null);
  await assert.rejects(
    auth.signIn({ email: account.email, password: "incorrect" }),
    /확인/,
  );
  assert.equal(
    (await auth.signIn({ email: account.email, password: input.password })).id,
    account.id,
  );
  assert.equal(
    await createAuthUseCases(new DemoAuthRepository()).current(),
    null,
  );
  await auth.update("새 이름");
  await auth.signOut();
  assert.equal(
    (await auth.signIn({ email: account.email, password: input.password }))
      .nickname,
    "새 이름",
  );
});
test("#12-S1 가입 필수 값과 비밀번호 확인 검증", () => {
  const auth = createAuthUseCases(new DemoAuthRepository());
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
  await uc.saveMemory("owner", memory(a.id));
  await uc.saveMemory("owner", memory(b.id, { body: "나비의 하루" }));
  await uc.selectPet("owner", a.id);
  assert.equal((await uc.load("owner")).selectedPetId, a.id);
  assert.equal(
    (await uc.list("owner", a.id)).records[0].body,
    "함께 걸은 하루",
  );
  assert.equal((await uc.list("owner", b.id)).records[0].body, "나비의 하루");
  assert.equal((await uc.load("someone")).pets.length, 0);
  await uc.savePet("owner", { ...pet, name: "보리보리" }, a.id);
  assert.equal((await uc.load("owner")).pets[0].name, "보리보리");
  assert.equal((await uc.list("owner", a.id)).total, 1);
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
test("#12-S3 질문 일기 저장·중복 방지·수정·삭제 후 목록 반영", async () => {
  const { useCases: uc } = setup();
  const p = await uc.savePet("a", pet);
  const input = memory(p.id, { type: "DIARY", question: "오늘은 어땠나요?" });
  const record = await uc.saveMemory("a", input);
  await assert.rejects(uc.saveMemory("a", input), /이미 있어요/);
  const edited = await uc.saveMemory(
    "a",
    { ...input, body: "오늘도 즐거웠다" },
    record.id,
  );
  assert.equal(edited.createdAt, record.createdAt);
  assert.equal((await uc.list("a", p.id)).records[0].body, "오늘도 즐거웠다");
  await uc.deleteMemory("a", p.id, record.id);
  assert.equal((await uc.list("a", p.id)).total, 0);
});
test("#12-S3 동시 일기 저장에서도 같은 날짜의 중복 방지", async () => {
  const { useCases: uc } = setup();
  const p = await uc.savePet("a", pet);
  const input = memory(p.id, { type: "DIARY" });
  const results = await Promise.allSettled([
    uc.saveMemory("a", input),
    uc.saveMemory("a", input),
  ]);
  assert.equal(
    results.filter((result) => result.status === "fulfilled").length,
    1,
  );
  assert.equal((await uc.list("a", p.id)).total, 1);
});
test("#12-S3 저장 실패 후 원본 보존 및 재시도", async () => {
  const setupResult = setup();
  const uc = setupResult.useCases;
  const p = await uc.savePet("a", pet);
  const record = await uc.saveMemory("a", memory(p.id));
  setupResult.fail();
  await assert.rejects(
    uc.saveMemory("a", memory(p.id, { body: "변경 시도" }), record.id),
    /storage full/,
  );
  assert.equal((await uc.list("a", p.id)).records[0].body, "함께 걸은 하루");
  await assert.rejects(uc.deleteMemory("a", p.id, record.id), /storage full/);
  assert.equal((await uc.list("a", p.id)).total, 1);
  setupResult.recover();
  await uc.saveMemory("a", memory(p.id, { body: "재시도 성공" }), record.id);
  assert.equal((await uc.list("a", p.id)).records[0].body, "재시도 성공");
});
test("#12-S3 다른 반려동물의 기록 수정·삭제 및 빈 일기 거부", async () => {
  const { useCases: uc } = setup();
  const a = await uc.savePet("owner", pet);
  const b = await uc.savePet("owner", { ...pet, name: "다른 친구" });
  const record = await uc.saveMemory("owner", memory(a.id));
  await assert.rejects(
    uc.saveMemory("owner", memory(b.id), record.id),
    /찾을 수/,
  );
  await assert.rejects(uc.deleteMemory("owner", b.id, record.id), /찾을 수/);
  await assert.rejects(
    uc.saveMemory(
      "owner",
      memory(a.id, { type: "DIARY", body: " ", photoUrl: "photo" }),
    ),
    /이야기를/,
  );
  await assert.rejects(
    uc.saveMemory("owner", memory(a.id, { body: "x".repeat(3001) })),
    /3,000/,
  );
  await assert.rejects(uc.saveMemory("owner", memory("missing")), /먼저 선택/);
  assert.equal((await uc.list("owner", a.id)).total, 1);
});
test("#12-S4 날짜 필터·페이지 추가 조회·날짜 검증", async () => {
  const { useCases: uc } = setup();
  const p = await uc.savePet("a", pet);
  for (let i = 1; i <= 14; i++)
    await uc.saveMemory(
      "a",
      memory(p.id, { recordedDate: `2026-11-${String(i).padStart(2, "0")}` }),
    );
  const first = await uc.list("a", p.id, { limit: 9 });
  const second = await uc.list("a", p.id, { limit: 9, offset: 9 });
  assert.equal(first.records.length, 9);
  assert.equal(first.hasMore, true);
  assert.equal(second.records.length, 5);
  assert.equal(second.hasMore, false);
  assert.equal(
    new Set([...first.records, ...second.records].map((record) => record.id))
      .size,
    14,
  );
  assert.equal(first.records[0].recordedDate, "2026-11-14");
  assert.equal((await uc.list("a", p.id, { date: "2026-11-01" })).total, 1);
  assert.equal((await uc.list("a", p.id, { date: "2026-11-30" })).total, 0);
  await assert.rejects(uc.list("a", p.id, { date: "2026-02-29" }), /日付|날짜/);
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
    await assert.rejects(repo.load("a"), /읽을 수/);
    assert.equal(values.get("pawmento.workspace.v1.a"), "broken json");
    values.set(
      "pawmento.workspace.v1.a",
      JSON.stringify({ pets: [null], records: [], selectedPetId: "" }),
    );
    await assert.rejects(repo.load("a"), /읽을 수/);
    await repo.save("b", { pets: [], records: [], selectedPetId: "" });
    assert.equal((await repo.load("b")).pets.length, 0);
  } finally {
    Reflect.deleteProperty(globalThis, "localStorage");
  }
});

test("#12-S3 오늘의 질문은 포트로 조회하고 날짜·반려동물별 식별자를 분리", async () => {
  const { createGetTodayQuestion } =
    await import("../src/workspace/application/questions.ts");
  const { MockQuestionRepository } =
    await import("../src/workspace/adapters/outbound/MockQuestionRepository.ts");
  const query = createGetTodayQuestion(new MockQuestionRepository());
  const first = await query("bori", "2026-11-15");
  assert.equal((await query("bori", "2026-11-15")).text, first.text);
  assert.notEqual((await query("bori", "2026-11-16")).text, first.text);
  assert.notEqual((await query("nabi", "2026-11-15")).id, first.id);
  assert.equal(typeof (await query("bori", "1960-01-01")).text, "string");
  await assert.rejects(query("", "2026-11-15"), /먼저 선택/);
  await assert.rejects(query("bori", "2026-02-30"), /날짜/);
});

test("#12-S6 질문 스냅샷과 답변을 날짜별 조회·수정 후에도 함께 보존", async () => {
  const { useCases: uc } = setup();
  const p = await uc.savePet("owner", pet);
  const first = await uc.saveMemory(
    "owner",
    memory(p.id, {
      type: "DIARY",
      question: "오늘의 원래 질문",
      questionId: "question-original",
      body: "그날의 답변",
    }),
  );
  const onDate = await uc.list("owner", p.id, { date: "2026-11-15" });
  assert.equal(onDate.records[0].question, "오늘의 원래 질문");
  assert.equal(onDate.records[0].body, "그날의 답변");
  await uc.saveMemory(
    "owner",
    memory(p.id, {
      type: "DIARY",
      question: "새로 들어온 다른 질문",
      questionId: "different-id",
      body: "답변만 수정",
    }),
    first.id,
  );
  const edited = (await uc.list("owner", p.id)).records[0];
  assert.equal(edited.question, "오늘의 원래 질문");
  assert.equal(edited.questionId, "question-original");
  assert.equal(edited.body, "답변만 수정");
  await assert.rejects(
    uc.saveMemory(
      "owner",
      memory(p.id, { type: "DIARY", recordedDate: "2026-11-16", question: "" }),
    ),
    /질문과 답변/,
  );
});
test("#12-S7 모드 전환과 프로필 수정 후 모든 기록 보존", async () => {
  const { useCases: uc } = setup();
  const p = await uc.savePet("a", pet);
  const record = await uc.saveMemory("a", memory(p.id));
  await uc.changeMode("a", p.id, "GARDEN");
  await uc.savePet("a", { ...pet, name: "보리보리" }, p.id);
  assert.equal((await uc.load("a")).pets[0].mode, "GARDEN");
  assert.equal((await uc.list("a", p.id)).records[0].id, record.id);
  await uc.changeMode("a", p.id, "ACTIVE");
  assert.equal((await uc.list("a", p.id)).total, 1);
  await assert.rejects(uc.deletePet("a", p.id), /기록이 있는/);
});
