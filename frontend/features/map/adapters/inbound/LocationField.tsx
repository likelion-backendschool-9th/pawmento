"use client";
import { useRef, useState } from "react";
import { Icon } from "@/components/Icon";
import type { PickedLocation } from "@/features/map/domain/PickedLocation";
import { LocationPicker } from "./LocationPicker";
import type { PinMode } from "./pinMarkup";
import styles from "./LocationPicker.module.css";

// 기록 쓰기 화면에 들어가는 "장소" 항목. 고른 위치를 부모가 들고 있다가 일기를 저장할 때 함께 보낸다.
export function LocationField({
  mode,
  value,
  onChange,
}: {
  mode: PinMode;
  value: PickedLocation | null;
  onChange: (value: PickedLocation | null) => void;
}) {
  const [open, setOpen] = useState(false);
  const opener = useRef<HTMLButtonElement>(null);

  function close() {
    setOpen(false);
    // 닫은 뒤 처음 누른 버튼으로 돌아간다
    setTimeout(() => opener.current?.focus(), 0);
  }

  return (
    <div className={styles.field}>
      <span className={styles.fieldLabel}>장소 (선택)</span>
      {value ? (
        <div className={styles.picked}>
          <span className={styles.pickedIcon}>
            <Icon name="pin" size={18} />
          </span>
          <span className={styles.pickedText}>
            <strong>{value.placeName ?? "지도에서 고른 위치"}</strong>
            <small>
              {value.latitude.toFixed(5)}, {value.longitude.toFixed(5)}
            </small>
          </span>
          <button
            ref={opener}
            type="button"
            className="text-link"
            onClick={() => setOpen(true)}
          >
            바꾸기
          </button>
          <button
            type="button"
            className="text-link"
            onClick={() => onChange(null)}
          >
            지우기
          </button>
        </div>
      ) : (
        <button
          ref={opener}
          type="button"
          className={styles.attach}
          onClick={() => setOpen(true)}
        >
          <Icon name="pin" size={18} />
          지도에서 위치 붙이기
        </button>
      )}
      <small className={styles.fieldNote}>
        위치는 나만 볼 수 있어요. 데모에서는 고른 위치가 저장되지 않고 화면만
        확인할 수 있어요.
      </small>
      {open && (
        <LocationPicker
          mode={mode}
          initial={value}
          onConfirm={(location) => {
            onChange(location);
            close();
          }}
          onClear={() => {
            onChange(null);
            close();
          }}
          onClose={close}
        />
      )}
    </div>
  );
}
