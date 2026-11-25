import test from "node:test";
import assert from "node:assert/strict";
import {
  closedDaysLine,
  dataAsOfDate,
  dataAsOfLabel,
  hoursLine,
  open24hLabel,
  openingLine,
  phoneHref,
  sourceLabel,
  sourceLine,
} from "../features/map/utils/placeCard.ts";
import { DEMO_PLACES } from "../mocks/fixtures/demoPlaces.ts";

// 목업 장소는 #172-S17의 경우를 하나씩 담고 있다
const [withPhone, nameBased24, noPhoneGrooming, grooming, hours24Shelter] =
  DEMO_PLACES;

test("#172-S17 전화가 있으면 tel 링크로 걸 수 있고, 하이픈과 공백은 지운다", () => {
  assert.equal(phoneHref(withPhone.phone), "tel:020000001");
  assert.equal(phoneHref("02-2273-3233"), "tel:0222733233");
  assert.equal(phoneHref(" 031 123 4567 "), "tel:0311234567");
});

test("#172-S17 전화가 없거나 걸 수 없는 값이면 버튼을 만들지 않는다", () => {
  assert.equal(phoneHref(noPhoneGrooming.phone), null);
  assert.equal(phoneHref(""), null);
  // 0으로 시작하지 않거나 너무 짧은 값, 글자가 섞인 값은 걸 수 없다
  assert.equal(phoneHref("3672-8441"), null);
  assert.equal(phoneHref("02-12"), null);
  assert.equal(phoneHref("전화 없음"), null);
  // tel 주소에 숫자 외의 글자가 들어가지 않는다
  assert.equal(phoneHref("02-000-0001;rm"), "tel:020000001");
});

test("#172-S17 24시는 근거에 따라 다르게 보인다: 이름 기준은 추정이라고 구분한다", () => {
  assert.equal(open24hLabel(nameBased24), "24시(이름 기준 추정)");
  assert.equal(open24hLabel(hours24Shelter), "24시간 운영");
  assert.equal(open24hLabel(withPhone), null);
  // 근거를 모르는 24시는 단정하지 않는다
  assert.equal(open24hLabel({ ...nameBased24, open24hBasis: null }), "24시(확인 필요)");
});

test("#172-S17 운영 시간: 값이 있으면 보이고, 24시간 운영이면 되풀이하지 않는다", () => {
  assert.equal(hoursLine(DEMO_PLACES[5]), "운영 시간 09:00~18:00");
  assert.equal(hoursLine(hours24Shelter), null);
  // 병원·미용은 원래 값이 없어서 줄을 만들지 않고, 보호센터만 없다고 알린다
  assert.equal(hoursLine(withPhone), null);
  assert.equal(hoursLine({ ...DEMO_PLACES[5], openingHours: null }), "운영 시간 정보 없음");
});

test("#172-S17 휴무일: 값이 있으면 그대로, 없으면 단정하지 않고 확인이 필요하다고 한다", () => {
  assert.equal(closedDaysLine(DEMO_PLACES[5]), "휴무일 토요일+일요일");
  assert.equal(closedDaysLine({ ...DEMO_PLACES[5], closedDays: "연중무휴" }), "연중무휴");
  assert.equal(closedDaysLine(hours24Shelter), "휴무 정보 확인 필요");
  assert.equal(closedDaysLine(grooming), null);
});

test("#172-S17 출처는 기관명만 보이고, 종류마다 그 장소의 출처다", () => {
  assert.equal(sourceLabel("HOSPITAL"), "출처 행정안전부");
  assert.equal(sourceLabel("GROOMING"), "출처 행정안전부");
  assert.equal(sourceLabel("SHELTER"), "출처 농림축산식품부");
});

test("#172-S21 가져온 시각은 한국 날짜로 보이고, 없거나 읽을 수 없으면 보이지 않는다", () => {
  assert.equal(dataAsOfLabel("2026-11-21T13:00:00Z"), "데이터 기준: 2026-11-21");
  // UTC 15시 이후는 한국에서는 다음 날이다
  assert.equal(dataAsOfLabel("2026-11-21T15:00:00Z"), "데이터 기준: 2026-11-22");
  assert.equal(dataAsOfLabel("2026-11-23T22:00:00+09:00"), "데이터 기준: 2026-11-23");
  assert.equal(dataAsOfLabel(null), null);
  assert.equal(dataAsOfLabel("어제"), null);
  assert.equal(dataAsOfLabel(""), null);
});

test("#172-S17 운영 시간과 휴무일은 한 줄로 합쳐 보이고, 보일 것이 없으면 줄이 없다", () => {
  assert.equal(
    openingLine(DEMO_PLACES[5]),
    "운영 시간 09:00~18:00 · 휴무일 토요일+일요일",
  );
  // 24시간 운영 센터는 운영 시간을 되풀이하지 않고 휴무 정보만 남는다
  assert.equal(openingLine(hours24Shelter), "휴무 정보 확인 필요");
  assert.equal(openingLine(withPhone), null);
});

test("#172-S17 카드 아래 줄은 출처에 가져온 날짜를 붙이고, 날짜를 모르면 출처만 보인다", () => {
  assert.equal(
    sourceLine("HOSPITAL", "2026-11-21T13:00:00Z"),
    "출처 행정안전부 · 데이터 기준 2026-11-21",
  );
  assert.equal(sourceLine("SHELTER", null), "출처 농림축산식품부");
  assert.equal(sourceLine("SHELTER", "어제"), "출처 농림축산식품부");
  assert.equal(dataAsOfDate("2026-11-21T15:00:00Z"), "2026-11-22");
});
