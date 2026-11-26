// 달력이 다루는 연월은 0001-01부터 9999-12까지다(월 입력의 min·max, 월 이동 범위와 같다).
export function isCalendarMonth(value: string) {
  return /^\d{4}-(0[1-9]|1[0-2])$/.test(value) && value >= "0001-01";
}
export function calendarMonth(value: string) {
  if (!isCalendarMonth(value)) throw Error("올바른 연월을 선택해 주세요.");
  const date = new Date(`${value}-01T12:00:00Z`);
  const last = new Date(date);
  last.setUTCMonth(last.getUTCMonth() + 1);
  last.setUTCDate(0);
  return { year: date.getUTCFullYear(), month: date.getUTCMonth() + 1, first: date.getUTCDay(), days: last.getUTCDate() };
}
export function shiftCalendarMonth(value: string, delta: number) {
  const { year, month } = calendarMonth(value);
  const index = Math.max(12, Math.min(9999 * 12 + 11, year * 12 + month - 1 + delta));
  return `${String(Math.floor(index / 12)).padStart(4, "0")}-${String(index % 12 + 1).padStart(2, "0")}`;
}
export function swipeMonthDirection(dx: number, dy: number): -1 | 0 | 1 {
  if (Math.abs(dx) < 48 || Math.abs(dx) <= Math.abs(dy) * 1.25) return 0;
  return dx < 0 ? 1 : -1;
}
