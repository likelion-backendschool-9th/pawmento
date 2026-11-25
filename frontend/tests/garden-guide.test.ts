import test from "node:test";
import assert from "node:assert/strict";
import {
  createGardenGuide,
  GARDEN_GUIDE_KEY,
} from "../features/pet/utils/gardenGuide.ts";

test("#233-S8 정원 튜토리얼은 최초 진입 때만 열리고 닫은 뒤 재방문에는 열리지 않는다", () => {
  const values = new Map<string, string>();
  const storage = {
    getItem: (key: string) => values.get(key) ?? null,
    setItem: (key: string, value: string) => {
      values.set(key, value);
    },
  };
  const first = createGardenGuide(() => storage);
  assert.equal(first.shouldShow(), true);
  first.finish();
  assert.equal(first.shouldShow(), false);
  assert.equal(createGardenGuide(() => storage).shouldShow(), false);
  assert.deepEqual([...values], [[GARDEN_GUIDE_KEY, "seen"]]);
});
test("#233-S8 저장이 차단돼도 튜토리얼 닫기를 유지하며 기록·인증 데이터는 저장하지 않는다", () => {
  const guide = createGardenGuide(() => {
    throw Error("storage blocked");
  });
  assert.equal(guide.shouldShow(), true);
  guide.finish();
  assert.equal(guide.shouldShow(), false);
});
