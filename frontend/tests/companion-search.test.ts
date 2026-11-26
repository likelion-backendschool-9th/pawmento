import test from "node:test";
import assert from "node:assert/strict";
import { companionOptions, searchCompanionKinds } from "../features/pet/utils/companionSearch.ts";

test("#233-S9 동물 자동완성은 전체·한글 부분 입력·영문 종류·검색 결과 없음을 구분한다", () => {
  assert.equal(searchCompanionKinds("").length, 8);
  assert.deepEqual(searchCompanionKinds("  토 ").map((kind) => kind.id), ["RABBIT"]);
  assert.deepEqual(searchCompanionKinds("BIRD").map((kind) => kind.label), ["새"]);
  assert.equal(searchCompanionKinds("그외")[0].species, "OTHER");
  assert.deepEqual(searchCompanionKinds("없는종류"), []);
});

test("#233-S9 맞는 종류가 없으면 안내한 '그 외'를 바로 고를 수 있게 보여 준다", () => {
  assert.deepEqual(companionOptions("수달"), { options: [{ id: "OTHER", label: "그 외", species: "OTHER" }], noMatch: true });
  const found = companionOptions("고양");
  assert.equal(found.noMatch, false);
  assert.deepEqual(found.options.map((kind) => kind.id), ["CAT"]);
});
