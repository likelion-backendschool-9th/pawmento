// 거리 글자(#226-S5). 검색 결과(#195)와 내 주변 목록(#226)이 같은 글자를 써서 같은 장소의 거리가 다르게 보이지 않는다.
// 올림해서 보인다: 990m까지는 10m 단위(`140m`), 991m부터는 100m 단위의 소수점 한 자리 km(`1.0km`, `1.2km`).
// 10m 단위로 올리면 991~999m가 `1000m`가 되므로 그때부터 km로 바꾼다.
export function distanceLabel(meters: number): string {
  if (!Number.isFinite(meters) || meters < 0) return "";
  if (meters <= 990) return `${Math.ceil(meters / 10) * 10}m`;
  return `${(Math.ceil(meters / 100) / 10).toFixed(1)}km`;
}
