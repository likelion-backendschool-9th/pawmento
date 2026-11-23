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

// 예시 지도는 화면 비율에 맞춰 가운데를 잘라 보여 준다(slice). 가로로 넓은 화면(1280×640 등)에서는 세로 약 23~77만 보이므로,
// 강과 지명은 이 범위 안에 둔다.
export const WIDE_VISIBLE = { minY: 23, maxY: 77 };

// 강은 화면 가운데 아래를 지나는 띠다. 위·아래 곡선이 모두 보이는 범위 안에 있다.
export const RIVER = {
  // 양 끝은 화면 밖(-300, 400)까지 곧게 이어 위치 고르기에서 지도를 끌어도 강이 잘려 보이지 않는다
  top: "M-300 62 L-20 62 C 20 58, 60 68, 120 60 L400 60",
  bottom: "L400 68 L120 68 C 60 76, 20 66, -20 70 L-300 70 Z",
  // 곡선이 가장 낮게 내려가는 y(제어점 기준 상한)
  lowestY: 76,
};

// 지명. 실제 위치와 무관한 예시 이름이라, 위치를 고르는 화면에서는 보여 주지 않는다.
export type MapLabel = { text: string; x: number; y: number; water?: boolean };
export const LABELS: MapLabel[] = [
  { text: "서울숲", x: 40, y: 52 },
  { text: "뚝섬로", x: 3, y: 75 },
  { text: "한강", x: 38, y: 68, water: true },
  { text: "아차산로", x: 86, y: 60 },
];
