// 핀 모양은 반려동물 모드를 따른다. 일상(ACTIVE)은 발자국, 정원(GARDEN)은 꽃이다.
// 색은 --brand-primary라서 모드가 바뀌면 같이 바뀐다.
export type PinMode = "ACTIVE" | "GARDEN";

const PAW = `<g fill="#fff"><ellipse cx="0" cy="5" rx="7.4" ry="5.6"/><ellipse cx="-9.4" cy="-2.4" rx="3.1" ry="4.2" transform="rotate(-22 -9.4 -2.4)"/><ellipse cx="-3.6" cy="-8.6" rx="3.2" ry="4.5"/><ellipse cx="3.6" cy="-8.6" rx="3.2" ry="4.5"/><ellipse cx="9.4" cy="-2.4" rx="3.1" ry="4.2" transform="rotate(22 9.4 -2.4)"/></g>`;

const FLOWER = `<g>${[0, 72, 144, 216, 288]
  .map(
    (angle) =>
      `<ellipse cx="0" cy="-7" rx="4.3" ry="6.4" fill="#fff" transform="rotate(${angle})"/>`,
  )
  .join("")}<circle r="3.6" fill="var(--color-apricot-300)"/></g>`;

// 끝(좌표 위치)이 아래 가운데인 40x58 핀. 선택하면 후광이 생긴다.
export function pinMarkup(mode: PinMode, selected = false): string {
  const glyph = mode === "GARDEN" ? FLOWER : PAW;
  const halo = selected
    ? `<circle cx="0" cy="-34" r="34" fill="var(--brand-primary)" opacity=".16"/>`
    : "";
  return `<svg width="${selected ? 58 : 40}" height="${selected ? 84 : 58}" viewBox="-20 -58 40 60" style="overflow:visible;display:block" aria-hidden="true">
    ${halo}
    <ellipse cx="0" cy="1" rx="8" ry="3" fill="#39452f" opacity=".22"/>
    <path d="M0 0 C -4 -9 -18 -20 -18 -34 A18 18 0 1 1 18 -34 C 18 -20 4 -9 0 0 Z" fill="var(--brand-primary)" stroke="#fffefb" stroke-width="3" stroke-linejoin="round"/>
    <g transform="translate(0 -34) scale(.82)">${glyph}</g>
  </svg>`;
}
