import test from "node:test";
import assert from "node:assert/strict";
import { monthCells, moveMonth, isCalendarDate, swipeMonthDirection } from "../features/diary/utils/dateSelection.ts";
import { searchDemoLocations } from "../mocks/fixtures/demoLocationSearch.ts";
import { placeNameAfterPick } from "../features/map/utils/pickedLocation.ts";

test("#233-S9 날짜 선택은 윤년·월 경계·1~9999년을 보존한다", () => {
  assert.equal(isCalendarDate("2026-02-29"), false);
  assert.equal(isCalendarDate("0000-01-01"), false);
  assert.equal(isCalendarDate("2000-02-29"), true);
  assert.equal(isCalendarDate("not-a-date"), false);
  assert.equal(monthCells(2000, 2).filter(Boolean).length, 29);
  assert.equal(monthCells(1900, 2).filter(Boolean).length, 28);
  assert.equal(monthCells(2026, 11)[0], "2026-11-01");
  assert.equal(monthCells(1, 1).find(Boolean), "0001-01-01");
  assert.deepEqual(moveMonth(2026, 12, 1), [2027, 1]);
  assert.deepEqual(moveMonth(2026, 1, -1), [2025, 12]);
  assert.deepEqual(moveMonth(1, 1, -1), [1, 1]);
  assert.deepEqual(moveMonth(9999, 12, 1), [9999, 12]);
});

test("#233-S9 데모 위치 검색은 빈 검색을 제외하고 주소·장소를 찾는다", () => {
  assert.equal(searchDemoLocations("  ").length, 0);
  assert.equal(searchDemoLocations("서울 숲").length, 2);
  assert.equal(searchDemoLocations("성동구 뚝섬로 273")[0].id, "seoul-forest");
  assert.equal(searchDemoLocations("없는 주소").length, 0);
});

test("#233-S9 달력은 가로 스와이프만 월 이동으로 처리한다", () => {
  assert.equal(swipeMonthDirection(-70, 4), 1);
  assert.equal(swipeMonthDirection(70, 4), -1);
  assert.equal(swipeMonthDirection(-10, 0), 0);
  assert.equal(swipeMonthDirection(50, 90), 0);
  assert.equal(swipeMonthDirection(50, 50), 0);
});

test("#233-S9 위치 검색 결과를 골라도 사용자가 직접 쓴 장소 이름은 덮지 않는다", () => {
  assert.equal(placeNameAfterPick("", null, "서울숲"), "서울숲", "비어 있으면 채운다");
  assert.equal(placeNameAfterPick("  ", null, "서울숲"), "서울숲", "공백뿐이면 채운다");
  assert.equal(placeNameAfterPick("서울숲", "서울숲", "성수동 카페거리"), "성수동 카페거리", "결과로 채운 이름은 다음 결과로 바꾼다");
  assert.equal(placeNameAfterPick("우리 산책 벤치", null, "서울숲"), "우리 산책 벤치", "직접 쓴 이름은 남긴다");
  assert.equal(placeNameAfterPick("서울숲 벤치", "서울숲", "성수동 카페거리"), "서울숲 벤치", "결과 이름을 고쳐 쓴 뒤에는 남긴다");
});
