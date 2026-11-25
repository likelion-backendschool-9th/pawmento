import test from "node:test";
import assert from "node:assert/strict";
import { createWorkspaceUseCases } from "../mocks/handlers/workspaceUseCases.ts";
import { emptyWorkspace } from "../features/shell/types/workspace.ts";
import { demoQuestionInputs } from "../mocks/fixtures/demoQuestions.ts";
import { demoDiarySamples } from "../mocks/fixtures/demoDiaries.ts";
import { myAnswer } from "../features/question/utils/myAnswer.ts";
import { readWorkspaceSnapshot } from "../mocks/handlers/workspaceStorage.ts";
import { gardenMemories } from "../features/pet/utils/gardenMemories.ts";
import { journalItems } from "../features/diary/utils/journalItems.ts";

function setup() {
  let state = emptyWorkspace();
  let id = 0;
  const uc = createWorkspaceUseCases(
    {
      load: async () => structuredClone(state),
      save: async (_account, next) => {
        state = structuredClone(next);
      },
    },
    { id: () => `id-${++id}`, now: () => "2026-11-25T00:00:00Z" },
  );
  return {
    uc,
    current: () => structuredClone(state),
    replace: (next: typeof state) => {
      state = structuredClone(next);
    },
  };
}
const petInput = {
  name: "보리",
  species: "DOG" as const,
  breed: "",
  birthday: "",
  photoUrl: "",
};

test("#233-S4 데모 일기는 제목 필수·공백 정리·코드 포인트 100자·재조회 보존을 지킨다", async () => {
  const { uc, current } = setup();
  const pet = await uc.savePet("demo", petInput);
  const draft = {
    petId: pet.id,
    recordedDate: "2026-11-24",
    body: "본문",
    photoUrl: "",
  };
  for (const title of [
    undefined,
    "",
    "   ",
    "가".repeat(101),
    "제목\n",
    "앞\uD800뒤",
  ])
    await assert.rejects(uc.diary.save("demo", { ...draft, title }), /제목/);
  const created = await uc.diary.save("demo", {
    ...draft,
    title: "  공 하나로 행복했던 오후  ",
  });
  assert.equal(created.title, "공 하나로 행복했던 오후");
  assert.equal(
    readWorkspaceSnapshot({ schemaVersion: 2, state: current() }).diaries[0]
      .title,
    created.title,
  );
  await uc.diary.save(
    "demo",
    { ...draft, title: "🐾".repeat(100) },
    created.id,
  );
  assert.equal(current().diaries[0].title, "🐾".repeat(100));
});

test("#233-S4 (고침 #233: 자동 보강 제거) 데모 재조회·질문 샘플 보강에도 기존 무제목 일기를 그대로 보존한다", async () => {
  const { uc, current, replace } = setup();
  const pet = await uc.savePet("demo", petInput);
  const sample = demoDiarySamples[0];
  const original = {
    id: "sample",
    petId: pet.id,
    authorId: "demo",
    type: "PHOTO" as const,
    recordedDate: "2026-11-24",
    createdAt: "2026-11-24T00:00:00Z",
    updatedAt: "2026-11-24T00:00:00Z",
    body: sample.body,
    photoUrl: sample.photoUrl,
  };
  const state = current();
  state.diaries = [
    original,
    { ...original, id: "user", body: "직접 작성한 일기" },
    { ...original, id: "titled", title: "내가 수정한 제목" },
  ];
  replace(state);
  const before = structuredClone(state.diaries);
  for (const day of ["2026-11-25", "2026-11-26"]) {
    const loaded = await uc.load("demo");
    await uc.seedDemoAnswers(
      "demo",
      demoQuestionInputs(pet, loaded.questionAnswers, day, loaded.questionPrompts),
    );
    assert.deepEqual((await uc.load("demo")).diaries, before);
    assert.deepEqual(current().diaries, before);
  }
  await assert.rejects(
    uc.diary.save(
      "demo",
      { petId: pet.id, recordedDate: "2026-11-24", body: "수정", photoUrl: "" },
      "user",
    ),
    /제목/,
  );
});

test("#233-S5 세 질문에 가족별 답변을 모으고 재진입·부분 보강에도 회차와 기존 답변을 복제하지 않는다", async () => {
  const { uc, current } = setup();
  const pet = await uc.savePet("demo", petInput);
  const inputs = demoQuestionInputs(pet, [], "2026-11-25");
  await uc.seedDemoAnswers(
    "demo",
    inputs.filter((input) => input.authorId === "demo"),
  );
  const before = current();
  await uc.seedDemoAnswers(
    "demo",
    demoQuestionInputs(pet, before.questionAnswers, "2026-11-26"),
  );
  assert.equal(current().questionPrompts.length, 3);
  assert.equal(current().questionAnswers.length, 9);
  for (const prompt of current().questionPrompts) {
    const family = current().questionAnswers.filter(
      (a) => a.promptId === prompt.id,
    );
    assert.equal(family.length, 3);
    assert.equal(new Set(family.map((a) => a.authorId)).size, 3);
    assert.equal(myAnswer(family, "demo")?.authorId, "demo");
    assert.equal(
      myAnswer(
        family.filter((a) => a.authorId !== "demo"),
        "demo",
      ),
      undefined,
    );
    assert.equal(myAnswer(family, undefined), undefined);
  }
  for (const answer of before.questionAnswers)
    assert.deepEqual(
      current().questionAnswers.find((a) => a.id === answer.id),
      answer,
    );
  for (const prompt of before.questionPrompts)
    assert.deepEqual(
      current().questionPrompts.find((p) => p.id === prompt.id),
      prompt,
    );
  const full = current();
  await uc.seedDemoAnswers(
    "demo",
    demoQuestionInputs(pet, full.questionAnswers, "2026-11-27"),
  );
  assert.deepEqual(current(), full);
  // Removing my seeded answer must not make it reappear on the next demo visit.
  const removed = full.questionAnswers.find((a) => a.authorId === "demo")!;
  await uc.questions.remove("demo", pet.id, removed.id);
  const afterRemoval = current();
  await uc.seedDemoAnswers(
    "demo",
    demoQuestionInputs(
      pet,
      afterRemoval.questionAnswers,
      "2026-11-28",
      afterRemoval.questionPrompts,
    ),
  );
  assert.deepEqual(current(), afterRemoval);
  const butterflies = gardenMemories(
    "demo",
    pet.id,
    journalItems(full),
    "demo",
  );
  assert.ok(
    butterflies.every(
      (memory) =>
        memory.item.kind === "DIARY" || memory.item.authorId === "demo",
    ),
  );
  await assert.rejects(uc.seedDemoAnswers("not-demo", inputs), /데모/);
});
