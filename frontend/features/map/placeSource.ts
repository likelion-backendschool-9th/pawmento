import type { PlaceRepository } from "./types/Place.ts";

// 정보 지도의 장소를 어디서 가져올지 고른다. 개발·테스트에서 목업을 명시적으로 켰을 때만 예시 장소를 보이고(#172-S19),
// 켜지 않았으면 서버를 부른다. 서버가 안 될 때 예시로 대신 보이지 않고 오류를 그대로 올려 화면이 오류 안내를 보이게 한다.
export function selectPlaceRepository(input: {
  mockRequested: boolean;
  server: PlaceRepository;
  demo: PlaceRepository;
}): PlaceRepository {
  return input.mockRequested ? input.demo : input.server;
}
