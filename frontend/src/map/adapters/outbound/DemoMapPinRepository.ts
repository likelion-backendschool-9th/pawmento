import { containsPoint, type MapBounds } from "../../domain/MapBounds.ts";
import type { MapPin } from "../../domain/MapPin.ts";
import type { MapPinList, MapPinRepository } from "../../application/ports.ts";

// 데모 계정은 서버 기록이 없어서 화면을 볼 수 있게 서울숲 근처의 예시 핀을 보여 준다.

const SAMPLE_PINS: MapPin[] = [
  { diaryId: "demo-1", latitude: 37.5462, longitude: 127.0352, placeName: "서울숲 반려견 놀이터" },
  { diaryId: "demo-2", latitude: 37.5451, longitude: 127.0381, placeName: "서울숲 은행나무길" },
  { diaryId: "demo-3", latitude: 37.5438, longitude: 127.0366, placeName: null },
  { diaryId: "demo-4", latitude: 37.5472, longitude: 127.0409, placeName: "성수동 카페거리" },
  { diaryId: "demo-5", latitude: 37.5429, longitude: 127.0402, placeName: "뚝섬 한강공원" },
  { diaryId: "demo-6", latitude: 37.5456, longitude: 127.0429, placeName: null },
  { diaryId: "demo-7", latitude: 37.5421, longitude: 127.0338, placeName: "뚝섬 놀이터" },
];

export class DemoMapPinRepository implements MapPinRepository {
  async list(_petId: string, bounds: MapBounds): Promise<MapPinList> {
    return {
      pins: SAMPLE_PINS.filter((pin) => containsPoint(bounds, pin)),
      truncated: false,
      sample: true,
    };
  }
}
