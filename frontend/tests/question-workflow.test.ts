import test from "node:test";
import assert from "node:assert/strict";
import { createWorkspaceUseCases } from "../src/workspace/application/useCases.ts";
import {
  emptyWorkspace,
  type Workspace,
} from "../src/workspace/domain/models.ts";
import {
  createQuestionAnswerUseCases,
  type QuestionCollection,
} from "../src/question/application/useCases.ts";
import type { JournalStore } from "../src/shared/application/JournalStore.ts";
function setup() {
  let state: Workspace = emptyWorkspace(),
    n = 0,
    now = "2026-11-15T14:59:00Z";
  const uc = createWorkspaceUseCases(
    {
      load: async () => structuredClone(state),
      save: async (_, s) => {
        state = structuredClone(s);
      },
    },
    { id: () => `id-${++n}`, now: () => now },
    {
      list: async (mode) => [
        { id: `${mode}-1`, text: "{name}와 함께한 순간은?" },
        { id: `${mode}-2`, text: "{name}에게 전할 말은?" },
      ],
    },
  );
  return {
    uc,
    time: (value: string) => {
      now = value;
    },
  };
}
const petInput = {
  name: "보리",
  species: "DOG" as const,
  breed: "코기",
  birthday: "",
  photoUrl: "",
};
test("#12-S3 (고침 #32) 질문은 날짜가 지나도 유지되고 중복 답변은 작성자·질문 단위로 막는다", async () => {
  const { uc, time } = setup();
  const pet = await uc.savePet("owner", petInput);
  const prompt = await uc.questions.current("owner", pet.id);
  time("2026-11-17T01:00:00Z");
  assert.deepEqual(await uc.questions.current("owner", pet.id), prompt);
  const input = {
    petId: pet.id,
    promptId: prompt.id,
    body: "함께 뛰었어요",
    photoUrl: "",
  };
  const results = await Promise.allSettled([
    uc.questions.save("owner", input),
    uc.questions.save("owner", input),
  ]);
  assert.equal(results.filter((r) => r.status === "fulfilled").length, 1);
  const answer = (await uc.questions.list("owner", pet.id))[0];
  assert.equal(answer.registeredAt, "2026-11-17T01:00:00Z");
  const next = await uc.questions.current("owner", pet.id);
  assert.notEqual(next.id, prompt.id);
  time("2026-11-18T01:00:00Z");
  const edited = await uc.questions.save(
    "owner",
    { ...input, body: "답변 수정" },
    answer.id,
  );
  assert.equal(edited.registeredAt, answer.registeredAt);
  assert.equal(edited.questionSnapshot, prompt.questionSnapshot);
  await uc.questions.remove("owner", pet.id, answer.id);
  assert.equal((await uc.questions.current("owner", pet.id)).id, next.id);
});
test("#12-S6 (고침 #32) 일기·질문 저장과 날짜 조회를 분리하고 모드별 질문을 보존한다", async () => {
  const { uc } = setup();
  const pet = await uc.savePet("owner", petInput);
  const active = await uc.questions.current("owner", pet.id);
  await uc.diary.save("owner", {
    petId: pet.id,
    recordedDate: "2026-11-14",
    body: "자유 일기",
    photoUrl: "",
  });
  await uc.questions.save("owner", {
    petId: pet.id,
    promptId: active.id,
    body: "질문 답변",
    photoUrl: "",
  });
  assert.equal((await uc.diary.list("owner", pet.id)).total, 1);
  assert.equal(
    (await uc.questions.list("owner", pet.id, "2026-11-15")).length,
    1,
  );
  assert.equal(
    (await uc.questions.list("owner", pet.id, "2026-11-14")).length,
    0,
  );
  const pending = await uc.questions.current("owner", pet.id);
  await uc.changeMode("owner", pet.id, "GARDEN");
  const garden = await uc.questions.current("owner", pet.id);
  assert.equal(garden.mode, "GARDEN");
  await assert.rejects(
    uc.questions.save("owner", {
      petId: pet.id,
      promptId: pending.id,
      body: "오래된 화면",
      photoUrl: "",
    }),
    /모드/,
  );
  await uc.changeMode("owner", pet.id, "ACTIVE");
  assert.equal((await uc.questions.current("owner", pet.id)).id, pending.id);
});
test("#12-S10 (고침 #32) 가족은 공통 질문에 각자 답하고 다른 작성자의 답변을 수정하지 못한다", async () => {
  let n = 0;
  let data = {
    pets: [
      {
        id: "pet",
        name: "보리",
        mode: "ACTIVE" as const,
        accessRole: "EDITOR" as const,
      },
    ],
    collection: { answers: [], prompts: [] } as QuestionCollection,
  };
  const store: JournalStore<QuestionCollection> = {
    read: async () => structuredClone(data),
    mutate: async (_, fn) => {
      const copy = structuredClone(data);
      const result = fn(copy);
      data = copy;
      return result;
    },
  };
  const uc = createQuestionAnswerUseCases(
    store,
    {
      list: async () => [
        { id: "one", text: "기억은?" },
        { id: "two", text: "또 다른 기억은?" },
      ],
    },
    { id: () => `${++n}`, now: () => "2026-11-15T00:00:00Z" },
  );
  const prompt = await uc.current("alice", "pet");
  const input = {
    petId: "pet",
    promptId: prompt.id,
    body: "내 답변",
    photoUrl: "",
  };
  const alice = await uc.save("alice", input);
  const next = await uc.current("bob", "pet");
  assert.notEqual(next.id, prompt.id);
  await uc.save("bob", input);
  assert.equal((await uc.list("alice", "pet")).length, 2);
  await assert.rejects(uc.save("bob", input, alice.id), /작성자/);
  await assert.rejects(uc.remove("bob", "pet", alice.id), /작성자/);
  assert.equal((await uc.current("alice", "pet")).id, next.id);
});
