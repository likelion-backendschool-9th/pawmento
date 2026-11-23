import type { MapBounds } from "./MapBounds.ts";

// 지도에 찍는 기억 하나. 서버의 핀(diaryId, 좌표, 장소 이름)과 같은 모양이다.
export type MapPin = {
  diaryId: string;
  latitude: number;
  longitude: number;
  placeName: string | null;
};

export type MapPinList = {
  pins: MapPin[];
  // 영역 안의 핀이 서버의 한 번에 주는 최대 개수를 넘어 잘렸다
  truncated: boolean;
  // 실제 기록이 아닌 화면 확인용 예시 핀이다(데모 계정)
  sample: boolean;
};

export type MapPinOptions = {
  // 화면 확인용 예시 핀을 섞는다. 데모 계정에서만 true로 준다(일반 계정의 반려동물에는 섞지 않는다).
  sample?: boolean;
};

export interface MapPinRepository {
  list(petId: string, bounds: MapBounds, options?: MapPinOptions): Promise<MapPinList>;
}
