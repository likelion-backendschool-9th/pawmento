import type { MapPoint } from "@/map/domain/MapBounds";

// 걷는 길을 그리는 쪽(네이버 지도, 예시 지도)이 화면과 주고받는 약속이다.
export type WalkCanvasProps = {
  points: readonly MapPoint[];
  current: MapPoint | null;
};
