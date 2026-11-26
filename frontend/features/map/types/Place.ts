import type { MapBounds } from "./MapBounds.ts";

// 정보 지도의 장소 종류. 서버의 PlaceCategory와 같은 이름이다.
export const PLACE_CATEGORIES = ["HOSPITAL", "GROOMING", "SHELTER"] as const;
export type PlaceCategory = (typeof PLACE_CATEGORIES)[number];

// 24시간 운영이라고 본 근거. NAME은 이름에 "24시"가 있는 추정이고, HOURS는 운영 시간이 00:00~24:00이다.
// 이름 기준은 검증된 영업시간이 아니라서 화면에서 "24시(이름 기준 추정)"처럼 구분해 보인다.
export type Open24hBasis = "NAME" | "HOURS";

// 정보 지도에 찍는 장소 하나. 서버의 장소와 같은 모양이고, 값이 없으면 null(정보 없음)이다.
export type Place = {
  id: string;
  category: PlaceCategory;
  name: string;
  address: string | null;
  phone: string | null;
  latitude: number;
  longitude: number;
  open24h: boolean;
  open24hBasis: Open24hBasis | null;
  openingHours: string | null;
  closedDays: string | null;
};

export type PlaceList = {
  places: Place[];
  // 영역 안의 장소가 서버의 한 번에 주는 최대 개수(500)를 넘어 잘렸다
  truncated: boolean;
  // 이 목록에 든 장소의 종류들을 마지막으로 가져온 시각(UTC 시각 문자열). 모르면 null이라 화면에 보이지 않는다(#172-S21)
  dataAsOf: string | null;
  // 실제 데이터가 아닌 개발·테스트용 예시 장소다(목업을 명시적으로 켰을 때만)
  sample: boolean;
};

export type PlaceQuery = {
  // 보내지 않으면 세 종류 모두(서버도 키를 생략했을 때만 모두다). 빈 목록은 고른 종류가 없다는 뜻이라 서버에 묻지 않고 빈 결과를 준다.
  categories?: readonly PlaceCategory[];
};

// 이름·주소 검색(#195)의 결과 장소. 영역 조회의 장소에 지도 중심에서의 직선거리(m, 정수)를 더한 것이다.
// 지도 중심을 보내지 않았으면 distanceMeters는 null이다.
export type PlaceHit = Place & { distanceMeters: number | null };

export type PlaceSearchResult = {
  places: PlaceHit[];
  // 겹침을 지운 뒤에도 20곳을 넘어 잘렸다(화면은 "더 있어요. 더 자세히 입력해 보세요"를 보인다)
  truncated: boolean;
  dataAsOf: string | null;
  // 실제 데이터가 아닌 개발·테스트용 예시 장소다(목업을 명시적으로 켰을 때만)
  sample: boolean;
};

export type PlaceSearchQuery = {
  // 사용자가 입력한 검색어. 정규화한 뒤 2~30자여야 한다(서버와 같은 기준, utils/placeSearchText)
  q: string;
  // 보내지 않으면 세 종류 모두. 빈 목록은 고른 종류가 없다는 뜻이라 서버에 묻지 않고 빈 결과를 준다.
  categories?: readonly PlaceCategory[];
  // 지도 중심. 보내면 가까운 순으로 정렬되고 거리가 온다. 위도·경도를 함께 보낸다.
  center?: { latitude: number; longitude: number };
};

export interface PlaceRepository {
  list(bounds: MapBounds, query?: PlaceQuery): Promise<PlaceList>;
  // 이름·주소로 장소를 찾는다(#195). 지도 영역과 상관없이 전체에서 찾는다.
  search(query: PlaceSearchQuery): Promise<PlaceSearchResult>;
  // 주소가 가려진 장소의 주소를 좌표로 찾는다(#239). 찾지 못했거나 서버를 부르지 않는 예시 데이터이면 null이다.
  resolveAddress(placeId: string): Promise<string | null>;
}
