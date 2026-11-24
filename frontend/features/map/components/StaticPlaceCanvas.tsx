"use client";
import { useEffect } from "react";
import type { MapBounds } from "@/features/map/types/MapBounds";
import { toViewportRatio } from "@/features/map/utils/mapBounds";
import { PLACE_STYLE, placeMarkup } from "../utils/placeMarkup";
import type { PlaceCanvasProps } from "../utils/placeCanvas";
import { StaticMapBackdrop } from "./StaticMapBackdrop";
import mapStyles from "./MapScreen.module.css";
import styles from "./PlacesScreen.module.css";

// 지도 키가 없거나 네이버 지도를 불러오지 못할 때 쓰는 예시 지도다. 움직이지 않고 정해진 영역만 보여 준다.
const SPAN = { latitude: 0.0075, longitude: 0.014 };

function boundsAround(center: PlaceCanvasProps["center"]): MapBounds {
  return {
    minLatitude: center.latitude - SPAN.latitude / 2,
    maxLatitude: center.latitude + SPAN.latitude / 2,
    minLongitude: center.longitude - SPAN.longitude / 2,
    maxLongitude: center.longitude + SPAN.longitude / 2,
  };
}

export function StaticPlaceCanvas({
  center,
  places,
  selectedId,
  onSelect,
  onBoundsChange,
}: PlaceCanvasProps) {
  const bounds = boundsAround(center);
  useEffect(() => {
    onBoundsChange(boundsAround(center));
    // 영역은 중심에서만 정해진다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [center.latitude, center.longitude]);

  return (
    <div
      className={mapStyles.canvas + " " + mapStyles.staticMap}
      onClick={() => onSelect(null)}
      role="group"
      aria-label="예시 지도"
    >
      <StaticMapBackdrop />
      {places.map((place) => {
        const { x, y } = toViewportRatio(bounds, place);
        const selected = place.id === selectedId;
        return (
          <button
            key={place.id}
            type="button"
            className={styles.marker}
            style={{
              left: `${x * 100}%`,
              top: `${y * 100}%`,
              zIndex: selected ? 2 : 1,
            }}
            aria-label={`${place.name}, ${PLACE_STYLE[place.category].label}`}
            aria-pressed={selected}
            onClick={(event) => {
              event.stopPropagation();
              onSelect(place.id);
            }}
            dangerouslySetInnerHTML={{
              __html: placeMarkup(place.category, selected),
            }}
          />
        );
      })}
    </div>
  );
}
