import type { ApiClient } from "../../../shared/http/apiClient.ts";
import type { MapBounds } from "../../domain/MapBounds.ts";
import type { MapPinList, MapPinRepository } from "../../application/ports.ts";

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

export class HttpMapPinRepository implements MapPinRepository {
  private readonly http: ApiClient;

  constructor(http: ApiClient) {
    this.http = http;
  }

  async list(petId: string, bounds: MapBounds): Promise<MapPinList> {
    const query = [
      `petId=${encodeURIComponent(petId)}`,
      `minLatitude=${decimal(bounds.minLatitude)}`,
      `minLongitude=${decimal(bounds.minLongitude)}`,
      `maxLatitude=${decimal(bounds.maxLatitude)}`,
      `maxLongitude=${decimal(bounds.maxLongitude)}`,
    ].join("&");
    const result = await this.http.get<PinsDto>(`/maps/pins?${query}`);
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
  }
}
