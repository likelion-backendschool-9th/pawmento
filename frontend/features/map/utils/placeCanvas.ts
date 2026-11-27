import type { MapBounds, MapPoint } from "@/features/map/types/MapBounds";
import type { Place } from "@/features/map/types/Place";
import { EARTH_RADIUS_METERS, toDegrees } from "./geo.ts";

// 지도를 이 지점으로 옮겨 달라는 요청(검색 결과를 눌렀을 때, #195-S11). token이 바뀔 때마다 한 번씩 옮긴다(같은 지점을 다시 눌러도 옮긴다).
// fitRadiusMeters가 있으면 그 반경이 보이게 확대 수준도 맞춘다(내 위치로 옮길 때, #226-S2). 없으면 확대 수준은 그대로다.
export type PlaceFocus = { point: MapPoint; token: number; fitRadiusMeters?: number };

// 지도에 점과 정확도 원으로 보이는 내 위치(#226-S2). accuracy는 미터다.
export type MyLocationMark = { point: MapPoint; accuracy: number };

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
  // 내 위치. 있으면 점과 정확도 원을 그린다
  me?: MyLocationMark | null;
};

// 예시 지도(정적)가 보여 주는 기본 영역의 크기(도). 사용자가 움직일 수 없고 정해진 영역만 보여 준다.
export const STATIC_SPAN = { latitude: 0.0075, longitude: 0.014 };

// 예시 지도에서 반경(미터)의 원이 보이도록 영역을 넓히는 배율(1 이상). 위쪽 검색창·종류 칩이 덮는 자리와 여백을 감안해 원의 지름보다 35% 넓게 보인다(#226-S2)
export function staticSpanScale(radiusMeters: number): number {
  const diameterDegrees = 2 * toDegrees(radiusMeters / EARTH_RADIUS_METERS);
  return Math.max(1, (diameterDegrees * 1.35) / STATIC_SPAN.latitude);
}

// 내 위치로 옮길 때 네이버 지도의 fitBounds에 넘기는 여백(px). 위·아래를 같게 해서 내 위치 점이 지도의 한가운데에 놓이게 한다.
// 목록 시트(NearbySheet)가 점이 한가운데에 있다는 것을 전제로 높이를 정하기 때문이다(예시 지도도 점이 한가운데다).
// 위쪽의 검색창·종류 칩이 덮는 만큼은 위·아래 모두 비워 두고(최대 120px), 지도가 낮으면 그만큼 줄인다.
export function fitMargin(stageHeight: number): { top: number; right: number; bottom: number; left: number } {
  const vertical = Math.max(20, Math.min(120, (stageHeight - 200) / 2));
  return { top: vertical, right: 20, bottom: vertical, left: 20 };
}

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
