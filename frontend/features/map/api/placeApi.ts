import { ApplicationError } from "../../../lib/application/ApplicationError.ts";
import type { ApiClient } from "../../../lib/http/apiClient.ts";
import type { MapBounds } from "../types/MapBounds.ts";
import {
  PLACE_CATEGORIES,
  type Open24hBasis,
  type PlaceCategory,
  type PlaceList,
  type PlaceQuery,
  type PlaceRepository,
} from "../types/Place.ts";
import { clampBounds, isValidBounds } from "../utils/mapBounds.ts";

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
};

// 서버는 영역 값을 십진수 문자열로만 받는다. toFixed는 지수 표기(1e-7)를 만들지 않아 항상 그 모양이다.
const decimal = (value: number) => value.toFixed(6);

const isCategory = (value: string): value is PlaceCategory =>
  (PLACE_CATEGORIES as readonly string[]).includes(value);

const isBasis = (value: string | null): value is Open24hBasis =>
  value === "NAME" || value === "HOURS";

export function checkedPlaceArea(bounds: MapBounds): MapBounds {
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

export function createPlaceApi(http: ApiClient): PlaceRepository {
  return {
    async list(bounds: MapBounds, query: PlaceQuery = {}): Promise<PlaceList> {
      const area = checkedPlaceArea(bounds);
      const categories =
        query.categories === undefined
          ? undefined
          : checkedPlaceCategories(query.categories);
      // 고른 종류가 없으면 보여 줄 것이 없다. 서버에 빈 값을 보내면 세 종류 모두로 읽히니 묻지 않는다.
      if (categories !== undefined && categories.length === 0) {
        return { places: [], truncated: false, sample: false };
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
        places: result.places.map((place) => ({
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
        })),
        truncated: result.truncated,
        sample: false,
      };
    },
  };
}
