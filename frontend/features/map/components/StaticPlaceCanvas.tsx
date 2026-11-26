"use client";
import { useEffect, useRef, useState } from "react";
import type { MapBounds } from "@/features/map/types/MapBounds";
import { toViewportRatio } from "@/features/map/utils/mapBounds";
import { PLACE_STYLE, placeMarkup } from "../utils/placeMarkup";
import {
  markerShiftToVisible,
  type PlaceCanvasProps,
} from "../utils/placeCanvas";
import { StaticMapBackdrop } from "./StaticMapBackdrop";
import mapStyles from "./MapScreen.module.css";
import styles from "./PlacesScreen.module.css";

// 지도 키가 없거나 네이버 지도를 불러오지 못할 때 쓰는 예시 지도다. 사용자가 움직일 수는 없고, 검색 결과를 누르면 그 지점으로 옮겨 정해진 영역만 보여 준다.
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
  coveredBottom,
  focus,
  me,
}: PlaceCanvasProps) {
  // 보이는 중심. 이동 요청(검색 결과를 눌렀을 때, #195-S11)이 오면 그 지점으로 바꾼다. 렌더링 중에 바꿔야 그린 뒤의 효과들이 새 영역을 쓴다
  const [view, setView] = useState(focus?.point ?? center);
  const [seenToken, setSeenToken] = useState<number | null>(
    focus?.token ?? null,
  );
  if (focus && focus.token !== seenToken) {
    setSeenToken(focus.token);
    setView(focus.point);
  }
  const bounds = boundsAround(view);
  const box = useRef<HTMLDivElement>(null);
  const shifter = useRef<HTMLDivElement>(null);
  useEffect(() => {
    onBoundsChange(boundsAround(view));
    // 영역은 중심에서만 정해진다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [view.latitude, view.longitude]);

  // 장소를 고르면 카드가 지도 아래를 덮는다. 네이버 지도처럼 선택한 마커가 카드 뒤에 가려지면 바탕과 마커를 함께 위로 올린다.
  // 이 지도는 움직이지 않는 그림이라 카드가 덮은 높이보다 더 올리지 않는다(올린 만큼 생기는 빈자리가 카드 뒤에 숨는다).
  useEffect(() => {
    const element = shifter.current;
    const height = box.current?.clientHeight ?? 0;
    if (!element) return;
    const place = places.find((item) => item.id === selectedId);
    const covered = coveredBottom?.() ?? 0;
    let shift = 0;
    if (place && height > 0) {
      const { y } = toViewportRatio(bounds, place);
      shift = markerShiftToVisible({ y: y * height, height, covered });
      if (shift > 0) shift = Math.min(shift, Math.max(covered - 8, 0));
    }
    element.style.transform = shift === 0 ? "" : `translateY(${-shift}px)`;
    // 장소를 고르거나 풀 때, 지도를 옮길 때만 옮긴다
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedId, view.latitude, view.longitude]);

  return (
    <div
      ref={box}
      className={mapStyles.canvas + " " + mapStyles.staticMap}
      onClick={() => onSelect(null)}
      role="group"
      aria-label="예시 지도"
    >
      <div ref={shifter} className={styles.shifter}>
        <StaticMapBackdrop />
        {me && <StaticMe mark={me} bounds={bounds} />}
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
    </div>
  );
}

// 내 위치의 점과 정확도 원(#226-S2). 원의 지름은 영역의 가로 길이(미터)에 대한 정확도의 두 배의 비율이다.
function StaticMe({
  mark,
  bounds,
}: {
  mark: NonNullable<PlaceCanvasProps["me"]>;
  bounds: MapBounds;
}) {
  const { x, y } = toViewportRatio(bounds, mark.point);
  const widthMeters =
    (bounds.maxLongitude - bounds.minLongitude) *
    111_320 *
    Math.cos((mark.point.latitude * Math.PI) / 180);
  const ring = Math.min(Math.max((mark.accuracy * 2) / widthMeters, 0.04), 1);
  const at = { left: `${x * 100}%`, top: `${y * 100}%` };
  return (
    <>
      <span
        className={styles.meRing}
        style={{ ...at, width: `${ring * 100}%` }}
        aria-hidden="true"
      />
      <span
        className={styles.meDot}
        style={at}
        role="img"
        aria-label="내 위치"
      />
    </>
  );
}
