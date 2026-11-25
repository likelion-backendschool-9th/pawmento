import test from "node:test";
import assert from "node:assert/strict";
import { questionNumber } from "../features/question/utils/questionNumber.ts";
import type { QuestionPrompt } from "../features/question/types/QuestionAnswer.ts";

const prompts: QuestionPrompt[] = [
  { id: "latest", petId: "bori", mode: "GARDEN", questionId: "q", questionSnapshot: "기억", createdAt: "2026-11-25T12:00:00+09:00" },
  { id: "other", petId: "coco", mode: "ACTIVE", questionId: "q", questionSnapshot: "다른 아이", createdAt: "2020-01-01T00:00:00Z" },
  { id: "first", petId: "bori", mode: "ACTIVE", questionId: "q", questionSnapshot: "일상", createdAt: "2026-11-23T00:00:00Z", completedAt: "2026-11-23T01:00:00Z" },
  { id: "second", petId: "bori", mode: "ACTIVE", questionId: "q", questionSnapshot: "다음 회차", createdAt: "2026-11-24T00:00:00Z", completedAt: "2026-11-24T01:00:00Z" },
];
test("#233-S5 질문 번호는 선택한 아이의 배정 순서이며 같은 질문의 재배정·모드 변경에도 회차별로 구분한다", () => {
  assert.equal(questionNumber("bori", "first", prompts), 1);
  assert.equal(questionNumber("bori", "second", prompts), 2);
  assert.equal(questionNumber("bori", "latest", prompts), 3);
  assert.equal(questionNumber("coco", "other", prompts), 1);
  assert.equal(questionNumber("bori", "other", prompts), undefined);
  assert.equal(questionNumber("bori", "missing", prompts), undefined);
  assert.equal(questionNumber("bori", "latest", prompts.map(p => ({ ...p, completedAt: undefined }))), 3);
});
test("#233-S5 배정 시간이 같으면 저장된 순서를 유지하며 번호 계산은 기존 배정을 수정하지 않는다", () => {
  const sameTime = prompts.filter(p => p.petId === "bori").map(p => ({ ...p, createdAt: "2026-11-25T00:00:00Z" }));
  const before = structuredClone(sameTime);
  assert.equal(questionNumber("bori", "latest", sameTime), 1);
  assert.equal(questionNumber("bori", "first", sameTime), 2);
  assert.deepEqual(sameTime, before);
});
