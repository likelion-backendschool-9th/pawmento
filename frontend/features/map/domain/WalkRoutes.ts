import type { WalkRecord } from "./WalkRecord.ts";
import type { WalkStatus } from "./Walk.ts";
import type { MapPoint } from "./MapBounds.ts";

// 경로를 그리는 쪽(네이버 지도, 예시 지도)이 같은 표시를 하도록 상태별 표시를 한 곳에서 정한다.
// 끝났거나 진행 중인 산책은 출발점을, 중단된 산책은 마지막 점과 "중단" 표시를 그린다.
export function routeMarks(status: WalkStatus): { startDot: boolean; pausedTag: boolean } {
  const paused = status === "INTERRUPTED";
  return { startDot: !paused, pausedTag: paused };
}

export type RouteView = {
  walkId: string;
  status: WalkStatus;
  path: { x: number; y: number }[];
};

const MIN_SPAN_DEGREES = 0.0006; // 거의 한 곳에서 움직인 산책도 점 하나로 뭉개지지 않게 약 65m는 보여 준다
const FILL = 80; // 0~100 화면 좌표 중 길이 차지하는 크기(나머지는 여백)

// 좌표 묶음이 한 화면(0~100)에 들어오도록 옮기고 줄이는 함수를 만든다. 가로·세로 비율은 유지한다.
function projector(all: readonly MapPoint[]) {
  const latitudes = all.map((p) => p.latitude);
  const longitudes = all.map((p) => p.longitude);
  const midLat = (Math.min(...latitudes) + Math.max(...latitudes)) / 2;
  const midLng = (Math.min(...longitudes) + Math.max(...longitudes)) / 2;
  const cosine = Math.cos((midLat * Math.PI) / 180);
  const height = Math.max(...latitudes) - Math.min(...latitudes);
  const width = (Math.max(...longitudes) - Math.min(...longitudes)) * cosine;
  const scale = FILL / Math.max(height, width, MIN_SPAN_DEGREES);
  return (p: MapPoint) => ({
    x: 50 + (p.longitude - midLng) * cosine * scale,
    y: 50 - (p.latitude - midLat) * scale,
  });
}

// 걷는 중인 길 하나를 한 화면에 맞춘다. 출발점과 지금 위치가 항상 화면 안에 들어온다.
export function fitPoints(points: readonly MapPoint[]): { x: number; y: number }[] {
  if (points.length === 0) return [];
  return points.map(projector(points));
}

// 그날의 모든 산책이 한 화면(0~100)에 들어오도록 좌표를 옮기고 줄인다.
export function fitRoutes(walks: readonly WalkRecord[]): RouteView[] {
  const points = walks.flatMap((walk) => walk.points);
  if (points.length === 0) return [];
  const project = projector(points);
  return walks.map((walk) => ({
    walkId: walk.walkId,
    status: walk.status,
    path: walk.points.map(project),
  }));
}
