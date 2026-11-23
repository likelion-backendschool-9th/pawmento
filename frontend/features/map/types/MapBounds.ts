// 지도 화면에 보이는 사각형 영역. 서버의 핀 조회 영역(MapArea)과 같은 범위 규칙을 쓴다.
export type MapBounds = {
  minLatitude: number;
  minLongitude: number;
  maxLatitude: number;
  maxLongitude: number;
};

export type MapPoint = { latitude: number; longitude: number };
