"use client";
import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { useApp } from "@/app/AppProvider";
import { isDemoAccount } from "@/features/member/types/account";
import { Icon } from "@/components/Icon";
import type { MapPoint } from "@/features/map/types/MapBounds";
import type { PickedLocation } from "@/features/map/types/PickedLocation";
import { pickLocation, placeNameAfterPick, PLACE_NAME_MAX_LENGTH } from "@/features/map/utils/pickedLocation";
import { DEFAULT_MAP_CENTER } from "../utils/mapCanvas";
import { NaverPickerCanvas } from "./NaverPickerCanvas";
import { StaticPickerCanvas } from "./StaticPickerCanvas";
import { pinMarkup, type PinMode } from "../utils/pinMarkup";
import styles from "./LocationPicker.module.css";

type Props = {
  mode: PinMode;
  initial: PickedLocation | null;
  onConfirm: (location: PickedLocation) => void;
  onClose: () => void;
};

// 가운데에 고정된 핀 모양. 지도 키가 있든 없든 같다.
const CENTER_PIN = (mode: PinMode) =>
  pinMarkup(mode).replace(/width="\d+" height="\d+"/, 'width="52" height="76"');

export function LocationPicker({
  mode,
  initial,
  onConfirm,
  onClose,
}: Props) {
  const clientId = process.env.NEXT_PUBLIC_NAVER_MAP_CLIENT_ID ?? "";
  const { account, dependencies } = useApp();
  const searchLocations = dependencies.locationSearch.searchDemo;
  const sample = isDemoAccount(account);
  // 지도를 옮길 위치. 결과를 고를 때마다 새 값이라, 같은 결과를 다시 골라도 그 위치로 돌아간다
  const [start, setStart] = useState<MapPoint>(initial ?? DEFAULT_MAP_CENTER);
  const [query, setQuery] = useState("");
  const [searched, setSearched] = useState<string | null>(null);
  const results = sample && searched ? searchLocations(searched) : [];
  const [center, setCenter] = useState<MapPoint>(start);
  const [name, setName] = useState(initial?.placeName ?? "");
  // 검색 결과로 채운 장소 이름. 사용자가 직접 쓴 이름은 결과를 골라도 덮지 않는다
  const filledName = useRef<string | null>(null);
  // 결과를 고른 횟수. 예시 지도는 이 값으로 다시 그려 같은 결과를 다시 골라도 그 위치로 돌아간다
  const [picks, setPicks] = useState(0);
  const [error, setError] = useState("");
  const [naverFailed, setNaverFailed] = useState(false);
  const dialog = useRef<HTMLDialogElement>(null);

  // 브라우저의 모달 dialog(showModal)를 쓴다. 열려 있는 동안 Tab 이동이 안에서만 돌고 뒤 화면은 조작할 수 없다(inert).
  // Esc는 dialog의 cancel 이벤트로 받는다.
  useEffect(() => {
    const element = dialog.current;
    const previous = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    if (element && !element.open) element.showModal();
    return () => {
      document.body.style.overflow = previous;
      if (element?.open) element.close();
    };
  }, []);

  function selectPlace(place: ReturnType<typeof searchLocations>[number]) {
    const point = { latitude: place.latitude, longitude: place.longitude };
    setStart(point);
    setCenter(point);
    const nextName = placeNameAfterPick(name, filledName.current, place.name);
    if (nextName === place.name) filledName.current = place.name;
    setName(nextName);
    setPicks((count) => count + 1);
    setError("");
  }

  function confirm() {
    try {
      onConfirm(pickLocation(center, name));
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "위치를 확인해 주세요.");
    }
  }

  return createPortal(
    <dialog
      ref={dialog}
      className={styles.dialog}
      data-mode={mode}
      aria-label="위치 붙이기"
      onCancel={(event) => {
        // Esc: 닫는 일은 부모의 상태가 한다
        event.preventDefault();
        onClose();
      }}
    >
      <div className={styles.panel}>
        <header className={styles.bar}>
          <h2>위치 붙이기</h2>
          <button
            type="button"
            className={styles.roundButton}
            onClick={onClose}
            aria-label="닫기"
          >
            <Icon name="close" size={20} />
          </button>
        </header>

        <div className={styles.body}>
          <div className={styles.mapArea}>
            {clientId && !naverFailed ? (
              <NaverPickerCanvas
                clientId={clientId}
                center={start}
                onCenterChange={setCenter}
                onUnavailable={() => setNaverFailed(true)}
              />
            ) : (
              <StaticPickerCanvas key={picks} center={start} onCenterChange={setCenter} />
            )}
            <p className={styles.hint} role="status">
              지도를 움직여 핀을 놓아 보세요
            </p>
            <span
              className={styles.centerPin}
              aria-hidden="true"
              dangerouslySetInnerHTML={{ __html: CENTER_PIN(mode) }}
            />
          </div>

          <section className={styles.sheet}>
            <form className={styles.search} onSubmit={(event) => { event.preventDefault(); event.stopPropagation(); const text = query.trim(); setSearched(text); const first = sample ? searchLocations(text)[0] : null; if (first) selectPlace(first); }}>
              <label htmlFor="location-search">주소·장소 검색</label>
              <div className={styles.searchRow}>
                <input id="location-search" type="search" value={query} onChange={(event) => { setQuery(event.target.value); setSearched(null); }} placeholder={sample ? "주소나 장소 이름을 입력해 주세요" : "주소 검색 준비 중"} autoComplete="off" maxLength={200} />
                <button type="submit" disabled={!query.trim() || !sample} aria-label="주소 검색"><Icon name="search" size={19} /></button>
              </div>
              {sample && searched && <ul className={styles.results} aria-label="주소 검색 결과">
                {results.map((place) => <li key={place.id}><button type="button" onClick={() => { selectPlace(place); setSearched(null); setQuery(place.name); }}><Icon name="pin" size={18} /><span><strong>{place.name}</strong><small>{place.address}</small></span></button></li>)}
                {results.length === 0 && <li className={styles.empty} role="status">예시에서 찾지 못했어요. ‘서울숲’으로 검색하거나 지도에서 골라 주세요.</li>}
              </ul>}
            </form>
            <label htmlFor="place-name">장소 이름 (선택)</label>
            <input
              id="place-name"
              value={name}
              maxLength={PLACE_NAME_MAX_LENGTH}
              onChange={(event) => {
                setError("");
                setName(event.target.value);
              }}
              placeholder="예: 서울숲 반려견 놀이터"
              autoComplete="off"
            />
            {error && (
              <p className="error" role="alert">
                {error}
              </p>
            )}
            <button type="button" className="button primary" onClick={confirm}>
              이 위치로 붙이기
            </button>
          </section>
        </div>
      </div>
    </dialog>,
    document.body,
  );
}
