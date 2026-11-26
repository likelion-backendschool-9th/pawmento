"use client";
import { useCallback, useEffect, useRef, useState } from "react";
import { useApp } from "@/app/AppProvider";
import { Icon } from "@/components/Icon";
import { PageHeading } from "@/features/shell/components/WorkspaceUi";
import type { MapBounds, MapPoint } from "@/features/map/types/MapBounds";
import {
  PLACE_CATEGORIES,
  type Place,
  type PlaceCategory,
  type PlaceList,
} from "@/features/map/types/Place";
import { DEFAULT_MAP_CENTER } from "../utils/mapCanvas";
import { countByCategory, toggleCategory } from "../utils/placeCounts";
import {
  dataAsOfLabel,
  open24hLabel,
  openingLine,
  phoneHref,
  sourceLine,
} from "../utils/placeCard";
import { PlaceAddressResolver } from "../utils/placeAddress";
import type { PlaceFocus } from "../utils/placeCanvas";
import { keepSelectedId, searchSelectionAfterTap, withPinned } from "../utils/placeSearchSelection";
import { PLACE_STYLE, placeMarkup } from "../utils/placeMarkup";
import { useMyLocation } from "../hooks/useMyLocation";
import { DEFAULT_NEARBY_RADIUS } from "../utils/nearby";
import { LocationNotice } from "./LocationNotice";
import { MyLocationButton } from "./MyLocationButton";
import { NaverPlaceCanvas } from "./NaverPlaceCanvas";
import { StaticPlaceCanvas } from "./StaticPlaceCanvas";
import { PlaceSearchPanel } from "./PlaceSearchPanel";
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
  // 주소 복사 결과. 어느 장소에서 한 것인지 함께 두어, 다른 장소를 고르거나 닫으면 처음 모습으로 돌아간다
  const [copyResult, setCopyResult] = useState<{
    id: string;
    status: "done" | "failed";
  } | null>(null);
  const [attempt, setAttempt] = useState(0);
  const request = useRef(0);
  // 장소 검색 화면(#195). 열 때의 지도 중심을 거리 계산에 쓴다. 닫으면 처음 누른 검색창으로 초점이 돌아간다.
  const [searchOpen, setSearchOpen] = useState(false);
  const [searchCenter, setSearchCenter] = useState<MapPoint | null>(null);
  const searchBar = useRef<HTMLButtonElement>(null);
  // 검색 결과로 고른 장소와 그때의 검색어(#195-S11). 고른 장소는 영역 조회 결과에 없어도(영역 밖, 종류 칩이 꺼짐) 지도에 그리고 카드를 연다.
  // 선택을 풀거나(카드 닫기, 지도의 빈 곳, 다른 장소, ✕) 하면 함께 지운다.
  const [pinned, setPinned] = useState<Place | null>(null);
  const [searchText, setSearchText] = useState("");
  const [focus, setFocus] = useState<PlaceFocus | null>(null);
  // 새 영역을 조회한 응답이 늦게 와도 검색으로 고른 장소의 선택은 풀지 않는다
  const pinnedId = useRef<string | null>(null);
  useEffect(() => {
    pinnedId.current = pinned?.id ?? null;
  });
  // 내 위치를 읽으면 지도를 그 위치로 옮기고(500m 반경이 보이게) 이전 선택과 검색어를 푼다(#226-S2)
  const my = useMyLocation((fix) => {
    setPinned(null);
    setSearchText("");
    setSelectedId(null);
    setFocus({ point: fix.point, token: Date.now(), fitRadiusMeters: DEFAULT_NEARBY_RADIUS });
  });
  function openSearch() {
    setSearchCenter(
      bounds
        ? {
            latitude: (bounds.minLatitude + bounds.maxLatitude) / 2,
            longitude: (bounds.minLongitude + bounds.maxLongitude) / 2,
          }
        : null,
    );
    setSearchOpen(true);
  }
  function closeSearch() {
    setSearchOpen(false);
    setTimeout(() => searchBar.current?.focus(), 0);
  }
  // 결과를 누르면 검색 화면을 닫고, 그 장소로 지도를 옮기고, 마커를 고르고 카드를 연다. 검색창에는 검색어가 남는다(#195-S11)
  function chooseResult(place: Place, text: string) {
    setSearchOpen(false);
    setPinned(place);
    setSearchText(text);
    setSelectedId(place.id);
    setFocus({ point: { latitude: place.latitude, longitude: place.longitude }, token: Date.now() });
  }
  // 지도의 마커·빈 곳을 눌러 선택이 바뀌면, 검색으로 고른 장소가 아닌 한 검색어를 지운다.
  // 아직 실행되지 않은 이동 요청(지도 SDK가 늦게 뜬 경우)도 함께 지워, 풀어 버린 장소로 지도가 가지 않게 한다
  function selectOnMap(id: string | null) {
    if (searchSelectionAfterTap(id, pinned?.id ?? null) === "clear") {
      setPinned(null);
      setSearchText("");
      setFocus(null);
    }
    setSelectedId(id);
  }
  // 검색창의 ✕: 선택과 검색어를 함께 푼다. 종류 칩과 지도는 그대로다
  function clearSearch() {
    setPinned(null);
    setSearchText("");
    setFocus(null);
    setSelectedId(null);
    setTimeout(() => searchBar.current?.focus(), 0);
  }

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
            keepSelectedId(
              list.places.map((place) => place.id),
              id,
              pinnedId.current,
            ),
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
  // 지도에는 조회 결과에 검색으로 고른 장소를 더해 그린다
  const canvasPlaces = withPinned(places, pinned);
  const selected = canvasPlaces.find((place) => place.id === selectedId) ?? null;
  const copy = copyResult?.id === selectedId ? copyResult.status : "idle";
  useEffect(() => {
    if (copyResult === null) return;
    const timer = setTimeout(() => setCopyResult(null), 1800);
    return () => clearTimeout(timer);
  }, [copyResult]);
  const copyAddress = async (id: string, text: string) => {
    try {
      await navigator.clipboard.writeText(text);
      setCopyResult({ id, status: "done" });
    } catch {
      setCopyResult({ id, status: "failed" });
    }
  };
  // 가려진 주소는 카드를 열 때 좌표로 찾은 주소로 바꿔 보인다(#239). 못 찾으면 원래 주소 그대로다.
  const [, notifyAddress] = useState(0);
  // 찾은 주소(found)와 찾는 중(inFlight)을 들고 있는 상태 객체라서 한 번만 만든다. useMemo는 성능 최적화로만 보장되어
  // 다시 만들어질 수 있고, 그러면 이미 찾은 주소가 사라져 같은 카드에서 서버를 또 부른다(#239-S4).
  // dependencies는 앱을 시작할 때 한 번 만들어지는 값이라 처음 것을 쓴다. 바뀌어야 한다면 이 화면을 key로 새로 연다.
  const [addresses] = useState(
    () =>
      new PlaceAddressResolver(
        (placeId) => dependencies.places.resolveAddress(placeId),
        () => notifyAddress((count) => count + 1),
      ),
  );
  useEffect(() => {
    if (selected) addresses.request(selected);
  }, [selected, addresses]);
  const shownAddress = selected ? addresses.addressOf(selected) : null;
  const callHref = selected ? phoneHref(selected.phone) : null;
  const open24h = selected ? open24hLabel(selected) : null;
  const opening = selected ? openingLine(selected) : null;
  const asOf = dataAsOfLabel(result?.dataAsOf ?? null);
  const counts = countByCategory(places);
  const cardRef = useRef<HTMLElement>(null);
  // 모든 화면 크기에서 모바일 카드가 지도 아래를 덮는다.
  // 브라우저 너비 대신 실제 카드 높이를 전달해 PC에서도 마커가 가려지지 않게 한다.
  const coveredBottom = useCallback(() => cardRef.current?.offsetHeight ?? 0, []);
  const canvasProps = {
    center: DEFAULT_MAP_CENTER,
    places: canvasPlaces,
    selectedId,
    onSelect: selectOnMap,
    onBoundsChange,
    coveredBottom,
    focus,
    me: my.state.fix,
  };
  const nothingChosen = categories.length === 0;
  // 내 위치를 보이는 동안 가운데의 안내(오류, 종류를 모두 끔, 장소 없음)는 아래로 내려 내 위치 점을 가리지 않는다(#226-S2)
  const lowMessage = my.state.fix ? styles.messageLow : "";

  return (
    <div className={styles.page}>
      <div className={styles.heading}>
        <PageHeading
          eyebrow="PLACES"
          title="지도"
          body="내 주변의 동물병원·동물미용·동물보호센터를 지도에서 찾아보세요."
        />
      </div>
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

        <div className={styles.searchBar}>
          <button
            ref={searchBar}
            type="button"
            className={styles.searchOpen}
            aria-haspopup="dialog"
            onClick={openSearch}
          >
            <Icon name="search" size={18} />
            <span className={searchText ? styles.searchValue : undefined}>
              {searchText || "병원·미용·보호센터 이름이나 주소 검색"}
            </span>
          </button>
          {searchText && (
            <button
              type="button"
              className={styles.searchClear}
              aria-label="검색 지우기"
              onClick={clearSearch}
            >
              <Icon name="close" size={16} />
            </button>
          )}
        </div>

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

        {/* 카드가 열려 있으면 카드가 지도 아래를 덮으므로 버튼은 숨긴다 */}
        {!selected && (
          <MyLocationButton
            reading={my.state.status === "reading"}
            located={my.state.fix !== null}
            supported={my.supported}
            onPress={() => void my.press()}
          />
        )}
        <LocationNotice
          status={my.state.status}
          onContinue={() => void my.continueIntro()}
          onDismiss={my.dismiss}
          onRetry={() => void my.press()}
        />

        {result?.truncated && !error && (
          <p className={`${mapStyles.notice} ${styles.noticeLow}`} role="status">
            장소가 많아서 일부만 보여요. 지도를 확대해 보세요.
          </p>
        )}

        {error && (
          <div className={`${mapStyles.message} ${lowMessage}`} role="alert">
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

        {nothingChosen && !error && !pinned && (
          <div className={`${mapStyles.message} ${lowMessage}`}>
            <strong>보고 싶은 종류를 골라 주세요</strong>
            <p>위쪽 칩을 눌러 병원·미용·보호센터를 켤 수 있어요.</p>
          </div>
        )}

        {/* 검색으로 고른 장소가 지도에 그려져 있으면(조회 결과는 비어도) "장소가 없어요"를 보이지 않는다 */}
        {!nothingChosen && result && !error && canvasPlaces.length === 0 && !loading && (
          <div className={`${mapStyles.message} ${lowMessage}`}>
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

        {!selected && !error && (result?.sample || asOf) && (
          <p className={styles.pill}>
            {result?.sample ? "개발용 예시 장소예요. 실제 장소가 아니에요." : asOf}
          </p>
        )}

        {selected && (
          <aside
            ref={cardRef}
            className={`${mapStyles.sheet} ${styles.card}`}
            aria-label="선택한 장소"
          >
            <div className={mapStyles.grab} aria-hidden="true" />
            <button
              type="button"
              className={styles.close}
              aria-label="장소 카드 닫기"
              onClick={() => selectOnMap(null)}
            >
              <Icon name="close" size={18} />
            </button>
            <div className={styles.tags}>
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
              {open24h && <span className={styles.badge24}>{open24h}</span>}
            </div>
            <h2>{selected.name}</h2>
            <p className={styles.address}>
              <Icon name="pin" size={16} />
              {shownAddress ?? "주소 정보 없음"}
            </p>
            {(callHref || shownAddress) && (
              <div className={styles.actions}>
                {callHref && (
                  <a className={`button primary ${styles.call}`} href={callHref}>
                    <Icon name="phone" size={18} />
                    전화 걸기
                  </a>
                )}
                {shownAddress && (
                  <button
                    type="button"
                    className={`button secondary ${styles.copy}`}
                    onClick={() => void copyAddress(selected.id, shownAddress)}
                  >
                    <Icon name={copy === "done" ? "check" : "copy"} size={18} />
                    {copy === "done"
                      ? "복사했어요"
                      : copy === "failed"
                        ? "복사하지 못했어요"
                        : "주소 복사"}
                  </button>
                )}
              </div>
            )}
            {(opening || selected.phone) && (
              <ul className={styles.facts}>
                {opening && (
                  <li>
                    <Icon name="clock" size={19} />
                    {opening}
                  </li>
                )}
                {selected.phone && callHref && (
                  <li>
                    <Icon name="phone" size={19} />
                    <span className={styles.number}>{selected.phone}</span>
                  </li>
                )}
              </ul>
            )}
            <p className={styles.source}>
              {sourceLine(selected.category, result?.dataAsOf ?? null)}
            </p>
            <span className="visually-hidden" role="status">
              {copy === "done" ? "주소를 복사했어요" : ""}
            </span>
          </aside>
        )}
      </div>
      {searchOpen && (
        <PlaceSearchPanel
          center={searchCenter}
          initialText={searchText}
          onSelect={chooseResult}
          onClose={closeSearch}
        />
      )}
    </div>
  );
}
