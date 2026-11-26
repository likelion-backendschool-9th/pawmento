import { ApplicationError } from "../../../lib/application/ApplicationError.ts";
import type { ApiClient } from "../../../lib/http/apiClient.ts";
import type { MapBounds } from "../types/MapBounds.ts";
import {
  PLACE_CATEGORIES,
  type Open24hBasis,
  type PlaceCategory,
  type PlaceList,
  type PlaceHit,
  type PlaceQuery,
  type PlaceRepository,
  type PlaceSearchQuery,
  type PlaceSearchResult,
} from "../types/Place.ts";
import { clampBounds, isValidBounds } from "../utils/mapBounds.ts";
import { checkSearchText, SEARCH_TEXT_MESSAGES } from "../utils/placeSearchText.ts";

type PlaceAddressDto = { address: string | null };

type PlacesDto = {
  places: {
    id: number | string;
    category: string;
    name: string;
    address: string | null;
    phone: string | null;
    latitude: number;
    longitude: number;
    open24h: boolean;
    open24hBasis: string | null;
    openingHours: string | null;
    closedDays: string | null;
  }[];
  truncated: boolean;
  // 서버가 아직 주지 않는 배포와도 맞도록 없을 수 있는 값으로 읽는다
  dataAsOf?: string | null;
};

type PlaceDto = PlacesDto["places"][number];

type PlaceSearchDto = {
  places: (PlaceDto & { distanceMeters?: number | null })[];
  truncated: boolean;
  dataAsOf?: string | null;
};

// 서버는 영역 값을 십진수 문자열로만 받는다. toFixed는 지수 표기(1e-7)를 만들지 않아 항상 그 모양이다.
const decimal = (value: number) => value.toFixed(6);

const isCategory = (value: string): value is PlaceCategory =>
  (PLACE_CATEGORIES as readonly string[]).includes(value);

const isBasis = (value: string | null): value is Open24hBasis =>
  value === "NAME" || value === "HOURS";

// 지도 SDK가 아주 멀리 줄였을 때 주는 범위 밖 값은 서버가 받는 범위로 맞추지만, 무한대나 거꾸로 된 영역은 맞춘 뒤에 정상으로 보이므로
// (예: 위도 95~94가 둘 다 90이 됨, ±무한대가 전 세계가 됨) 보정하기 전에 먼저 거절한다.
function isFiniteAndOrdered(bounds: MapBounds): boolean {
  return (
    Object.values(bounds).every(Number.isFinite) &&
    bounds.minLatitude <= bounds.maxLatitude &&
    bounds.minLongitude <= bounds.maxLongitude
  );
}

export function checkedPlaceArea(bounds: MapBounds): MapBounds {
  if (!isFiniteAndOrdered(bounds)) {
    throw new ApplicationError("VALIDATION", "지도 영역을 확인해 주세요.");
  }
  const area = clampBounds(bounds);
  if (!isValidBounds(area)) {
    throw new ApplicationError("VALIDATION", "지도 영역을 확인해 주세요.");
  }
  return area;
}

// 같은 종류를 두 번 보내지 않고, 모르는 종류는 서버에 보내지 않는다.
export function checkedPlaceCategories(
  categories: readonly PlaceCategory[],
): PlaceCategory[] {
  const unique: PlaceCategory[] = [];
  for (const category of categories) {
    if (!isCategory(category)) {
      throw new ApplicationError("VALIDATION", "장소 종류를 확인해 주세요.");
    }
    if (!unique.includes(category)) unique.push(category);
  }
  return unique;
}

const toPlace = (place: PlaceDto) => ({
  id: String(place.id),
  category: place.category as PlaceCategory,
  name: place.name,
  address: place.address,
  phone: place.phone,
  latitude: place.latitude,
  longitude: place.longitude,
  open24h: place.open24h,
  open24hBasis: isBasis(place.open24hBasis) ? place.open24hBasis : null,
  openingHours: place.openingHours,
  closedDays: place.closedDays,
});

