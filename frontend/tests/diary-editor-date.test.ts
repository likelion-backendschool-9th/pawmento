import test from "node:test";
import assert from "node:assert/strict";
import { monthCells, moveMonth, isCalendarDate } from "../features/diary/utils/dateSelection.ts";
import { searchDemoLocations } from "../mocks/fixtures/demoLocationSearch.ts";

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
