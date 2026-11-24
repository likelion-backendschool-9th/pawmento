// 지도에 찍는 기억 하나. 서버의 핀(diaryId, 좌표, 장소 이름)과 같은 모양이다.
export type MapPin = {
  diaryId: string;
  latitude: number;
  longitude: number;
  placeName: string | null;
};
