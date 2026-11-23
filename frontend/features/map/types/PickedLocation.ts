// 기록에 붙이려고 지도에서 고른 위치. 서버의 일기 위치(MANUAL: 위도·경도 필수, 장소 이름 선택)와 같은 모양이다.
export type PickedLocation = {
  latitude: number;
  longitude: number;
  placeName: string | null;
};
