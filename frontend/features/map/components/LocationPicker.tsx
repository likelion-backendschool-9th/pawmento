"use client";
import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { Icon } from "@/components/Icon";
import type { MapPoint } from "@/features/map/types/MapBounds";
import type { PickedLocation } from "@/features/map/types/PickedLocation";
import { pickLocation, PLACE_NAME_MAX_LENGTH } from "@/features/map/utils/pickedLocation";
import { DEFAULT_MAP_CENTER } from "../utils/mapCanvas";
import { NaverPickerCanvas } from "./NaverPickerCanvas";
import { StaticPickerCanvas } from "./StaticPickerCanvas";
import { pinMarkup, type PinMode } from "../utils/pinMarkup";
import styles from "./LocationPicker.module.css";

type Props = {
  mode: PinMode;
  initial: PickedLocation | null;
  onConfirm: (location: PickedLocation) => void;
  onClear: () => void;
  onClose: () => void;
};

// 가운데에 고정된 핀 모양. 지도 키가 있든 없든 같다.
const CENTER_PIN = (mode: PinMode) =>
  pinMarkup(mode).replace(/width="\d+" height="\d+"/, 'width="52" height="76"');

export function LocationPicker({
  mode,
  initial,
  onConfirm,
  onClear,
  onClose,
}: Props) {
  const clientId = process.env.NEXT_PUBLIC_NAVER_MAP_CLIENT_ID ?? "";
  const start: MapPoint = initial ?? DEFAULT_MAP_CENTER;
  const [center, setCenter] = useState<MapPoint>(start);
  const [name, setName] = useState(initial?.placeName ?? "");
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
          <button
            type="button"
            className={styles.roundButton}
            onClick={onClose}
            aria-label="돌아가기"
          >
            <Icon name="chevron" size={20} />
          </button>
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
              <StaticPickerCanvas center={start} onCenterChange={setCenter} />
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
            <p className={styles.eyebrow}>OUR MEMORY MAP</p>
            <h3>이 장소를 기록에 남길게요</h3>
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
            <p className={styles.helper}>
              <Icon name="pin" size={14} />
              비워 두면 저장할 때 주소로 채워 드려요.
            </p>
            <p className={styles.helper}>
              <Icon name="lock" size={14} />
              위치는 나만 볼 수 있어요. 가족에게도 보이지 않아요.
            </p>
            {error && (
              <p className="error" role="alert">
                {error}
              </p>
            )}
            <button type="button" className="button primary" onClick={confirm}>
              이 위치로 붙이기
            </button>
            <button type="button" className={styles.skip} onClick={onClear}>
              위치 없이 저장하기
            </button>
          </section>
        </div>
      </div>
    </dialog>,
    document.body,
  );
}
