import test from "node:test";
import assert from "node:assert/strict";
import { petAge } from "../features/pet/utils/petAge.ts";

test("#250-S2 홈의 나이 표시는 생일 기준으로 생일 전후의 살·개월을 계산한다", () => {
  assert.equal(petAge("2021-04-12", "2026-11-25"), "5살 7개월");
  assert.equal(petAge("2021-04-12", "2026-04-11"), "4살 11개월");
  assert.equal(petAge("2021-04-12", "2026-04-12"), "5살");
  assert.equal(petAge("2026-11-25", "2026-11-25"), "1개월 미만");
  assert.equal(petAge("2026-10-25", "2026-11-25"), "1개월");
});
test("#250-S2 월말·윤년 생일과 미등록·잘못된 생일에도 음수 나이가 표시되지 않는다", () => {
  assert.equal(petAge("2026-01-31", "2026-02-28"), "1개월");
  assert.equal(petAge("2024-02-29", "2025-02-28"), "1살");
  assert.equal(petAge("", "2026-11-25"), null);
  assert.equal(petAge("2026-02-30", "2026-11-25"), null);
  assert.equal(petAge("2026-11-26", "2026-11-25"), null);
});
