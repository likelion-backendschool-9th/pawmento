import test from "node:test";
import assert from "node:assert/strict";
import { demoQuestionInputs } from "../src/app/demoQuestions.ts";
import type { QuestionAnswer, Pet } from "../src/workspace/domain/models.ts";
const pet: Pet = {
  id: "bori",
  name: "보리",
  species: "DOG",
  breed: "",
  birthday: "",
  photoUrl: "",
  mode: "ACTIVE",
};
test("#12-S1 데모 질문은 날짜·질문 식별자·답변을 가진 세 일기로 생성한다", () => {
  const inputs = demoQuestionInputs(pet, [], "2026-11-02");
  assert.deepEqual(
    inputs.map((input) => input.registeredAt.slice(0, 10)),
    ["2026-10-31", "2026-10-29", "2026-10-27"],
  );
  assert.ok(
    inputs.every(
      (input) =>
        input.questionId &&
        input.questionSnapshot.includes("보리") &&
        input.body &&
        input.photoUrl,
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
