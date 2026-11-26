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
// 주소의 date처럼 바깥에서 받은 날짜도 달력이 그릴 수 있는 범위(0001-01-01~9999-12-31)의 실제 날짜만 받는다
export function isCalendarDate(value: string) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value) || !isCalendarMonth(value.slice(0, 7))) return false;
  const day = Number(value.slice(8));
  return day >= 1 && day <= calendarMonth(value.slice(0, 7)).days;
}
/**
 * 브라우저가 월 선택 화면을 보여 주는가.
 * 데스크톱 Firefox는 type="month"를 text로 바꾸고, macOS Safari는 month로 두지만 선택 화면이 없다.
 * iPhone·iPad의 WebKit(터치 지점이 있음)은 선택 화면이 있다.
 */
export function hasMonthPicker({ inputType, vendor, maxTouchPoints }: { inputType: string; vendor: string; maxTouchPoints: number }) {
  if (inputType !== "month") return false;
  return !(vendor.startsWith("Apple") && maxTouchPoints === 0);
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
