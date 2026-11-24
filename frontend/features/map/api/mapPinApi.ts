import { ApplicationError } from "../../../lib/application/ApplicationError.ts";
import type { ApiClient } from "../../../lib/http/apiClient.ts";
import type { MapBounds } from "../types/MapBounds.ts";
import type { MapPinList } from "../types/MapPin.ts";
import { clampBounds, isValidBounds } from "../utils/mapBounds.ts";

type PinsDto = {
  pins: {
    diaryId: number | string;
    latitude: number;
    longitude: number;
    placeName: string | null;
  }[];
  truncated: boolean;
};

// 서버는 영역 값을 십진수 문자열로만 받는다. toFixed는 지수 표기(1e-7)를 만들지 않아 항상 그 모양이다.
const decimal = (value: number) => value.toFixed(6);

export function checkedMapArea(petId: string, bounds: MapBounds): MapBounds {
  const area = clampBounds(bounds);
  if (!petId.trim() || !isValidBounds(area)) {
    throw new ApplicationError("VALIDATION", "지도 영역을 확인해 주세요.");
  }
  return area;
}

export function createMapPinApi(http: ApiClient) {
  return {
    async listPins(petId: string, bounds: MapBounds): Promise<MapPinList> {
      const area = checkedMapArea(petId, bounds);
      const query = [
        `petId=${encodeURIComponent(petId)}`,
        `minLatitude=${decimal(area.minLatitude)}`,
        `minLongitude=${decimal(area.minLongitude)}`,
        `maxLatitude=${decimal(area.maxLatitude)}`,
        `maxLongitude=${decimal(area.maxLongitude)}`,
      ].join("&");
      const result = await http.get<PinsDto>(`/maps/pins?${query}`);
      return {
        pins: result.pins.map((pin) => ({
          diaryId: String(pin.diaryId),
          latitude: pin.latitude,
          longitude: pin.longitude,
          placeName: pin.placeName,
        })),
        truncated: result.truncated,
        sample: false,
      };
    },
  };
}
