import test from "node:test";
import assert from "node:assert/strict";
import { formatDate } from "../src/shared/date.ts";
import { createUnsavedChangesGuard } from "../src/shared/unsavedChanges.ts";

test("#12-S4 날짜 표시가 미국 서부와 한국에서 기본·상세 옵션 모두 한국 날짜를 유지한다", () => {
  const previous = process.env.TZ;
  try {
    for (const timeZone of [
      "America/Los_Angeles",
      "Pacific/Honolulu",
      "Asia/Seoul",
    ]) {
      process.env.TZ = timeZone;
      assert.equal(formatDate("2026-11-16"), "11월 16일 월요일");
      assert.equal(
        formatDate("2026-11-16", {
          year: "numeric",
          month: "long",
          day: "numeric",
        }),
        "2026년 11월 16일",
      );
      assert.equal(
        formatDate("2026-11-16", {
          day: "numeric",
          timeZone: "America/Los_Angeles",
        }),
        "16일",
      );
    }
  } finally {
    if (previous === undefined) delete process.env.TZ;
    else process.env.TZ = previous;
  }
});
test("#12-S3 미저장 폼의 이동 취소·승인과 여러 폼의 독립 해제를 처리한다", () => {
  let accept = false;
  let confirmations = 0;
  const guard = createUnsavedChangesGuard(() => {
    confirmations++;
    return accept;
  });
  assert.equal(guard.confirmNavigation(), true);
  assert.equal(confirmations, 0);
  const first = guard.register();
  const second = guard.register();
  assert.equal(guard.confirmNavigation(), false);
  first();
  assert.equal(guard.isDirty(), true);
  accept = true;
  assert.equal(guard.confirmNavigation(), true);
  second();
  assert.equal(guard.isDirty(), false);
  assert.equal(guard.confirmNavigation(), true);
  assert.equal(confirmations, 2);
});
