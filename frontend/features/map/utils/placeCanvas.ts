import type { MapBounds, MapPoint } from "@/features/map/types/MapBounds";
import type { Place } from "@/features/map/types/Place";

// 정보 지도의 그림을 그리는 쪽(네이버 지도, 정적 지도)이 화면과 주고받는 약속이다.
export type PlaceCanvasProps = {
  center: MapPoint;
  places: Place[];
  selectedId: string | null;
  onSelect: (placeId: string | null) => void;
  onBoundsChange: (bounds: MapBounds) => void;
};
