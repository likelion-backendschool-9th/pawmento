// 위치 선택 디자인 확인용. 실제 주소 검색이나 GPS 추출 결과가 아니다.
export const DEMO_LOCATION_SEARCH = [
  { id: "seoul-forest", name: "서울숲", address: "서울특별시 성동구 뚝섬로 273", latitude: 37.5445, longitude: 127.0374 },
  { id: "seoul-forest-dog", name: "서울숲 반려견 놀이터", address: "서울특별시 성동구 성수동1가 서울숲", latitude: 37.5461, longitude: 127.0425 },
  { id: "ttukseom", name: "뚝섬한강공원", address: "서울특별시 광진구 강변북로 139", latitude: 37.5294, longitude: 127.0699 },
  { id: "olympic", name: "올림픽공원", address: "서울특별시 송파구 올림픽로 424", latitude: 37.5207, longitude: 127.1216 },
];

export function searchDemoLocations(query: string) {
  const text = query.replace(/\s/g, "").toLocaleLowerCase();
  if (!text) return [];
  return DEMO_LOCATION_SEARCH.filter((place) => `${place.name}${place.address}`.replace(/\s/g, "").toLocaleLowerCase().includes(text));
}
