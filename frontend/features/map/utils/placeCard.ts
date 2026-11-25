import type { Place, PlaceCategory } from "../types/Place.ts";

// 정보 지도 장소 카드에 보일 글자를 만든다(#172-S17, S21). 화면은 여기서 만든 값을 그대로 보여 주기만 한다.

const SOURCE: Record<PlaceCategory, string> = {
  HOSPITAL: "행정안전부",
  GROOMING: "행정안전부",
  SHELTER: "농림축산식품부",
};

// 전화 걸기 링크. 서버는 걸 수 있는 번호(0으로 시작하는 지역번호)만 주지만, 화면에서도 한 번 더 확인해
// 이상한 값이 오면 버튼을 만들지 않는다. 하이픈·공백은 지운다.
export function phoneHref(phone: string | null): string | null {
  if (phone === null) return null;
  const digits = phone.replace(/\D/g, "");
  return /^0\d{8,10}$/.test(digits) ? `tel:${digits}` : null;
}

// 24시 표시. 이름 기준은 검증된 영업시간이 아니라서 "추정"이라고 구분해 보인다. 24시가 아니면 null이다.
export function open24hLabel(place: Place): string | null {
  if (!place.open24h) return null;
  if (place.open24hBasis === "HOURS") return "24시간 운영";
  if (place.open24hBasis === "NAME") return "24시(이름 기준 추정)";
  return "24시(확인 필요)";
}

// 운영 시간 줄. 24시간 운영이라고 이미 보이면 같은 말을 되풀이하지 않는다.
// 보호센터는 값이 없으면 "정보 없음"을 알리고, 병원·미용은 원래 값이 없어서 줄 자체를 숨긴다.
export function hoursLine(place: Place): string | null {
  if (place.open24hBasis === "HOURS") return null;
  if (place.openingHours !== null) return `운영 시간 ${place.openingHours}`;
  return place.category === "SHELTER" ? "운영 시간 정보 없음" : null;
}

// 휴무일 줄. 값이 없으면 "연중무휴"로 단정하지 않고 확인이 필요하다고 알린다(보호센터만).
export function closedDaysLine(place: Place): string | null {
  if (place.closedDays !== null) {
    return place.closedDays === "연중무휴" ? "연중무휴" : `휴무일 ${place.closedDays}`;
  }
  return place.category === "SHELTER" ? "휴무 정보 확인 필요" : null;
}

// 출처는 기관명만 보인다. 인허가(병원·미용)는 행정안전부, 보호센터는 농림축산식품부다.
export function sourceLabel(category: PlaceCategory): string {
  return `출처 ${SOURCE[category]}`;
}

const KST_OFFSET_MS = 9 * 60 * 60 * 1000;

// 마지막으로 가져온 시각(UTC 시각 문자열)을 한국 날짜 "2026-11-21"로 바꾼다. 없거나 읽을 수 없으면 null이다.
export function dataAsOfDate(dataAsOf: string | null): string | null {
  if (dataAsOf === null) return null;
  const time = Date.parse(dataAsOf);
  if (Number.isNaN(time)) return null;
  return new Date(time + KST_OFFSET_MS).toISOString().slice(0, 10);
}

// 지도 위에 보이는 "데이터 기준: 2026-11-21". 날짜를 모르면 null이라 아무것도 보이지 않는다.
export function dataAsOfLabel(dataAsOf: string | null): string | null {
  const date = dataAsOfDate(dataAsOf);
  return date === null ? null : `데이터 기준: ${date}`;
}

// 카드 아래의 출처 줄. 날짜를 알면 함께 보인다: "출처 행정안전부 · 데이터 기준 2026-11-21"
export function sourceLine(category: PlaceCategory, dataAsOf: string | null): string {
  const date = dataAsOfDate(dataAsOf);
  return date === null ? sourceLabel(category) : `${sourceLabel(category)} · 데이터 기준 ${date}`;
}

// 운영 시간과 휴무일을 한 줄로 합친다("운영 시간 09:00~18:00 · 휴무일 토요일+일요일"). 보일 것이 없으면 null이다.
export function openingLine(place: Place): string | null {
  const parts = [hoursLine(place), closedDaysLine(place)].filter(
    (part): part is string => part !== null,
  );
  return parts.length === 0 ? null : parts.join(" · ");
}
