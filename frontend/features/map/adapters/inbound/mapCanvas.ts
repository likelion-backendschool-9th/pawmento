import type { MapBounds, MapPoint } from "@/features/map/domain/MapBounds";
import type { MapPin } from "@/features/map/domain/MapPin";
import type { PinMode } from "./pinMarkup";

// 지도 그림을 그리는 쪽(네이버 지도, 정적 지도)이 화면과 주고받는 약속이다.
export type MapCanvasProps = {
  center: MapPoint;
  pins: MapPin[];
  selectedId: string | null;
  mode: PinMode;
  onSelect: (diaryId: string | null) => void;
  onBoundsChange: (bounds: MapBounds) => void;
};

// 처음 보여 줄 위치(서울숲). 기억이 있는 곳으로 옮기는 기능은 서버에 "내 핀의 범위" 조회가 생긴 뒤에 더한다.
export const DEFAULT_MAP_CENTER: MapPoint = {
  latitude: 37.5445,
  longitude: 127.0374,
};
