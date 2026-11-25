import type { PlaceCategory } from "../types/Place.ts";

// 정보 지도의 마커는 다이어리 지도의 핀(발자국·꽃)과 달리 네이버·카카오 지도처럼 둥근 종류별 아이콘이다.
// 종류는 색과 모양을 함께 달리해서 색만으로 구분하지 않는다(병원 ✚, 미용 ✂, 보호센터 집).
export const PLACE_STYLE: Record<
  PlaceCategory,
  { label: string; color: string; glyph: string }
> = {
  HOSPITAL: {
    label: "병원",
    color: "#3a7fc1",
    glyph: `<path d="M0-6.5V6.5M-6.5 0H6.5" stroke="#fff" stroke-width="3.2" stroke-linecap="round"/>`,
  },
  GROOMING: {
    label: "미용",
    color: "#d1692f",
    glyph: `<g stroke="#fff" stroke-width="2.2" stroke-linecap="round" fill="none"><circle cx="-4.2" cy="4.5" r="2.4"/><circle cx="4.2" cy="4.5" r="2.4"/><path d="M-3 2.6L4.5-6.2M3 2.6L-4.5-6.2"/></g>`,
  },
  SHELTER: {
    label: "보호센터",
    color: "#8366b8",
    glyph: `<path d="M-7 1.5L0-6.5 7 1.5V7.5H-7z" fill="#fff"/>`,
  },
};

// 좌표 위치가 가운데인 지름 34(선택하면 52)의 둥근 마커. 선택하면 후광이 생긴다. 지도 밖에서 쓰는 작은 아이콘은 size로 크기를 정한다.
export function placeMarkup(
  category: PlaceCategory,
  selected = false,
  iconSize?: number,
): string {
  const { color, glyph } = PLACE_STYLE[category];
  const size = iconSize ?? (selected ? 52 : 34);
  const halo = selected
    ? `<circle r="25" fill="${color}" opacity=".16"/><circle r="20" fill="none" stroke="${color}" stroke-width="1.5" opacity=".4"/>`
    : "";
  return `<svg width="${size}" height="${size}" viewBox="-26 -26 52 52" style="overflow:visible;display:block" aria-hidden="true">
    ${halo}
    <circle r="17" fill="#fffefb"/>
    <circle r="14" fill="${color}"/>
    ${glyph}
  </svg>`;
}
