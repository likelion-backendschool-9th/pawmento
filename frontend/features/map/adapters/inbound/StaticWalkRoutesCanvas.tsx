"use client";
import { useMemo } from "react";
import type { WalkRecord } from "@/features/map/domain/WalkRecord";
import { fitRoutes, routeMarks } from "@/features/map/domain/WalkRoutes";
import type { WalkRoutesCanvasProps } from "./walkRoutesCanvas";
import { StaticMapBackdrop } from "./StaticMapBackdrop";
import styles from "./WalkHistory.module.css";

// 지도 키가 없을 때 쓰는 예시 지도. 그날의 산책을 한 화면에 맞춰 그린다.
export function StaticWalkRoutesCanvas({ walks, selectedId, onSelect }: WalkRoutesCanvasProps) {
  const routes = useMemo(() => fitRoutes(walks as WalkRecord[]), [walks]);
  return (
    <div className={styles.canvas + " " + styles.staticMap} onClick={() => onSelect(null)} role="group" aria-label="예시 지도">
      <StaticMapBackdrop />
      <svg className={styles.routes} viewBox="0 0 100 100" preserveAspectRatio="xMidYMid meet">
        {routes.map((route) => {
          const marks = routeMarks(route.status);
          const paused = marks.pausedTag;
          const active = selectedId === null || route.walkId === selectedId;
          const line = route.path.map((p) => `${p.x.toFixed(2)},${p.y.toFixed(2)}`).join(" ");
          const start = route.path[0];
          const end = route.path[route.path.length - 1];
          if (!start || !end) return null;
          return (
            <g
              key={route.walkId}
              className={styles.route + (active ? "" : " " + styles.faded)}
              onClick={(event) => {
                event.stopPropagation();
                onSelect(route.walkId);
              }}
            >
              <polyline points={line} className={styles.hit} />
              {!paused && <polyline points={line} className={styles.casing} />}
              <polyline points={line} className={paused ? styles.pausedLine : styles.line} />
              {paused ? (
                <>
                  <circle cx={end.x} cy={end.y} r="1.6" className={styles.pausedDot} />
                  <g transform={`translate(${end.x + 2.5} ${end.y - 2})`}>
                    <rect x="0" y="-3.4" width="9" height="5.6" rx="2.8" className={styles.tag} />
                    <text x="4.5" y="0.5" textAnchor="middle" className={styles.tagText}>중단</text>
                  </g>
                </>
              ) : (
                <circle cx={start.x} cy={start.y} r="1.7" className={styles.startDot} />
              )}
            </g>
          );
        })}
      </svg>
    </div>
  );
}
