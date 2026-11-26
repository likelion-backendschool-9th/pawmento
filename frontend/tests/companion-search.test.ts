import test from "node:test";
import assert from "node:assert/strict";
import { searchCompanionKinds } from "../features/pet/utils/companionSearch.ts";

test("#233-S9 동물 자동완성은 전체·한글 부분 입력·영문 종류·검색 결과 없음을 구분한다", () => {
  assert.equal(searchCompanionKinds("").length, 8);
  assert.deepEqual(searchCompanionKinds("  토 ").map((kind) => kind.id), ["RABBIT"]);
  assert.deepEqual(searchCompanionKinds("BIRD").map((kind) => kind.label), ["새"]);
  assert.equal(searchCompanionKinds("그외")[0].species, "OTHER");
  assert.deepEqual(searchCompanionKinds("없는종류"), []);
});
