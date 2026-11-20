import type { WalkRecord } from "./WalkRecord.ts";
import type { WalkStatus } from "./Walk.ts";

export type RouteView = {
  walkId: string;
  status: WalkStatus;
  path: { x: number; y: number }[];
};

const MIN_SPAN_DEGREES = 0.0006; // 거의 한 곳에서 움직인 산책도 점 하나로 뭉개지지 않게 약 65m는 보여 준다
const FILL = 80; // 0~100 화면 좌표 중 길이 차지하는 크기(나머지는 여백)

// 그날의 모든 산책이 한 화면(0~100)에 들어오도록 좌표를 옮기고 줄인다. 가로·세로 비율은 유지한다.
export function fitRoutes(walks: readonly WalkRecord[]): RouteView[] {
  const points = walks.flatMap((walk) => walk.points);
  if (points.length === 0) return [];
  const latitudes = points.map((p) => p.latitude);
  const longitudes = points.map((p) => p.longitude);
  const midLat = (Math.min(...latitudes) + Math.max(...latitudes)) / 2;
  const midLng = (Math.min(...longitudes) + Math.max(...longitudes)) / 2;
  const cosine = Math.cos((midLat * Math.PI) / 180);
  const height = Math.max(...latitudes) - Math.min(...latitudes);
  const width = (Math.max(...longitudes) - Math.min(...longitudes)) * cosine;
  const scale = FILL / Math.max(height, width, MIN_SPAN_DEGREES);
  return walks.map((walk) => ({
    walkId: walk.walkId,
    status: walk.status,
    path: walk.points.map((p) => ({
      x: 50 + (p.longitude - midLng) * cosine * scale,
      y: 50 - (p.latitude - midLat) * scale,
    })),
  }));
}
