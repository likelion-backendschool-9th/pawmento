import type { MapPoint } from "@/map/domain/MapBounds";
import { DEFAULT_MAP_CENTER } from "./mapCanvas";

// 걷는 길을 그리는 쪽(네이버 지도, 예시 지도)이 화면과 주고받는 약속이다.
export type WalkCanvasProps = {
  points: readonly MapPoint[];
  current: MapPoint | null;
};

const VIEW_SPAN_DEGREES = 0.006; // 화면 높이가 담는 위도 범위(약 650m)

// 지금 위치를 가운데(50,50)에 두고 길의 점들을 0~100 화면 좌표로 바꾼다.
export function pathToView(
  points: readonly MapPoint[],
  current: MapPoint | null,
): { x: number; y: number }[] {
  const center = current ?? points[points.length - 1] ?? DEFAULT_MAP_CENTER;
  const scale = 100 / VIEW_SPAN_DEGREES;
  const cosine = Math.cos((center.latitude * Math.PI) / 180);
  return points.map((point) => ({
    x: 50 + (point.longitude - center.longitude) * cosine * scale,
    y: 50 - (point.latitude - center.latitude) * scale,
  }));
}
