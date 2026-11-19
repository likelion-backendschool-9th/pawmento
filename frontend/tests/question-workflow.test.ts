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
function setup(poolSize = 2) {
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
        ...Array.from({ length: Math.max(0, poolSize - 2) }, (_, i) => ({
          id: `${mode}-${i + 3}`,
          text: `추가 질문 ${i + 3}`,
        })),
      ],
    },
  );
  return {
    uc,
    poolSize: (value: number) => {
      poolSize = value;
    },
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

test("#12-S3 (고침 #32) 활성 풀의 세 번째 이후 질문과 새 질문도 순환 배정한다", async () => {
  const env = setup(4);
  const { uc } = env;
  const pet = await uc.savePet("owner", petInput);
  const ids = [];
  for (let i = 0; i < 8; i++) {
    const p = await uc.questions.current("owner", pet.id);
    ids.push(p.questionId);
    await uc.questions.save("owner", {
      petId: pet.id,
      promptId: p.id,
      body: "답변",
      photoUrl: "",
    });
  }
  assert.deepEqual(ids, [
    "ACTIVE-1",
    "ACTIVE-2",
    "ACTIVE-3",
    "ACTIVE-4",
    "ACTIVE-1",
    "ACTIVE-2",
    "ACTIVE-3",
    "ACTIVE-4",
  ]);
  env.poolSize(5);
  assert.equal(
    (await uc.questions.current("owner", pet.id)).questionId,
    "ACTIVE-5",
  );
});
test("#12-S3 (고침 #32) 사진·음성이 있어도 공백 답변은 저장과 공통 진행을 변경하지 않는다", async () => {
  const { uc } = setup();
  const pet = await uc.savePet("owner", petInput);
  const p = await uc.questions.current("owner", pet.id);
  const before = await uc.load("owner");
  for (const media of [
    { photoUrl: "photo", attachments: [] },
    {
      photoUrl: "",
      attachments: [
        {
          id: "voice",
          name: "목소리",
          url: "audio",
          type: "VOICE" as const,
          mimeType: "audio/webm",
        },
      ],
    },
  ]) {
    await assert.rejects(
      uc.questions.save("owner", {
        petId: pet.id,
        promptId: p.id,
        body: " \n\t ",
        ...media,
      }),
      /답변 내용/,
    );
    assert.deepEqual(await uc.load("owner"), before);
  }
  const a = await uc.questions.save("owner", {
    petId: pet.id,
    promptId: p.id,
    body: "정상 답변",
    photoUrl: "photo",
  });
  const saved = await uc.load("owner");
  await assert.rejects(
    uc.questions.save(
      "owner",
      { petId: pet.id, promptId: p.id, body: " ", photoUrl: "photo" },
      a.id,
    ),
    /답변 내용/,
  );
  assert.deepEqual(await uc.load("owner"), saved);
});
test("#12-S10 (고침 #32) 마지막 답변을 지워도 완료 배정 이력에서 가족과 본인이 재답변한다", async () => {
  const { questionHistory } =
    await import("../src/question/application/history.ts");
  const { uc } = setup();
  const pet = await uc.savePet("alice", petInput);
  const p = await uc.questions.current("alice", pet.id);
  const input = {
    petId: pet.id,
    promptId: p.id,
    body: "첫 답변",
    photoUrl: "",
  };
  const first = await uc.questions.save("alice", input);
  const next = await uc.questions.current("alice", pet.id);
  await uc.questions.remove("alice", pet.id, first.id);
  let state = await uc.load("alice");
  const empty = questionHistory(
    pet.id,
    state.questionPrompts,
    state.questionAnswers,
  );
  assert.equal(empty.length, 1);
  assert.equal(empty[0].prompt.id, p.id);
  assert.deepEqual(empty[0].answers, []);
  await uc.questions.save("bob", input);
  await uc.questions.save("alice", input);
  state = await uc.load("alice");
  assert.deepEqual(
    questionHistory(pet.id, state.questionPrompts, state.questionAnswers)[0]
      .answers.map((a) => a.authorId)
      .sort(),
    ["alice", "bob"],
  );
  assert.equal((await uc.questions.current("alice", pet.id)).id, next.id);
});
test("#12-S4 (고침 #32) 같은 날짜의 혼합 데이터에서 페이지 ID·답변 제외·조회 불변을 보존한다", async () => {
  const { uc } = setup();
  const pet = await uc.savePet("owner", petInput);
  const p = await uc.questions.current("owner", pet.id);
  const answer = await uc.questions.save("owner", {
    petId: pet.id,
    promptId: p.id,
    body: "질문 답변",
    photoUrl: "",
  });
  const a = await uc.diary.save("owner", {
    petId: pet.id,
    recordedDate: "2026-11-15",
    body: "일기 A",
    photoUrl: "",
  });
  const b = await uc.diary.save("owner", {
    petId: pet.id,
    recordedDate: "2026-11-14",
    body: "일기 B",
    photoUrl: "",
  });
  const before = await uc.load("owner");
  const first = await uc.diary.list("owner", pet.id, { limit: 1 });
  const second = await uc.diary.list("owner", pet.id, { limit: 1, offset: 1 });
  assert.deepEqual(
    first.entries.map((d) => d.id),
    [a.id],
  );
  assert.equal(first.total, 2);
  assert.equal(first.hasMore, true);
  assert.deepEqual(
    second.entries.map((d) => d.id),
    [b.id],
  );
  assert.equal(second.hasMore, false);
  assert.deepEqual(
    (await uc.diary.list("owner", pet.id, { date: "2026-11-15" })).entries.map(
      (d) => d.id,
    ),
    [a.id],
  );
  assert.deepEqual(
    (await uc.questions.list("owner", pet.id, "2026-11-15")).map((a) => a.id),
    [answer.id],
  );
  assert.deepEqual(await uc.load("owner"), before);
});
