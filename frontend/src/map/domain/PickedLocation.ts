import { isValidBounds, type MapPoint } from "./MapBounds.ts";

// 기록에 붙이려고 지도에서 고른 위치. 서버의 일기 위치(MANUAL: 위도·경도 필수, 장소 이름 선택)와 같은 모양이다.
export type PickedLocation = {
  latitude: number;
  longitude: number;
  placeName: string | null;
};

// 서버의 장소 이름 한도(DiaryLocation)와 같다
export const PLACE_NAME_MAX_LENGTH = 255;

const roundCoordinate = (value: number) => Math.round(value * 1e6) / 1e6;

// 지도에서 고른 점과 입력한 장소 이름으로 붙일 위치를 만든다. 규칙에 맞지 않으면 사용자에게 보여 줄 문구로 던진다.
export function pickLocation(
  point: MapPoint,
  rawPlaceName: string,
): PickedLocation {
  const area = {
    minLatitude: point.latitude,
    maxLatitude: point.latitude,
    minLongitude: point.longitude,
    maxLongitude: point.longitude,
  };
  if (!isValidBounds(area)) {
    throw Error("위치를 확인해 주세요. 지도를 다시 움직여 핀을 놓아 주세요.");
  }
  const placeName = rawPlaceName.trim();
  if (placeName.length > PLACE_NAME_MAX_LENGTH) {
    throw Error(`장소 이름은 ${PLACE_NAME_MAX_LENGTH}자 이하로 입력해 주세요.`);
  }
  return {
    latitude: roundCoordinate(point.latitude),
    longitude: roundCoordinate(point.longitude),
    // 비워 두면 서버가 저장할 때 주소로 채운다
    placeName: placeName === "" ? null : placeName,
  };
}
