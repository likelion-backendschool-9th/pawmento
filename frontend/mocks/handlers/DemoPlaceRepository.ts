import type { MapBounds } from "../../features/map/types/MapBounds.ts";
import type {
  PlaceList,
  PlaceQuery,
  PlaceRepository,
} from "../../features/map/types/Place.ts";
import { containsPoint } from "../../features/map/utils/mapBounds.ts";
import { DEMO_PLACES } from "../fixtures/demoPlaces.ts";

// 개발·테스트에서 목업을 명시적으로 켰을 때만 쓴다(#172-S19). 서버를 부르지 않고 서울숲 근처의 예시 장소를 보여 준다.
// 목업을 켜지 않았을 때 서버가 안 되는 것을 예시로 가리지 않는다. 그때는 호출한 쪽이 오류 안내를 보인다.
export class DemoPlaceRepository implements PlaceRepository {
  // 예시 장소는 서버를 부르지 않고 예시 주소를 그대로 보인다(#239-S8)
  async resolveAddress(): Promise<string | null> {
    return null;
  }

  async list(bounds: MapBounds, query: PlaceQuery = {}): Promise<PlaceList> {
    const categories = query.categories;
    return {
      places: DEMO_PLACES.filter(
        (place) =>
          containsPoint(bounds, place) &&
          (categories === undefined || categories.includes(place.category)),
      ),
      truncated: false,
      dataAsOf: null,
      sample: true,
    };
  }
}
