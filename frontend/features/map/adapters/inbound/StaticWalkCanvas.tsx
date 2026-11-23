"use client";
import type { WalkCanvasProps } from "./walkCanvas";
import { fitPoints } from "@/features/map/domain/WalkRoutes";
import { StaticMapBackdrop } from "./StaticMapBackdrop";
import styles from "./WalkScreen.module.css";

// 지도 키가 없을 때 쓰는 예시 지도. 지금 위치를 가운데에 두고 지나온 길만 그린다.
export function StaticWalkCanvas({ points }: WalkCanvasProps) {
  // 걸은 길 전체(출발점~지금 위치)가 한 화면에 보이도록 맞춘다
  const view = fitPoints(points);
  const dots = view.slice(-1)[0];
  const line = view.map((p) => `${p.x.toFixed(2)},${p.y.toFixed(2)}`).join(" ");
  return (
    <div className={styles.canvas + " " + styles.staticMap} role="img" aria-label="지나온 길을 그린 예시 지도">
      <StaticMapBackdrop />
      <svg className={styles.routes} viewBox="0 0 100 100" preserveAspectRatio="xMidYMid meet" aria-hidden="true">
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
