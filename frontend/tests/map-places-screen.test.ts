import test from "node:test";
import assert from "node:assert/strict";
import {
  PLACE_CATEGORIES,
  type Place,
  type PlaceCategory,
} from "../features/map/types/Place.ts";
import { countByCategory, toggleCategory } from "../features/map/utils/placeCounts.ts";
import { PLACE_STYLE, placeMarkup } from "../features/map/utils/placeMarkup.ts";
import {
  MOBILE_NAV_LIMIT,
  mobileNavLinks,
} from "../features/shell/utils/mobileNavigation.ts";
import { DEMO_PLACES } from "../mocks/fixtures/demoPlaces.ts";

const place = (id: string, category: PlaceCategory): Place => ({
  ...DEMO_PLACES[0],
  id,
  category,
});

test("#172-S16 종류 칩의 숫자는 영역 안 장소를 종류별로 센 값이고, 없는 종류는 0이다", () => {
  const counts = countByCategory([
    place("1", "HOSPITAL"),
    place("2", "HOSPITAL"),
    place("3", "GROOMING"),
  ]);

  assert.deepEqual(counts, { HOSPITAL: 2, GROOMING: 1, SHELTER: 0 });
  assert.deepEqual(countByCategory([]), { HOSPITAL: 0, GROOMING: 0, SHELTER: 0 });
});

test("#172-S16 칩을 누르면 그 종류가 켜지고 꺼지며, 켜진 종류의 순서는 항상 같다", () => {
  let selected: PlaceCategory[] = [...PLACE_CATEGORIES];

  selected = toggleCategory(selected, "GROOMING");
  assert.deepEqual(selected, ["HOSPITAL", "SHELTER"]);

  // 다시 켜면 원래 자리로 돌아간다(누른 순서가 아니라 정해진 순서)
  selected = toggleCategory(selected, "GROOMING");
  assert.deepEqual(selected, ["HOSPITAL", "GROOMING", "SHELTER"]);

  // 모두 끌 수 있고, 하나만 켤 수도 있다
  assert.deepEqual(toggleCategory(["SHELTER"], "SHELTER"), []);
  assert.deepEqual(toggleCategory([], "SHELTER"), ["SHELTER"]);
  // 입력을 바꾸지 않는다
  const original: PlaceCategory[] = ["HOSPITAL"];
  toggleCategory(original, "GROOMING");
  assert.deepEqual(original, ["HOSPITAL"]);
});

test("#172-S16 종류마다 마커의 색과 모양이 다르고 선택하면 후광이 생긴다", () => {
  const markups = PLACE_CATEGORIES.map((category) => placeMarkup(category));
  assert.equal(new Set(markups).size, PLACE_CATEGORIES.length, "종류마다 다르다");
  const colors = PLACE_CATEGORIES.map((category) => PLACE_STYLE[category].color);
  assert.equal(new Set(colors).size, PLACE_CATEGORIES.length, "색이 겹치지 않는다");
  for (const category of PLACE_CATEGORIES) {
    assert.ok(placeMarkup(category).includes(PLACE_STYLE[category].color));
    assert.ok(placeMarkup(category).includes('aria-hidden="true"'));
    assert.ok(placeMarkup(category, true).length > placeMarkup(category).length, "선택하면 후광이 더해진다");
  }
  assert.deepEqual(
    PLACE_CATEGORIES.map((category) => PLACE_STYLE[category].label),
    ["병원", "미용", "보호센터"],
  );
});

test("#172-S16 모바일 하단 메뉴는 앞의 네 개와 지도로 이루어지고, 더보기는 없다", () => {
  const links = [
    { label: "홈" },
    { label: "오늘의 질문" },
    { label: "다이어리" },
    { label: "커뮤니티" },
  ];
  const map = { label: "지도" };

  const nav = mobileNavLinks(links, map);

  assert.deepEqual(
    nav.map((link) => link.label),
    ["홈", "오늘의 질문", "다이어리", "커뮤니티", "지도"],
  );
  assert.equal(nav.length, MOBILE_NAV_LIMIT + 1);

  // 정원 모드처럼 메뉴가 다섯 개여도 하단은 다섯 칸을 넘지 않고 지도가 마지막이다
  const garden = [...links.slice(0, 3), { label: "우체통" }, { label: "커뮤니티" }];
  const gardenNav = mobileNavLinks(garden, map);
  assert.equal(gardenNav.length, 5);
  assert.equal(gardenNav[gardenNav.length - 1].label, "지도");
  assert.ok(!gardenNav.some((link) => link.label === "더보기"));
});

test("#172-S16 마커 크기는 선택 여부와 별개로 인자로 정할 수 있다", () => {
  assert.match(placeMarkup("HOSPITAL"), /width="34" height="34"/);
  assert.match(placeMarkup("HOSPITAL", true), /width="52" height="52"/);
  // 카드 안의 작은 아이콘처럼 지도 밖에서는 크기를 직접 정한다
  assert.match(placeMarkup("SHELTER", false, 20), /width="20" height="20"/);
});
