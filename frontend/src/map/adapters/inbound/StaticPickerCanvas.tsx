"use client";
import { useEffect, useRef, useState } from "react";
import type { MapPoint } from "@/map/domain/MapBounds";
import styles from "./LocationPicker.module.css";

// 지도 키가 없거나 네이버 지도를 불러오지 못할 때 쓰는 예시 지도. 끌어서 움직이면 가운데 좌표가 따라 바뀐다.
// 확대 수준 16 근처에서 한 화면 점(px)이 가리키는 각도다(서울 위도 기준).
const DEGREES_PER_PIXEL = { latitude: 1.7e-5, longitude: 2.15e-5 };

export function StaticPickerCanvas({
  center,
  onCenterChange,
}: {
  center: MapPoint;
  onCenterChange: (point: MapPoint) => void;
}) {
  const [offset, setOffset] = useState({ x: 0, y: 0 });
  const drag = useRef<{ x: number; y: number } | null>(null);
  const origin = useRef(center);

  useEffect(() => {
    onCenterChange(origin.current);
    // 처음 한 번 처음 위치를 알린다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  function move(next: { x: number; y: number }) {
    setOffset(next);
    onCenterChange({
      // 지도를 오른쪽·아래로 끌면 가운데는 서쪽·북쪽으로 간다
      latitude: origin.current.latitude + next.y * DEGREES_PER_PIXEL.latitude,
      longitude:
        origin.current.longitude - next.x * DEGREES_PER_PIXEL.longitude,
    });
  }

  return (
    <div
      className={`${styles.canvas} ${styles.staticMap}`}
      role="application"
      aria-label="위치를 고를 예시 지도(끌어서 움직여요)"
      tabIndex={0}
      onPointerDown={(event) => {
        event.currentTarget.setPointerCapture(event.pointerId);
        drag.current = { x: event.clientX - offset.x, y: event.clientY - offset.y };
      }}
      onPointerMove={(event) => {
        if (!drag.current) return;
        move({ x: event.clientX - drag.current.x, y: event.clientY - drag.current.y });
      }}
      onPointerUp={() => {
        drag.current = null;
      }}
      onPointerCancel={() => {
        drag.current = null;
      }}
      onKeyDown={(event) => {
        const step = 40;
        const delta: Record<string, [number, number]> = {
          ArrowLeft: [step, 0],
          ArrowRight: [-step, 0],
          ArrowUp: [0, step],
          ArrowDown: [0, -step],
        };
        const next = delta[event.key];
        if (!next) return;
        event.preventDefault();
        move({ x: offset.x + next[0], y: offset.y + next[1] });
      }}
    >
      {/* 끌면 이 층이 같이 움직인다 */}
      <div
        className={styles.layer}
        style={{ transform: `translate(${offset.x}px, ${offset.y}px)` }}
        aria-hidden="true"
      >
        <span className={styles.park} />
        <span className={styles.river} />
        <span className={styles.avenue} />
      </div>
    </div>
  );
}