// 지도 중심은 위도·경도가 모두 범위 안의 유한한 수일 때만 보낸다. 틀리면 서버가 400을 주므로 미리 거절한다.
export function checkedSearchCenter(
  center: PlaceSearchQuery["center"],
): { latitude: number; longitude: number } | undefined {
  if (center === undefined) return undefined;
  const { latitude, longitude } = center;
  if (
    !Number.isFinite(latitude) ||
    !Number.isFinite(longitude) ||
    Math.abs(latitude) > 90 ||
    Math.abs(longitude) > 180
  ) {
    throw new ApplicationError("VALIDATION", "지도 중심을 확인해 주세요.");
  }
  return { latitude, longitude };
}

// 서버의 장소 id는 양의 정수다. 예시 장소처럼 그렇지 않은 id는 서버에 묻지 않는다.
const isServerPlaceId = (id: string) => /^[1-9][0-9]*$/.test(id);

export function createPlaceApi(http: ApiClient): PlaceRepository {
  return {
    async resolveAddress(placeId: string): Promise<string | null> {
      if (!isServerPlaceId(placeId)) return null;
      const result = await http.get<PlaceAddressDto>(
        `/maps/places/${placeId}/address`,
      );
      return typeof result.address === "string" && result.address.trim() !== ""
        ? result.address
        : null;
    },

    async list(bounds: MapBounds, query: PlaceQuery = {}): Promise<PlaceList> {
      const area = checkedPlaceArea(bounds);
      const categories =
        query.categories === undefined
          ? undefined
          : checkedPlaceCategories(query.categories);
      // 고른 종류가 없으면 보여 줄 것이 없다. 서버는 빈 categories를 400으로 거절하고(키를 생략해야 세 종류 모두다) 이 경우는
      // 칩을 모두 끈 정상 동작이라, 서버에 묻지 않고 빈 목록을 돌려준다.
      if (categories !== undefined && categories.length === 0) {
        return { places: [], truncated: false, dataAsOf: null, sample: false };
      }
      const params = [
        `minLatitude=${decimal(area.minLatitude)}`,
        `minLongitude=${decimal(area.minLongitude)}`,
        `maxLatitude=${decimal(area.maxLatitude)}`,
        `maxLongitude=${decimal(area.maxLongitude)}`,
      ];
      if (categories !== undefined) params.push(`categories=${categories.join(",")}`);
      const result = await http.get<PlacesDto>(`/maps/places?${params.join("&")}`);
      return {
        places: result.places.map(toPlace),
        truncated: result.truncated,
        dataAsOf: typeof result.dataAsOf === "string" ? result.dataAsOf : null,
        sample: false,
      };
    },

    async search(query: PlaceSearchQuery): Promise<PlaceSearchResult> {
      // 검색어는 서버에 묻기 전에 같은 기준으로 확인한다(너무 짧거나 길거나 쓸 수 없는 글자). 이유에 검색어를 담지 않는다.
      const checked = checkSearchText(query.q);
      if (!checked.ok) {
        throw new ApplicationError("VALIDATION", SEARCH_TEXT_MESSAGES[checked.problem]);
      }
      const categories =
        query.categories === undefined
          ? undefined
          : checkedPlaceCategories(query.categories);
      const center = checkedSearchCenter(query.center);
      // 고른 종류가 없으면 찾을 것이 없다. 서버는 빈 categories를 400으로 거절하므로 묻지 않고 빈 결과를 준다.
      if (categories !== undefined && categories.length === 0) {
        return { places: [], truncated: false, dataAsOf: null, sample: false };
      }
      const params = [`q=${encodeURIComponent(checked.text)}`];
      if (categories !== undefined) params.push(`categories=${categories.join(",")}`);
      if (center !== undefined) {
        params.push(`latitude=${decimal(center.latitude)}`, `longitude=${decimal(center.longitude)}`);
      }
      const result = await http.get<PlaceSearchDto>(`/maps/places/search?${params.join("&")}`);
      return {
        places: result.places.map(
          (place): PlaceHit => ({
            ...toPlace(place),
            distanceMeters:
              typeof place.distanceMeters === "number" && Number.isFinite(place.distanceMeters)
                ? Math.round(place.distanceMeters)
                : null,
          }),
        ),
        truncated: result.truncated,
        dataAsOf: typeof result.dataAsOf === "string" ? result.dataAsOf : null,
        sample: false,
      };
    },
  };
}
