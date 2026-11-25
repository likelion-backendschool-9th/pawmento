import test from "node:test";
import assert from "node:assert/strict";
import { demoQuestionInputs } from "../mocks/fixtures/demoQuestions.ts";
import type { Pet } from "../features/pet/types/Pet.ts";
import type { QuestionAnswer } from "../features/question/types/QuestionAnswer.ts";
const pet: Pet = {
  id: "bori",
  name: "보리",
  species: "DOG",
  breed: "",
  birthday: "",
  photoUrl: "",
  mode: "ACTIVE",
};
test("#12-S1 (고침 #233: 가족별 답변 데모) 세 질문에 나·엄마·아빠의 답변을 각각 준비한다", () => {
  const inputs = demoQuestionInputs(pet, [], "2026-11-02");
  assert.deepEqual(
    inputs
      .filter((input) => input.authorId === "demo")
      .map((input) => input.registeredAt.slice(0, 10)),
    ["2026-10-31", "2026-10-29", "2026-10-27"],
  );
  assert.equal(inputs.length, 9);
  assert.equal(new Set(inputs.map((input) => input.questionId)).size, 3);
  assert.equal(new Set(inputs.map((input) => input.authorId)).size, 3);
  assert.ok(
    inputs.every(
      (input) =>
        input.questionId &&
        input.questionSnapshot.includes("보리") &&
        input.body &&
        input.authorName &&
        input.authorId,
    ),
  );
});
test("#12-S1 기존 답변과 데모 샘플을 덮어쓰거나 재생성하지 않는다", () => {
  const samples = demoQuestionInputs(pet, [], "2026-11-02");
  const records = samples.map((input, index) => ({
    ...input,
    id: `${index}`,
    promptId: "p",
    updatedAt: "",
  })) as QuestionAnswer[];
  const before = structuredClone(records);
  assert.deepEqual(demoQuestionInputs(pet, records, "2026-11-03"), []);
  assert.deepEqual(
    demoQuestionInputs(
      pet,
      records.map((record) => ({ ...record, questionId: "user-question" })),
      "2026-11-02",
    ),
    samples,
  );
  assert.deepEqual(records, before);
});
