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
