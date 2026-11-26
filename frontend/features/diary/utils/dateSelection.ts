// 날짜 선택 UI에 쓰는 달 계산. 로컬 시간대와 0~99년의 Date 보정을 피한다.
export function monthCells(year: number, month: number): (string | null)[] {
  const first = new Date(0);
  first.setUTCFullYear(year, month - 1, 1);
  const last = new Date(0);
  last.setUTCFullYear(year, month, 0);
  return [
    ...Array<null>(first.getUTCDay()).fill(null),
    ...Array.from({ length: last.getUTCDate() }, (_, index) =>
      `${String(year).padStart(4, "0")}-${String(month).padStart(2, "0")}-${String(index + 1).padStart(2, "0")}`),
  ];
}

export function moveMonth(year: number, month: number, delta: number): [number, number] {
  const index = Math.min(9999 * 12 - 1, Math.max(0, (year - 1) * 12 + month - 1 + delta));
  return [Math.floor(index / 12) + 1, index % 12 + 1];
}

export function isCalendarDate(value: string): boolean {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const year = Number(value.slice(0, 4));
  const month = Number(value.slice(5, 7));
  return year >= 1 && year <= 9999 && month >= 1 && month <= 12 && monthCells(year, month).includes(value);
}

// 세로 스크롤과 짧은 탭은 월 이동으로 처리하지 않는다.
export function swipeMonthDirection(dx: number, dy: number): -1 | 0 | 1 {
  if (Math.abs(dx) < 48 || Math.abs(dx) <= Math.abs(dy) * 1.2) return 0;
  return dx < 0 ? 1 : -1;
}
