import type { MapPoint } from "./MapBounds.ts";

// 산책 하나. 상태는 서버가 마지막 좌표를 받은 시각으로 계산한다(5분 동안 좌표가 없으면 INTERRUPTED).
export type WalkStatus = "IN_PROGRESS" | "ENDED" | "INTERRUPTED";

export type Walk = {
  walkId: string;
  petId: string;
  startedAt: string;
};

export type WalkEnd = {
  status: WalkStatus;
  endedAt: string | null;
};

// 산책 경로의 점 하나. recordedAt은 UTC ISO 시각이다(서버는 같은 시각의 점을 한 번만 쌓는다).
export type WalkPoint = MapPoint & { recordedAt: string };

// 서버의 한도(docs/map/walk-route.md)
// 서버의 반려동물 id는 양의 정수다. 브라우저에서 만든 반려동물(UUID)은 서버에 물을 수 없어 데모 산책으로 기록한다.
const SERVER_PET_ID = /^[1-9][0-9]*$/;
export const isServerPetId = (petId: string): boolean => SERVER_PET_ID.test(petId);

// 데모 산책은 서버가 없어서 브라우저 메모리에만 둔다(새로고침하면 사라진다).
export const DEMO_WALK_PREFIX = "demo-walk-";
export const isDemoWalkId = (walkId: string): boolean => walkId.startsWith(DEMO_WALK_PREFIX);

// 끝낸 결과. complete가 false면 서버에 저장하지 못한 좌표가 남아 있다.
export type WalkOutcome = WalkEnd & { complete: boolean };

export const MAX_POINTS_PER_REQUEST = 100;
export const MAX_POINTS_PER_WALK = 5000;

// 좌표를 모으는 간격과 한 번에 보내는 개수. 15초 간격으로 4개(1분)씩 보낸다.
export const SAMPLE_INTERVAL_MS = 15_000;
export const FLUSH_EVERY_POINTS = 4;

const EARTH_RADIUS_METERS = 6_371_000;
const toRadians = (degrees: number) => (degrees * Math.PI) / 180;

// 두 점 사이의 거리(하버사인, 미터)
export function distanceBetween(from: MapPoint, to: MapPoint): number {
  const dLat = toRadians(to.latitude - from.latitude);
  const dLng = toRadians(to.longitude - from.longitude);
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRadians(from.latitude)) *
      Math.cos(toRadians(to.latitude)) *
      Math.sin(dLng / 2) ** 2;
  return 2 * EARTH_RADIUS_METERS * Math.asin(Math.min(1, Math.sqrt(a)));
}

// 걸은 거리(점들을 이은 선의 길이, 미터)
export function pathDistance(points: readonly MapPoint[]): number {
  let total = 0;
  for (let i = 1; i < points.length; i += 1) {
    total += distanceBetween(points[i - 1], points[i]);
  }
  return total;
}

export function elapsedSeconds(startedAt: string, nowMs: number): number {
  const started = Date.parse(startedAt);
  if (!Number.isFinite(started)) return 0;
  return Math.max(0, Math.floor((nowMs - started) / 1000));
}

const two = (value: number) => String(value).padStart(2, "0");

// 00:24:18
export function formatDuration(totalSeconds: number): string {
  const seconds = Math.max(0, Math.floor(totalSeconds));
  const hours = Math.floor(seconds / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  return `${two(hours)}:${two(minutes)}:${two(seconds % 60)}`;
}

// 1.24 km (1km 미만은 m)
export function formatDistance(meters: number): { value: string; unit: "m" | "km" } {
  if (meters < 1000) return { value: String(Math.round(meters)), unit: "m" };
  return { value: (meters / 1000).toFixed(2), unit: "km" };
}

export function isValidWalkPoint(point: WalkPoint): boolean {
  return (
    Number.isFinite(point.latitude) &&
    Number.isFinite(point.longitude) &&
    Math.abs(point.latitude) <= 90 &&
    Math.abs(point.longitude) <= 180 &&
    Number.isFinite(Date.parse(point.recordedAt))
  );
}
