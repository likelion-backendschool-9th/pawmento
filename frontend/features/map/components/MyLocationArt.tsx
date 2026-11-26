// "내 위치"에 쓰는 그림들(#226). 색은 글자색(currentColor)과 디자인 토큰을 따른다.

// 지도 위 버튼에 쓰는 조준 아이콘: 바깥 원, 눈금 네 개, 가운데 점. located면 점이 커지고 선이 두꺼워진다.
export function LocateGlyph({ size = 26, located = false }: { size?: number; located?: boolean }) {
  return (
    <svg
      viewBox="0 0 24 24"
      width={size}
      height={size}
      fill="none"
      stroke="currentColor"
      strokeWidth={located ? 2.2 : 1.9}
      strokeLinecap="round"
      aria-hidden="true"
      focusable="false"
    >
      <circle data-part="ring" cx="12" cy="12" r="7.2" />
      <path d="M12 1.8v3.2M12 19v3.2M1.8 12H5M19 12h3.2" />
      <circle data-part="core" cx="12" cy="12" r={located ? 3.8 : 3} fill="currentColor" stroke="none" />
    </svg>
  );
}

// 안내에 쓰는 위치 핀. 지도 앱에서 널리 쓰는 물방울 모양 핀을 바탕으로 한다.
// - paw: 발바닥이 들어간 채워진 핀(권한을 묻기 전 안내). 핀 아래에 은은한 그림자가 있다.
// - off: 사선이 그어진 핀(위치가 꺼져 있음, 지원하지 않음)
// - alert: 느낌표가 든 핀(위치를 찾지 못함)
// 사선 아래의 틈과 발바닥은 --glyph-gap(바탕색)으로 칠한다.
const PIN = "M12 20.5s6.5-5.7 6.5-11.2a6.5 6.5 0 1 0-13 0C5.5 14.8 12 20.5 12 20.5z";

export function PinGlyph({ variant, size = 40 }: { variant: "paw" | "off" | "alert"; size?: number }) {
  return (
    <svg viewBox="0 0 24 24" width={size} height={size} aria-hidden="true" focusable="false">
      {variant === "paw" && (
        <>
          <ellipse cx="12" cy="22.2" rx="4.2" ry="1" fill="currentColor" opacity="0.18" />
          <path d={PIN} fill="currentColor" />
          <g fill="var(--glyph-gap, #fff)">
            <ellipse cx="9.4" cy="8" rx="0.9" ry="1.15" />
            <ellipse cx="11.2" cy="6.5" rx="0.9" ry="1.15" />
            <ellipse cx="12.8" cy="6.5" rx="0.9" ry="1.15" />
            <ellipse cx="14.6" cy="8" rx="0.9" ry="1.15" />
            <path d="M12 9.7c-1.4 0-2.6 1-2.6 2.1 0 .8.6 1.2 1.4 1.2.4 0 .8-.2 1.2-.2s.8.2 1.2.2c.8 0 1.4-.4 1.4-1.2 0-1.1-1.2-2.1-2.6-2.1z" />
          </g>
        </>
      )}
      {variant !== "paw" && (
        <g fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round">
          <path d={PIN} />
          {variant === "alert" && <path d="M12 6.4v3.4M12 12.2v.1" />}
          {variant === "off" && (
            <>
              {/* 사선 아래에 바탕색 선을 깔아 핀과 틈을 둔다 */}
              <path d="M4.2 3.6l15.6 15.6" stroke="var(--glyph-gap, #fff)" strokeWidth="5" />
              <path d="M4.2 3.6l15.6 15.6" strokeWidth="2" />
            </>
          )}
        </g>
      )}
    </svg>
  );
}
