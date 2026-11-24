"use client";
import { useCallback, useEffect, useRef, useState } from "react";
import { useApp } from "@/app/AppProvider";
import { Icon } from "@/components/Icon";
import { PageHeading } from "@/features/shell/components/WorkspaceUi";
import type { MapBounds } from "@/features/map/types/MapBounds";
import {
  PLACE_CATEGORIES,
  type PlaceCategory,
  type PlaceList,
} from "@/features/map/types/Place";
import { DEFAULT_MAP_CENTER } from "../utils/mapCanvas";
import { countByCategory, toggleCategory } from "../utils/placeCounts";
import { PLACE_STYLE, placeMarkup } from "../utils/placeMarkup";
import { NaverPlaceCanvas } from "./NaverPlaceCanvas";
import { StaticPlaceCanvas } from "./StaticPlaceCanvas";
import mapStyles from "./MapScreen.module.css";
import styles from "./PlacesScreen.module.css";

const REQUEST_DELAY_MS = 250;

// 정보 지도: 지도에서 병원·미용·보호센터를 보고 종류 칩으로 켜고 끈다(#172-S16, S18).
// 지도를 옮기거나 확대·축소해서 멈춘 뒤에만 새 영역을 조회한다. 움직이는 동안에는 부르지 않는다.
export function PlacesScreen() {
  const { dependencies, state } = useApp();
  // 정원 모드의 반려동물을 보고 있으면 지도 바탕도 정원 색으로 보인다(MapScreen과 같다)
  const mode = state.pets.find((pet) => pet.id === state.selectedPetId)?.mode;
  // Client ID는 공개되는 값이다. 빌드할 때 번들에 들어간다.
  const clientId = process.env.NEXT_PUBLIC_NAVER_MAP_CLIENT_ID ?? "";
  const [naverFailed, setNaverFailed] = useState(false);
  const [bounds, setBounds] = useState<MapBounds | null>(null);
  const [categories, setCategories] = useState<PlaceCategory[]>([
    ...PLACE_CATEGORIES,
  ]);
  const [result, setResult] = useState<PlaceList | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);
  const request = useRef(0);

  useEffect(() => {
    if (!bounds) return;
    const current = ++request.current;
    const timer = setTimeout(() => {
      setLoading(true);
      setError("");
      dependencies.places
        .list(bounds, { categories })
        .then((list) => {
          if (current !== request.current) return; // 더 최근 요청이 있으면 버린다
          setResult(list);
          setSelectedId((id) =>
            list.places.some((place) => place.id === id) ? id : null,
          );
        })
        .catch((cause) => {
          if (current !== request.current) return;
          setError(
            cause instanceof Error
              ? cause.message
              : "장소를 불러오지 못했어요.",
          );
        })
        .finally(() => {
          if (current === request.current) setLoading(false);
        });
    }, REQUEST_DELAY_MS);
    return () => clearTimeout(timer);
  }, [bounds, categories, dependencies, attempt]);

  const onBoundsChange = useCallback(
    (next: MapBounds) => setBounds(next),
    [],
  );
  const places = result?.places ?? [];
  const selected = places.find((place) => place.id === selectedId) ?? null;
  const counts = countByCategory(places);
  const canvasProps = {
    center: DEFAULT_MAP_CENTER,
    places,
    selectedId,
    onSelect: setSelectedId,
    onBoundsChange,
  };
  const nothingChosen = categories.length === 0;

  return (
    <div className={styles.page}>
      <PageHeading
        eyebrow="PLACES"
        title="지도"
        body="내 주변의 동물병원·동물미용·동물보호센터를 지도에서 찾아보세요."
      />
      <div
        className={mapStyles.stage}
        data-places-stage=""
        data-mode={mode}
        aria-busy={loading}
      >
        {clientId && !naverFailed ? (
          <NaverPlaceCanvas
            {...canvasProps}
            clientId={clientId}
            onUnavailable={() => setNaverFailed(true)}
          />
        ) : (
          <StaticPlaceCanvas {...canvasProps} />
        )}

        <div className={styles.filters} role="group" aria-label="장소 종류">
          {PLACE_CATEGORIES.map((category) => {
            const on = categories.includes(category);
            return (
              <button
                key={category}
                type="button"
                className={styles.filter}
                aria-pressed={on}
                style={
                  { "--dot": PLACE_STYLE[category].color } as React.CSSProperties
                }
                onClick={() =>
                  setCategories((current) => toggleCategory(current, category))
                }
              >
                <span className={styles.dot} aria-hidden="true" />
                {PLACE_STYLE[category].label}
                {on && result && !error && <b>{counts[category]}</b>}
              </button>
            );
          })}
        </div>

        {result?.truncated && !error && (
          <p className={mapStyles.notice} role="status">
            장소가 많아서 일부만 보여요. 지도를 확대해 보세요.
          </p>
        )}

        {error && (
          <div className={mapStyles.message} role="alert">
            <strong>장소를 불러오지 못했어요</strong>
            <p>{error}</p>
            <button
              type="button"
              className="button primary"
              onClick={() => setAttempt((count) => count + 1)}
            >
              다시 불러오기
            </button>
          </div>
        )}

        {nothingChosen && !error && (
          <div className={mapStyles.message}>
            <strong>보고 싶은 종류를 골라 주세요</strong>
            <p>위쪽 칩을 눌러 병원·미용·보호센터를 켤 수 있어요.</p>
          </div>
        )}

        {!nothingChosen && result && !error && places.length === 0 && !loading && (
          <div className={mapStyles.message}>
            <span className={mapStyles.emptyArt} aria-hidden="true">
              <span className={mapStyles.emptyCircle}>
                <Icon name="map" size={34} />
              </span>
              <span className={mapStyles.emptyBadge}>
                <Icon name="search" size={16} />
              </span>
            </span>
            <strong>이 지역에는 장소가 없어요</strong>
            <p>지도를 옮기거나 다른 종류를 켜 보세요.</p>
          </div>
        )}

        {selected && (
          <aside className={mapStyles.sheet} aria-label="선택한 장소">
            <div className={mapStyles.grab} aria-hidden="true" />
            <p
              className={styles.kind}
              style={
                { "--kind": PLACE_STYLE[selected.category].color } as React.CSSProperties
              }
            >
              <span
                aria-hidden="true"
                dangerouslySetInnerHTML={{
                  __html: placeMarkup(selected.category, false, 20),
                }}
              />
              {PLACE_STYLE[selected.category].label}
            </p>
            <h2>{selected.name}</h2>
            <p className={styles.address}>
              {selected.address ?? "주소 정보 없음"}
            </p>
          </aside>
        )}
      </div>
      {result?.sample && (
        <p className={mapStyles.note}>
          개발용 예시 장소예요. 실제 장소가 아니에요.
        </p>
      )}
    </div>
  );
}
