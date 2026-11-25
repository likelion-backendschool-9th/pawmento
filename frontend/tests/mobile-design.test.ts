import test from "node:test";
import assert from "node:assert/strict";
import {
  activeNavigation,
  appNavigation,
} from "../features/shell/utils/navigation.ts";
import {
  anotherMemory,
  gardenMemories,
} from "../features/pet/utils/gardenMemories.ts";
import { journalItems } from "../features/diary/utils/journalItems.ts";

const items = journalItems({
  diaries: [
    {
      id: "same-id",
      petId: "bori",
      type: "TEXT",
      recordedDate: "2026-11-24",
      createdAt: "2026-11-24T00:00:00Z",
      updatedAt: "2026-11-24T00:00:00Z",
      body: "일기",
      photoUrl: "",
    },
    {
      id: "other",
      petId: "coco",
      type: "TEXT",
      recordedDate: "2026-11-24",
      createdAt: "2026-11-24T00:00:00Z",
      updatedAt: "2026-11-24T00:00:00Z",
      body: "다른 아이의 일기",
      photoUrl: "",
    },
  ],
  questionAnswers: [
    {
      id: "same-id",
      petId: "bori",
      promptId: "p1",
      questionId: "q1",
      questionSnapshot: "어떤 하루였나요?",
      registeredAt: "2026-11-24T00:00:00Z",
      updatedAt: "2026-11-24T00:00:00Z",
      body: "답변",
      photoUrl: "",
    },
  ],
});

test("#233-S2 일기·달력·미디어와 질문 답변·회차는 서로 다른 하단 항목만 활성화한다", () => {
  for (const suffix of [
    "diary",
    "diary/new",
    "diary/a/edit",
    "calendar",
    "media",
    "records/old",
    "timeline",
  ])
    assert.equal(activeNavigation(`/pets/bori/${suffix}`, "bori"), "diary");
  for (const suffix of [
    "questions",
    "questions/prompts/p1",
    "questions/answers/a",
    "questions/answers/new",
  ])
    assert.equal(activeNavigation(`/pets/bori/${suffix}`, "bori"), "questions");
  assert.equal(activeNavigation("/pets/bori/diary-other", "bori"), null);
  assert.equal(activeNavigation("/pets/coco/diary", "bori"), null);
});
test("#233-S2 하단 5개 항목은 정보 지도로 끝나고 전체 메뉴·우체통 삽입으로 밀리지 않는다", () => {
  assert.deepEqual(
    appNavigation("bori").map((x) => x.label),
    ["홈", "다이어리", "오늘의 질문", "커뮤니티", "정보 지도"],
  );
  assert.equal(appNavigation()[1].href, "/pets");
  assert.equal(activeNavigation("/places"), "places");
  assert.equal(activeNavigation("/me"), null);
  assert.equal(activeNavigation("/pets/bori/garden", "bori"), "home");
});
test("#233-S6 나비는 선택한 아이의 데모 일기/답변을 모으되 종류별 동일 ID를 구분한다", () => {
  const candidates = gardenMemories("demo", "bori", items);
  assert.equal(candidates.length, 2);
  assert.equal(new Set(candidates.map((x) => x.key)).size, 2);
  assert.deepEqual(
    candidates.map((x) => x.href),
    ["/pets/bori/diary/same-id", "/pets/bori/questions/answers/same-id"],
  );
  assert.deepEqual(gardenMemories("http", "bori", items), []);
  assert.deepEqual(gardenMemories(null, "bori", items), []);
  assert.deepEqual(gardenMemories("demo", "missing", items), []);
});
test("#233-S6 다른 기억은 직전 항목을 반복하지 않고 단일·빈 후보를 처리한다", () => {
  const candidates = gardenMemories("demo", "bori", items);
  assert.equal(
    anotherMemory(candidates, candidates[0].key, () => 0)?.key,
    candidates[1].key,
  );
  assert.equal(
    anotherMemory([candidates[0]], candidates[0].key)?.key,
    candidates[0].key,
  );
  assert.equal(anotherMemory([], candidates[0].key), null);
  assert.equal(
    anotherMemory(candidates, null, () => 0.999)?.key,
    candidates[1].key,
  );
});
