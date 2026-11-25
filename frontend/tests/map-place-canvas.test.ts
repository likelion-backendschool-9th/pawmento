import test from "node:test";
import assert from "node:assert/strict";
import { markerShiftToVisible } from "../features/map/utils/placeCanvas.ts";

// 휴대폰에서 장소 카드가 지도 아래를 덮는다. 선택한 마커가 그 뒤나 칩 뒤에 가려지지 않게 옮길 거리를 구한다(#172-S17).
const base = { height: 700, covered: 340 };

test("#172-S17 카드가 지도를 덮지 않으면(PC) 지도를 옮기지 않는다", () => {
  assert.equal(markerShiftToVisible({ y: 650, height: 700, covered: 0 }), 0);
});

test("#172-S17 마커가 카드 위의 보이는 영역 안에 있으면 지도를 옮기지 않는다", () => {
  // 보이는 영역은 칩(64) 아래부터 카드 위쪽에서 여백(36)을 뺀 324까지다
  assert.equal(markerShiftToVisible({ ...base, y: 64 }), 0);
  assert.equal(markerShiftToVisible({ ...base, y: 200 }), 0);
  assert.equal(markerShiftToVisible({ ...base, y: 324 }), 0);
});

test("#172-S17 마커가 카드 뒤에 있으면 보이는 영역의 가운데로 올린다(양수 = 위로)", () => {
  // 가운데는 64 + (324 - 64) / 2 = 194
  assert.equal(markerShiftToVisible({ ...base, y: 500 }), 500 - 194);
  assert.equal(markerShiftToVisible({ ...base, y: 325 }), 325 - 194);
});

test("#172-S17 마커가 위쪽 칩에 가려지면 아래로 내린다(음수)", () => {
  assert.equal(markerShiftToVisible({ ...base, y: 20 }), 20 - 194);
});

test("#172-S17 카드가 너무 커서 보이는 영역이 없으면 옮기지 않는다(어디로 옮겨도 가려진다)", () => {
  assert.equal(markerShiftToVisible({ y: 300, height: 400, covered: 340 }), 0);
});
