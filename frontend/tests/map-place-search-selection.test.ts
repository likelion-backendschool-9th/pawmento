import test from "node:test";
import assert from "node:assert/strict";
import {
  keepSelectedId,
  searchSelectionAfterTap,
  withPinned,
} from "../features/map/utils/placeSearchSelection.ts";
import type { Place } from "../features/map/types/Place.ts";

const place = (id: string, name = `장소${id}`): Place => ({
  id,
  category: "HOSPITAL",
  name,
  address: null,
  phone: null,
  latitude: 37.5,
  longitude: 127.0,
  open24h: false,
  open24hBasis: null,
  openingHours: null,
  closedDays: null,
});

test("#195-S11 검색으로 고른 장소가 조회 결과에 없으면(영역 밖, 종류 칩이 꺼짐) 지도에 그리도록 더한다", () => {
  const list = [place("1"), place("2")];
  const pinned = place("9", "강남 동물병원");
  assert.deepEqual(
    withPinned(list, pinned).map((item) => item.id),
    ["1", "2", "9"],
  );
  // 조회 결과 목록 자체는 바꾸지 않는다(칩의 숫자는 조회 결과만 센다)
  assert.equal(list.length, 2);
});

test("#195-S11 고른 장소가 이미 조회 결과에 있으면 중복으로 더하지 않고 조회 결과를 쓴다", () => {
  const fromList = place("2", "조회 결과의 이름");
  const list = [place("1"), fromList];
  const merged = withPinned(list, place("2", "검색 결과의 이름"));
  assert.equal(merged, list);
  assert.equal(merged.find((item) => item.id === "2")?.name, "조회 결과의 이름");
});

test("#195-S11 고른 장소가 없으면 조회 결과를 그대로 둔다", () => {
  const list = [place("1")];
  assert.equal(withPinned(list, null), list);
});

test("#195-S11 새 영역을 조회해도 검색으로 고른 장소의 선택은 유지되고, 그 밖의 장소는 새 목록에 없으면 풀린다", () => {
  // 지도가 옮겨 가 새 목록에 고른 장소가 없어도 유지된다
  assert.equal(keepSelectedId([], "9", "9"), "9");
  // 일반 선택은 새 목록에 있으면 유지, 없으면 푼다
  assert.equal(keepSelectedId(["1", "2"], "2", null), "2");
  assert.equal(keepSelectedId(["1", "2"], "5", null), null);
  // 고른 장소가 아닌 다른 장소는 고른 장소가 있어도 목록에 없으면 푼다
  assert.equal(keepSelectedId(["1"], "5", "9"), null);
  // 선택이 없으면 없는 채다
  assert.equal(keepSelectedId(["1"], null, "9"), null);
});

test("#195-S11 지도에서 다른 장소나 빈 곳을 누르면 검색어를 지우고, 고른 장소를 다시 누르면 이어 둔다", () => {
  assert.equal(searchSelectionAfterTap("9", "9"), "keep");
  assert.equal(searchSelectionAfterTap("2", "9"), "clear");
  // 빈 곳(null)을 누르거나 카드를 닫는 것
  assert.equal(searchSelectionAfterTap(null, "9"), "clear");
  // 검색으로 고른 장소가 없을 때는 지울 것도 없지만 지우는 쪽으로 정리한다
  assert.equal(searchSelectionAfterTap("2", null), "clear");
  assert.equal(searchSelectionAfterTap(null, null), "clear");
});
