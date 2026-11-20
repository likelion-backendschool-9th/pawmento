import type { MapBounds } from "../domain/MapBounds.ts";
import type { MapPin } from "../domain/MapPin.ts";

export type MapPinList = {
  pins: MapPin[];
  // 영역 안의 핀이 서버의 한 번에 주는 최대 개수를 넘어 잘렸다
  truncated: boolean;
  // 실제 기록이 아닌 화면 확인용 예시 핀이다(데모 계정)
  sample: boolean;
};

export interface MapPinRepository {
  list(petId: string, bounds: MapBounds): Promise<MapPinList>;
}
