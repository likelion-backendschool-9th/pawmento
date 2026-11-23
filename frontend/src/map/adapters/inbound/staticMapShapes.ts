// 예시 지도(네이버 지도 키가 없을 때)의 바탕 그림에 쓰는 도형이다. 0~100 안의 좌표를 쓰고 항상 같은 모양이다.

export type Tree = { x: number; y: number; r: number };

// 공원 안에 흩어 놓는 나무(동그라미). 같은 값에서 늘 같은 배치가 나오도록 고정된 난수로 만든다.
export const PARK = { cx: 24, cy: 34, rx: 30, ry: 22 };

export function parkTrees(count = 44): Tree[] {
  let seed = 20261121;
  const next = () => {
    seed = (seed * 1664525 + 1013904223) % 4294967296;
    return seed / 4294967296;
  };
  const trees: Tree[] = [];
  let guard = 0;
  while (trees.length < count && guard < count * 40) {
    guard += 1;
    const x = PARK.cx + (next() * 2 - 1) * PARK.rx;
    const y = PARK.cy + (next() * 2 - 1) * PARK.ry;
    const inside = ((x - PARK.cx) / (PARK.rx * 0.92)) ** 2 + ((y - PARK.cy) / (PARK.ry * 0.9)) ** 2 <= 1;
    if (!inside) continue;
    // 서로 너무 겹치지 않게 한다
    if (trees.some((t) => (t.x - x) ** 2 + (t.y - y) ** 2 < 2.6 ** 2)) continue;
    trees.push({ x, y, r: 0.45 + next() * 0.8 });
  }
  return trees;
}

// 예시 지도는 0~100 정사각형을 화면 비율에 맞춰 가운데를 잘라 보여 준다(slice). 그림은 긴 쪽에 맞춰 늘어나므로,
// 화면이 가로로 넓으면 세로가 일부만 보이고 세로로 길면 가로가 일부만 보인다. 이 함수는 화면 크기에서 보이는 범위를 구한다.
export type VisibleWindow = { minX: number; maxX: number; minY: number; maxY: number };

export function visibleWindow(width: number, height: number): VisibleWindow {
  const visibleX = width >= height ? 100 : (100 * width) / height;
  const visibleY = width >= height ? (100 * height) / width : 100;
  return {
    minX: (100 - visibleX) / 2,
    maxX: (100 + visibleX) / 2,
    minY: (100 - visibleY) / 2,
    maxY: (100 + visibleY) / 2,
  };
}

// 강과 지명을 둘 때 지켜야 하는 화면 크기(지도 영역 기준, 가로×세로 px): 데스크톱 1280×720의 추억 지도(약 1188×488),
// 1280×640 화면, 휴대폰 세로 화면(390×700)이다. 모든 크기에서 보이는 범위가 겹치는 곳에 둔다.
export const GUARANTEED_SIZES: ReadonlyArray<readonly [number, number]> = [
  [1188, 488],
  [1190, 640],
  [390, 700],
];

// 강은 화면 가운데 아래를 지나는 띠다. 위·아래 곡선이 모두 보이는 범위 안에 있다.
export const RIVER = {
  // 양 끝은 화면 밖(-300, 400)까지 곧게 이어 위치 고르기에서 지도를 끌어도 강이 잘려 보이지 않는다
  top: "M-300 55 L-20 55 C 20 52, 60 60, 120 54 L400 54",
  bottom: "L400 61 L120 61 C 60 67, 20 59, -20 63 L-300 63 Z",
  // 곡선이 가장 낮게 내려가는 y(제어점 기준 상한)
  lowestY: 67,
};

// 지명. 실제 위치와 무관한 예시 이름이라, 위치를 고르는 화면에서는 보여 주지 않는다. (x, y)는 글자의 왼쪽 아래다.
export type MapLabel = { text: string; x: number; y: number; water?: boolean };
export const LABELS: MapLabel[] = [
  { text: "서울숲", x: 30, y: 46 },
  { text: "한강", x: 38, y: 60, water: true },
  { text: "뚝섬로", x: 26, y: 66 },
  { text: "아차산로", x: 62, y: 50 },
];

// 지명 글자의 크기(StaticMapBackdrop.module.css의 font-size)와 글자 하나의 가로 폭. 화면 안에 들어오는지 셀 때 쓴다.
export const LABEL_FONT = 2.6;
