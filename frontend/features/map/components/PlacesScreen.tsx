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
import { useNearbyList } from "../hooks/useNearbyList";
import {
  DEFAULT_NEARBY_RADIUS,
  myLocationReference,
  type NearbyRadius,
  type NearbyReference,
} from "../utils/nearby";
import { areaSheetView, nearbySheetView } from "../utils/nearbyView";
import { LocationNotice } from "./LocationNotice";
import { MyLocationButton } from "./MyLocationButton";
import { NearbySheet } from "./NearbySheet";
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
  // 가까운 순 목록(#226-S5~S9). 기준은 가장 최근에 정한 지점이다(지금은 내 위치). 기준이 없으면 시트는 접힌 채 지도 영역의 장소를 거리 없이 보인다.
  // 시트를 접었는지 폈는지는 사용자가 정하기 전(null)에는 기준이 있으면 펼친 것으로 본다
  const [reference, setReference] = useState<NearbyReference | null>(null);
  const [radius, setRadius] = useState<NearbyRadius>(DEFAULT_NEARBY_RADIUS);
  const [sheetOpen, setSheetOpen] = useState<boolean | null>(null);
  // 내 위치를 읽으면 지도를 그 위치로 옮기고(500m 반경이 보이게) 이전 선택과 검색어를 풀고, 그 위치를 기준으로 목록을 펼친다(#226-S2, S9)
  const my = useMyLocation((fix) => {
    setPinned(null);
    setSearchText("");
    setSelectedId(null);
    setReference(myLocationReference(fix.point));
    setRadius(DEFAULT_NEARBY_RADIUS);
    setSheetOpen(true);
    setFocus({ point: fix.point, token: Date.now(), fitRadiusMeters: DEFAULT_NEARBY_RADIUS });
  });
  // 반경을 바꾸면 목록을 다시 조회하고 지도 확대 수준도 그 반경에 맞춘다. 지도의 마커는 반경으로 거르지 않는다(#226-S6)
  function changeRadius(next: NearbyRadius) {
    setRadius(next);
    if (reference) setFocus({ point: reference.point, token: Date.now(), fitRadiusMeters: next });
  }
  // 목록의 장소를 누르면 지도가 그 장소로 옮겨 가고 마커가 선택되며 카드가 열린다. 목록과 기준은 그대로다(#226-S8)
  function chooseNearby(place: Place) {
    listReturn.current = place.id;
    setPinned(place);
    setSearchText("");
    setSelectedId(place.id);
    setFocus({ point: { latitude: place.latitude, longitude: place.longitude }, token: Date.now() });
  }
  // 검색 화면의 "내 위치 주변 보기": 검색 화면을 닫고 "내 위치" 동작(#226-S1)을 시작한다
  function startNearby() {
    setSearchOpen(false);
    void my.press();
  }
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
    // 지도에서 다른 장소를 고르면 목록에서 열었다는 기억은 버린다(닫을 때 목록으로 초점을 돌리지 않는다)
    if (id !== null) listReturn.current = null;
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
  // 키보드로 목록에서 카드를 열면 카드의 닫기 버튼으로, 카드를 닫으면 눌렀던 목록의 장소로 초점을 이어 준다(시트는 카드가 열리는 동안 사라진다)
  const listReturn = useRef<string | null>(null);
  const closeButton = useRef<HTMLButtonElement>(null);
  const hadCard = useRef(false);
  const selectedKey = selected?.id ?? null;
  useEffect(() => {
    if (selectedKey !== null) {
      hadCard.current = true;
      if (listReturn.current !== null) closeButton.current?.focus();
      return;
    }
    if (!hadCard.current) return;
    hadCard.current = false;
    const id = listReturn.current;
    listReturn.current = null;
    if (id === null) return;
    const row = document.querySelector<HTMLElement>(`[data-nearby-place="${CSS.escape(id)}"]`);
    (row ?? document.querySelector<HTMLElement>("[data-nearby-sheet] button[aria-expanded]"))?.focus();
  }, [selectedKey]);
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
  const nearby = useNearbyList(reference?.point ?? null, radius, categories);
  // 시트에 보일 내용. 기준이 있으면 기준점을 가운데로 한 따로의 조회 결과를, 없으면 지도 영역의 조회 결과를 보인다(#226-S9).
  // 지도 가운데에 따로 띄우던 안내(장소 없음, 종류를 모두 끔)는 시트 안으로 옮겼다
  const sheetView = reference
    ? nearbySheetView(nearby.state, reference, radius, nothingChosen)
    : areaSheetView({
        places,
        loading: loading || (result === null && !error),
        error,
        truncated: Boolean(result?.truncated),
        nothingChosen,
      });
  const sheetNote = reference
    ? nearby.state.sample
      ? "개발용 예시 장소예요. 실제 장소가 아니에요."
      : dataAsOfLabel(nearby.state.dataAsOf)
    : result?.sample
      ? "개발용 예시 장소예요. 실제 장소가 아니에요."
      : asOf;

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

        {/* 지도 아래쪽 묶음: 위쪽에 "내 위치" 버튼, 아래에 가까운 순 목록 시트. 카드가 열려 있으면 카드가 지도 아래를 덮으므로 함께 숨긴다 */}
        {!selected && (
          <div className={styles.dock}>
            <div className={styles.dockRow}>
              <MyLocationButton
                reading={my.state.status === "reading"}
                located={my.state.fix !== null}
                supported={my.supported}
                onPress={() => void my.press()}
              />
            </div>
            <NearbySheet
              view={sheetView}
              radius={radius}
              showRadius={reference !== null}
              open={sheetOpen ?? reference !== null}
              note={sheetNote}
              onToggle={() => setSheetOpen(!(sheetOpen ?? reference !== null))}
              onRadius={changeRadius}
              onPick={chooseNearby}
              onRetry={reference ? nearby.retry : () => setAttempt((count) => count + 1)}
            />
          </div>
        )}
        <LocationNotice
          status={my.state.status}
          onContinue={() => void my.continueIntro()}
          onDismiss={my.dismiss}
          onRetry={() => void my.press()}
        />

        {reference && result?.truncated && !error && (
          <p className={`${mapStyles.notice} ${styles.noticeLow}`} role="status">
            장소가 많아서 일부만 보여요. 지도를 확대해 보세요.
          </p>
        )}

        {/* 지도 영역 조회 오류(#172-S18). 기준이 없으면 시트 안에 보이고, 카드가 열려 있으면 가운데 안내로 보인다.
            내 위치 기준의 시트는 주변 목록만 읽으므로, 지도의 핀이 오래된 채로 남지 않게 위쪽에 따로 알리고 다시 불러오게 한다 */}
        {error && !selected && reference && (
          <div className={styles.areaError} role="alert">
            <span>지도의 장소를 불러오지 못했어요</span>
            <button type="button" onClick={() => setAttempt((count) => count + 1)}>
              다시 불러오기
            </button>
          </div>
        )}
        {error && selected && (
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

        {selected && (
          <aside
            ref={cardRef}
            className={`${mapStyles.sheet} ${styles.card}`}
            aria-label="선택한 장소"
          >
            <div className={mapStyles.grab} aria-hidden="true" />
            <button
              ref={closeButton}
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
          onNearby={startNearby}
          onClose={closeSearch}
        />
      )}
    </div>
  );
}
