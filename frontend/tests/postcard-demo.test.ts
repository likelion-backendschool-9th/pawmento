import test from "node:test";
import assert from "node:assert/strict";
import {
  drawJournalItem,
  sceneForJournalItem,
  postcardScenes,
} from "../src/life/application/postcardScenes.ts";
import { DemoPostcardGenerator } from "../mocks/handlers/DemoPostcardGenerator.ts";
import type { JournalItem } from "../src/workspace/domain/models.ts";
const record: JournalItem = {
  id: "park",
  petId: "bori",
  kind: "QUESTION_ANSWER",
  type: "QUESTION_ANSWER",
  promptId: "p",
  questionId: "q",
  questionSnapshot: "질문",
  registeredAt: "2026-11-15T00:00:00Z",
  question: "오늘 가장 신났던 순간은?",
  body: "공원에서 공을 쫓아 달렸어요.",
  photoUrl: "reference:running",
  recordedDate: "2026-11-15",
  createdAt: "2026-11-15T00:00:00Z",
  updatedAt: "2026-11-15T00:00:00Z",
};
test("#12-S9 랜덤 뽑기는 빈 기록을 처리하고 전체 후보 중 정확히 한 기억을 반환", () => {
  const records = [
    record,
    { ...record, id: "nap" },
    { ...record, id: "beach" },
  ];
  assert.equal(drawJournalItem([]), undefined);
  assert.equal(drawJournalItem(records, () => 0)?.id, "park");
  assert.equal(drawJournalItem(records, () => 0.5)?.id, "nap");
  assert.equal(drawJournalItem(records, () => 0.999)?.id, "beach");
  assert.equal(records.length, 3);
});
test("#12-S9 기록의 질문과 답변에 따라 공원·바닷가·낮잠 장면을 선택", () => {
  assert.equal(sceneForJournalItem(record), "PARK");
  assert.equal(
    sceneForJournalItem({
      ...record,
      body: "노을이 지는 바닷가에서 산책했어요.",
    }),
    "BEACH",
  );
  assert.equal(
    sceneForJournalItem({ ...record, body: "인형을 안고 낮잠에 들었어요." }),
    "NAP",
  );
  assert.equal(
    sceneForJournalItem({
      ...record,
      question: "해변에서 어떤 시간을 보냈나요?",
      body: "함께 즐겁게 뛰었어요.",
    }),
    "BEACH",
  );
});
test("#12-S9 데모 결과는 원본 사진 대신 별도 그림을 사용하고 기록·질문·참고 사진을 연결", async () => {
  const before = structuredClone(record);
  const generator = new DemoPostcardGenerator();
  const result = await generator.generate(
    [record],
    "보리",
    "data:image/png;base64,profile-photo",
  );
  assert.equal(result.imageUrl, postcardScenes.PARK.imageUrl);
  assert.notEqual(result.imageUrl, record.photoUrl);
  assert.equal(
    result.demoArtwork.referencePhotoUrl,
    "data:image/png;base64,profile-photo",
  );
  assert.ok(result.demoArtwork.prompt.includes(record.body));
  assert.ok(result.demoArtwork.prompt.includes(record.question!));
  assert.ok(result.demoArtwork.prompt.includes("보리"));
  assert.ok(result.text.includes(record.question!));
  assert.deepEqual(record, before);
  await assert.rejects(generator.generate([], "보리"), /한 장/);
  await assert.rejects(generator.generate([record, record], "보리"), /한 장/);
});
