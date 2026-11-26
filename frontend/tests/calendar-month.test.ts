import test from "node:test";
import assert from "node:assert/strict";
import { calendarMonth, isCalendarMonth, shiftCalendarMonth, swipeMonthDirection } from "../features/diary/utils/calendarMonth.ts";

test("#233-S4 월 이동은 연도 경계와 선택 가능한 연월 범위를 유지한다", () => {
  assert.equal(shiftCalendarMonth("2026-12", 1), "2027-01");
  assert.equal(shiftCalendarMonth("2026-01", -1), "2025-12");
  assert.equal(shiftCalendarMonth("0099-12", 1), "0100-01");
  assert.equal(shiftCalendarMonth("0001-01", -1), "0001-01");
  assert.equal(shiftCalendarMonth("9999-12", 1), "9999-12");
});
test("#233-S4 연월 선택은 윤년과 두 자리 연도에서도 날짜 수와 첫 요일이 정확하다", () => {
  assert.deepEqual(calendarMonth("2026-11"), { year: 2026, month: 11, first: 0, days: 30 });
  assert.equal(calendarMonth("2024-02").days, 29);
  assert.equal(calendarMonth("2026-02").days, 28);
  assert.equal(calendarMonth("0001-01").first, 1);
  assert.throws(() => calendarMonth("2026-13"));
});
test("#233-S4 가로 스와이프만 월을 바꾸며 짧은 이동과 세로 스크롤은 유지한다", () => {
  assert.equal(swipeMonthDirection(-100, 12), 1);
  assert.equal(swipeMonthDirection(100, 12), -1);
  assert.equal(swipeMonthDirection(20, 0), 0);
  assert.equal(swipeMonthDirection(50, 100), 0);
  assert.equal(swipeMonthDirection(-50, -40), 0);
});
test("#233-S4 연월 입력과 월 계산은 같은 범위(0001-01~9999-12)만 받는다", () => {
  for (const value of ["0001-01", "2025-02", "9999-12"]) assert.equal(isCalendarMonth(value), true, value);
  for (const value of ["0000-01", "0000-12", "2025-2", "2025-00", "2025-13", "202502", "", "2025-02-01"])
    assert.equal(isCalendarMonth(value), false, value);
  assert.throws(() => calendarMonth("0000-01"));
  assert.throws(() => shiftCalendarMonth("0000-12", 1));
});
