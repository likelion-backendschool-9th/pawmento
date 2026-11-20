"use client";
import type { WalkCanvasProps } from "./walkCanvas";
import { pathToView } from "./walkCanvas";
import styles from "./WalkScreen.module.css";

// 지도 키가 없을 때 쓰는 예시 지도. 지금 위치를 가운데에 두고 지나온 길만 그린다.
export function StaticWalkCanvas({ points, current }: WalkCanvasProps) {
  const view = pathToView(points, current);
  const dots = view.slice(-1)[0];
  const line = view.map((p) => `${p.x.toFixed(2)},${p.y.toFixed(2)}`).join(" ");
  return (
    <div className={styles.canvas + " " + styles.staticMap} role="img" aria-label="지나온 길을 그린 예시 지도">
      <span className={styles.park} aria-hidden="true" />
      <span className={styles.river} aria-hidden="true" />
      <svg viewBox="0 0 100 100" preserveAspectRatio="xMidYMid slice" aria-hidden="true">
        {view.length > 1 && (
          <>
            <polyline points={line} className={styles.casing} />
            <polyline points={line} className={styles.route} />
            <circle cx={view[0].x} cy={view[0].y} r="1.8" className={styles.startDot} />
          </>
        )}
        {dots && (
          <>
            <circle cx={dots.x} cy={dots.y} r="4.2" className={styles.halo} />
            <circle cx={dots.x} cy={dots.y} r="2" className={styles.here} />
          </>
        )}
      </svg>
    </div>
  );
}
