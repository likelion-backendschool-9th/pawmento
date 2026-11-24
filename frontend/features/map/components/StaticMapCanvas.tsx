"use client";
import { useEffect } from "react";
import type { MapBounds } from "@/features/map/types/MapBounds";
import { toViewportRatio } from "@/features/map/utils/mapBounds";
import { pinMarkup } from "../utils/pinMarkup";
import type { MapCanvasProps } from "../utils/mapCanvas";
import { StaticMapBackdrop } from "./StaticMapBackdrop";
import styles from "./MapScreen.module.css";

// 지도 키가 없거나 네이버 지도를 불러오지 못할 때 쓰는 예시 지도다. 움직이지 않고 정해진 영역만 보여 준다.
const SPAN = { latitude: 0.0075, longitude: 0.014 };

function boundsAround(center: MapCanvasProps["center"]): MapBounds {
  return {
    minLatitude: center.latitude - SPAN.latitude / 2,
    maxLatitude: center.latitude + SPAN.latitude / 2,
    minLongitude: center.longitude - SPAN.longitude / 2,
    maxLongitude: center.longitude + SPAN.longitude / 2,
  };
}

export function StaticMapCanvas({
  center,
  pins,
  selectedId,
  mode,
  onSelect,
  onBoundsChange,
}: MapCanvasProps) {
  const bounds = boundsAround(center);
  useEffect(() => {
    onBoundsChange(boundsAround(center));
    // 영역은 중심에서만 정해진다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [center.latitude, center.longitude]);

  return (
    <div
      className={styles.canvas + " " + styles.staticMap}
      onClick={() => onSelect(null)}
      role="group"
      aria-label="예시 지도"
    >
      <StaticMapBackdrop />
      {pins.map((pin) => {
        const { x, y } = toViewportRatio(bounds, pin);
        const selected = pin.diaryId === selectedId;
        return (
          <button
            key={pin.diaryId}
            type="button"
            className={styles.pin}
            style={{
              left: `${x * 100}%`,
              top: `${y * 100}%`,
              zIndex: selected ? 2 : 1,
            }}
            aria-label={pin.placeName ?? "이름 없는 장소의 기억"}
            aria-pressed={selected}
            onClick={(event) => {
              event.stopPropagation();
              onSelect(pin.diaryId);
            }}
            dangerouslySetInnerHTML={{ __html: pinMarkup(mode, selected) }}
          />
        );
      })}
    </div>
  );
}
