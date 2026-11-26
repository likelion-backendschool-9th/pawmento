import type { MapBounds, MapPoint } from "@/features/map/types/MapBounds";
import type { Place } from "@/features/map/types/Place";

// 지도를 이 지점으로 옮겨 달라는 요청(검색 결과를 눌렀을 때, #195-S11). token이 바뀔 때마다 한 번씩 옮긴다(같은 지점을 다시 눌러도 옮긴다).
export type PlaceFocus = { point: MapPoint; token: number };

// 정보 지도의 그림을 그리는 쪽(네이버 지도, 정적 지도)이 화면과 주고받는 약속이다.
export type PlaceCanvasProps = {
  center: MapPoint;
  places: Place[];
  selectedId: string | null;
  onSelect: (placeId: string | null) => void;
  onBoundsChange: (bounds: MapBounds) => void;
  // 카드가 지도 아래를 덮은 높이(px). 덮고 있지 않으면 0이다. 선택한 마커가 카드 뒤에 가려지지 않게 지도를 옮길 때 쓴다.
  coveredBottom?: () => number;
  // 지도를 옮겨 달라는 요청. 지도의 중심은 처음에만 center를 쓰고, 그 뒤의 이동은 이 요청으로만 한다
  focus?: PlaceFocus | null;
};

// 카드가 지도 아래쪽을 덮을 때 선택한 마커(위에서 y픽셀)가 보이는 영역 안에 오도록 마커를 위로 옮길 픽셀을 구한다.
// 위로 옮기면 양수, 아래로 옮기면 음수이고, 이미 보이면 0이다. 위쪽 topInset은 종류 칩이 차지하는 자리다.
export function markerShiftToVisible({
  y,
  height,
  covered,
  topInset = 64,
  margin = 36,
}: {
  y: number;
  height: number;
  covered: number;
  topInset?: number;
  margin?: number;
}): number {
  if (covered <= 0) return 0;
  const visibleBottom = height - covered - margin;
  if (visibleBottom <= topInset) return 0;
  if (y >= topInset && y <= visibleBottom) return 0;
  // 보이는 영역의 가운데쯤에 둔다
  const target = topInset + (visibleBottom - topInset) / 2;
  return y - target;
}
