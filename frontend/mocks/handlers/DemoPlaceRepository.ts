import type { MapBounds } from "../../features/map/types/MapBounds.ts";
import type {
  PlaceHit,
  PlaceList,
  PlaceQuery,
  PlaceRepository,
  PlaceSearchQuery,
  PlaceSearchResult,
} from "../../features/map/types/Place.ts";
import { containsPoint } from "../../features/map/utils/mapBounds.ts";
import { checkSearchText, comparableSearchText } from "../../features/map/utils/placeSearchText.ts";
import { distanceBetween } from "../../features/map/utils/walk.ts";
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

  // 예시 장소 안에서 이름·주소를 찾는다(#195). 서버와 같이 띄어쓰기·구두점·대소문자를 무시하고, 지도 중심을 주면 가까운 순이다.
  async search(query: PlaceSearchQuery): Promise<PlaceSearchResult> {
    const checked = checkSearchText(query.q);
    const key = checked.ok ? comparableSearchText(checked.text) : "";
    const categories = query.categories;
    const center = query.center;
    const hits: PlaceHit[] = DEMO_PLACES.filter(
      (place) =>
        key !== "" &&
        (categories === undefined || categories.includes(place.category)) &&
        (comparableSearchText(place.name).includes(key) ||
          comparableSearchText(place.address ?? "").includes(key)),
    ).map((place) => ({
      ...place,
      distanceMeters: center ? Math.round(distanceBetween(center, place)) : null,
    }));
    if (center) hits.sort((a, b) => (a.distanceMeters ?? 0) - (b.distanceMeters ?? 0));
    return { places: hits.slice(0, 20), truncated: hits.length > 20, dataAsOf: null, sample: true };
  }
}
