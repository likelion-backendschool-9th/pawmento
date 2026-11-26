"use client";
import { LOCATE_LABEL } from "../utils/myLocationText";
import { LocateGlyph } from "./MyLocationArt";
import styles from "./MyLocation.module.css";

// 지도 오른쪽 아래의 "내 위치" 버튼(#226-S1). 글자 없이 조준 아이콘만 둔 원이다.
// 읽는 동안에는 아이콘의 원이 맥박처럼 깜빡이고, 한 번 읽은 뒤에는 은은한 초록 바탕과 큰 점으로 "위치가 켜져 있다"를 보인다.
// 이 기기가 위치를 지원하지 않으면 흐려지지만, 눌러서 이유를 볼 수 있다.
export function MyLocationButton({
  reading,
  located,
  supported,
  onPress,
}: {
  reading: boolean;
  located: boolean;
  supported: boolean;
  onPress: () => void;
}) {
  const label = reading ? LOCATE_LABEL.reading : LOCATE_LABEL.idle;
  return (
    <button
      type="button"
      className={styles.locate}
      aria-label={label}
      title={label}
      aria-disabled={!supported}
      aria-busy={reading}
      data-located={located}
      onClick={onPress}
    >
      <LocateGlyph located={located && !reading} />
    </button>
  );
}
