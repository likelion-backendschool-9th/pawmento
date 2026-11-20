import test from "node:test";
import assert from "node:assert/strict";
import {
  PLACE_NAME_MAX_LENGTH,
  pickLocation,
} from "../src/map/domain/PickedLocation.ts";

const SEOUL_FOREST = { latitude: 37.5445, longitude: 127.0374 };

test("#19-S8 지도에서 고른 점과 장소 이름으로 붙일 위치를 만든다", () => {
  const picked = pickLocation(SEOUL_FOREST, "  서울숲 반려견 놀이터  ");
  assert.deepEqual(picked, {
    latitude: 37.5445,
    longitude: 127.0374,
    placeName: "서울숲 반려견 놀이터",
  });
});

test("#19-S8 장소 이름을 비우면 이름 없이 붙이고 서버가 주소로 채우게 한다", () => {
  for (const blank of ["", "   ", "\n\t"]) {
    assert.equal(pickLocation(SEOUL_FOREST, blank).placeName, null);
  }
});

test("#19-S8 좌표는 소수점 여섯 자리로 맞춘다(지도가 주는 긴 소수를 그대로 보내지 않는다)", () => {
  const picked = pickLocation(
    { latitude: 37.544512345678, longitude: 127.037498765432 },
    "",
  );
  assert.equal(picked.latitude, 37.544512);
  assert.equal(picked.longitude, 127.037499);
});

test("#19-S8 장소 이름은 서버와 같은 255자까지이고 넘으면 사용자 문구로 거절한다", () => {
  assert.equal(
    pickLocation(SEOUL_FOREST, "가".repeat(PLACE_NAME_MAX_LENGTH)).placeName
      ?.length,
    PLACE_NAME_MAX_LENGTH,
  );
  assert.throws(
    () => pickLocation(SEOUL_FOREST, "가".repeat(PLACE_NAME_MAX_LENGTH + 1)),
    /255자 이하/,
  );
  // 공백을 지운 뒤 길이로 센다
  assert.doesNotThrow(() =>
    pickLocation(SEOUL_FOREST, ` ${"가".repeat(PLACE_NAME_MAX_LENGTH)} `),
  );
});

test("#19-S8 위도 -90~90, 경도 -180~180을 벗어나거나 숫자가 아닌 좌표는 거절한다", () => {
  for (const point of [
    { latitude: 90.0001, longitude: 0 },
    { latitude: -90.0001, longitude: 0 },
    { latitude: 0, longitude: 180.0001 },
    { latitude: 0, longitude: -180.0001 },
    { latitude: Number.NaN, longitude: 0 },
    { latitude: 0, longitude: Number.POSITIVE_INFINITY },
  ]) {
    assert.throws(() => pickLocation(point, ""), /위치를 확인/, JSON.stringify(point));
  }
  // 경계값은 받는다
  assert.doesNotThrow(() => pickLocation({ latitude: 90, longitude: -180 }, ""));
});
