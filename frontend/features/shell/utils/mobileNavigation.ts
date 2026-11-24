// 모바일 하단 메뉴는 주요 메뉴 앞의 네 개와 "지도"로 이루어진다. 전체 메뉴는 위쪽 머리글의 버튼으로 열린다.
// 가운데 낀 메뉴가 늘어도 하단 메뉴는 다섯 칸을 넘지 않는다.
export const MOBILE_NAV_LIMIT = 4;

export function mobileNavLinks<T>(links: readonly T[], mapLink: T): T[] {
  return [...links.slice(0, MOBILE_NAV_LIMIT), mapLink];
}
