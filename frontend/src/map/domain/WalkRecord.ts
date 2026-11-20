import { formatDistance, pathDistance, type WalkPoint, type WalkStatus } from "./Walk.ts";

// 날짜별로 다시 보는 산책 하나. 서버의 `GET /maps/walks` 항목과 같은 모양이다.
// 끝내지 않은 산책(중단된 산책 포함)의 endedAt은 null이다.
export type WalkRecord = {
  walkId: string;
  startedAt: string;
  endedAt: string | null;
  status: WalkStatus;
  points: WalkPoint[];
};

const SEOUL_OFFSET_MS = 9 * 60 * 60 * 1000;
const DATE_PATTERN = /^(\d{4})-(\d{2})-(\d{2})$/;
const WEEKDAYS = ["일", "월", "화", "수", "목", "금", "토"];

// 서버는 `YYYY-MM-DD`만 받는다. 달력에 없는 날(2026-02-30 등)도 거른다.
export function isValidDate(value: string): boolean {
  const match = DATE_PATTERN.exec(value);
  if (!match) return false;
  const [year, month, day] = [Number(match[1]), Number(match[2]), Number(match[3])];
  const date = new Date(Date.UTC(year, month - 1, day));
  return (
    date.getUTCFullYear() === year &&
    date.getUTCMonth() === month - 1 &&
    date.getUTCDate() === day
  );
}

const pad = (value: number) => String(value).padStart(2, "0");

// 시각이 속한 날(Asia/Seoul). 서버가 산책의 날짜를 정하는 기준과 같다.
export function seoulDate(isoOrMs: string | number): string {
  const ms = typeof isoOrMs === "number" ? isoOrMs : Date.parse(isoOrMs);
  const shifted = new Date(ms + SEOUL_OFFSET_MS);
  return `${shifted.getUTCFullYear()}-${pad(shifted.getUTCMonth() + 1)}-${pad(shifted.getUTCDate())}`;
}

export function shiftDate(date: string, days: number): string {
  const [year, month, day] = date.split("-").map(Number);
  const shifted = new Date(Date.UTC(year, month - 1, day + days));
  return `${shifted.getUTCFullYear()}-${pad(shifted.getUTCMonth() + 1)}-${pad(shifted.getUTCDate())}`;
}

// 끝 날짜를 포함해 과거로 count일. 오래된 날이 앞에 온다.
export function datesEndingAt(last: string, count: number): string[] {
  return Array.from({ length: count }, (_, index) => shiftDate(last, index - count + 1));
}

export function weekdayLabel(date: string): string {
  const [year, month, day] = date.split("-").map(Number);
  return WEEKDAYS[new Date(Date.UTC(year, month - 1, day)).getUTCDay()];
}

export function dayOfMonth(date: string): number {
  return Number(date.slice(8, 10));
}

export function formatMonthDay(date: string): string {
  return `${Number(date.slice(5, 7))}월 ${dayOfMonth(date)}일`;
}

export function formatYearMonth(date: string): string {
  return `${date.slice(0, 4)}년 ${Number(date.slice(5, 7))}월`;
}

// "오전 10:00" 처럼 한국 시각으로 적는다.
export function formatClock(iso: string): string {
  const shifted = new Date(Date.parse(iso) + SEOUL_OFFSET_MS);
  const hours = shifted.getUTCHours();
  const period = hours < 12 ? "오전" : "오후";
  const twelve = hours % 12 === 0 ? 12 : hours % 12;
  return `${period} ${twelve}:${pad(shifted.getUTCMinutes())}`;
}

// 산책이 끝난 시각. 중단된 산책은 마지막 좌표를 받은 시각이다.
export function walkEndedAt(walk: WalkRecord): string {
  return (
    walk.endedAt ??
    walk.points[walk.points.length - 1]?.recordedAt ??
    walk.startedAt
  );
}

export function walkMinutes(walk: WalkRecord): number {
  const span = Date.parse(walkEndedAt(walk)) - Date.parse(walk.startedAt);
  return Number.isFinite(span) && span > 0 ? Math.round(span / 60_000) : 0;
}

export function formatMinutes(minutes: number): string {
  if (minutes < 60) return `${minutes}분`;
  const rest = minutes % 60;
  return rest === 0 ? `${Math.floor(minutes / 60)}시간` : `${Math.floor(minutes / 60)}시간 ${rest}분`;
}

export function walkDistanceText(walk: WalkRecord): string {
  const { value, unit } = formatDistance(pathDistance(walk.points));
  return `${value}${unit}`;
}

// "00:20" 처럼 한국 시각의 시:분만 적는다.
export function formatHourMinute(iso: string): string {
  const shifted = new Date(Date.parse(iso) + SEOUL_OFFSET_MS);
  return `${pad(shifted.getUTCHours())}:${pad(shifted.getUTCMinutes())}`;
}

// 시작과 끝 시각을 "오전 10:00 – 10:42"로 적는다. 끝이 다른 오전·오후이면 끝에도 붙인다.
export function formatTimeRange(walk: WalkRecord): string {
  const start = formatClock(walk.startedAt);
  const end = formatClock(walkEndedAt(walk));
  return start.slice(0, 2) === end.slice(0, 2)
    ? `${start} – ${end.slice(3)}`
    : `${start} – ${end}`;
}
