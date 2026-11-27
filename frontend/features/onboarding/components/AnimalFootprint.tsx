export type FootprintKind = "dog" | "cat" | "bird" | "rabbit" | "reptile";

// 로고와 같은 단순한 면/선으로 여러 종의 발 모양을 구분한다. 장식과 페이지 인디케이터에서 함께 사용한다.
export function AnimalFootprint({ kind = "dog", className }: { kind?: FootprintKind; className?: string }) {
  return <svg className={className} viewBox="0 0 40 40" fill="currentColor" aria-hidden="true" focusable="false">
    {kind === "dog" && <><ellipse cx="9" cy="14" rx="4" ry="5.5" transform="rotate(-25 9 14)" /><ellipse cx="17" cy="8" rx="4" ry="5.5" /><ellipse cx="26" cy="9" rx="4" ry="5.5" transform="rotate(15 26 9)" /><ellipse cx="33" cy="17" rx="3.5" ry="5" transform="rotate(30 33 17)" /><path d="M10 29c0-4 5-6 7-10 2-3 6-2 8 1 2 4 6 6 5 10-1 5-7 2-10 2s-10 3-10-3Z" /></>}
    {kind === "cat" && <><ellipse cx="10" cy="13" rx="3" ry="4" /><ellipse cx="18" cy="8" rx="3" ry="4" /><ellipse cx="26" cy="10" rx="3" ry="4" /><ellipse cx="32" cy="16" rx="3" ry="4" /><path d="M11 29c0-3 4-5 7-10 1-2 4-2 5 0l7 10c2 5-4 5-7 3l-3-1-4 2c-4 1-6-1-5-4Z" /></>}
    {kind === "bird" && <g fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round"><path d="m20 29-1-22m1 17L7 12m13 12L32 10m-12 17 6 9" /></g>}
    {kind === "rabbit" && <><ellipse cx="13" cy="24" rx="5" ry="10" transform="rotate(-13 13 24)" /><ellipse cx="28" cy="18" rx="5" ry="10" transform="rotate(9 28 18)" /><circle cx="10" cy="9" r="2" /><circle cx="15" cy="8" r="2" /><circle cx="26" cy="4" r="2" /><circle cx="31" cy="5" r="2" /></>}
    {kind === "reptile" && <><ellipse cx="20" cy="24" rx="6" ry="8" /><g fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round"><path d="m18 19-6-12m8 12 1-15m3 17 8-11m-17 15L5 19m19 8 12-5m-18 9-3 6m8-6 3 5" /></g><circle cx="12" cy="7" r="2" /><circle cx="21" cy="4" r="2" /><circle cx="32" cy="10" r="2" /></>}
  </svg>;
}
