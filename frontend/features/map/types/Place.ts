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
  // 실제 데이터가 아닌 개발·테스트용 예시 장소다(목업을 명시적으로 켰을 때만)
  sample: boolean;
};

export type PlaceQuery = {
  // 보내지 않으면 세 종류 모두(서버도 키를 생략했을 때만 모두다). 빈 목록은 고른 종류가 없다는 뜻이라 서버에 묻지 않고 빈 결과를 준다.
  categories?: readonly PlaceCategory[];
};

export interface PlaceRepository {
  list(bounds: MapBounds, query?: PlaceQuery): Promise<PlaceList>;
}
