"use client";
import { Icon } from "@/components/Icon";
import type { Place } from "../types/Place";
import { NEARBY_RADII, radiusLabel, type NearbyRadius } from "../utils/nearby";
import type { SheetView } from "../utils/nearbyView";
import { open24hLabel } from "../utils/placeCard";
import { PLACE_STYLE, placeMarkup } from "../utils/placeMarkup";
import styles from "./NearbySheet.module.css";

// 지도 아래쪽의 가까운 순 목록 시트(#226-S5~S9). 접고 펼 수 있고, 반경(500m·1km·3km)을 고르며, 장소를 누르면 지도가 그 장소로 옮겨 가고 카드가 열린다.
// 기준(내 위치 등)이 없을 때는 지도 화면 영역의 장소를 거리 없이 보이고 반경 선택은 없다.
export function NearbySheet({
  view,
  radius,
  showRadius,
  open,
  note,
  onToggle,
  onRadius,
  onPick,
  onRetry,
}: {
  view: SheetView;
  radius: NearbyRadius;
  // 기준이 있을 때만 반경을 고른다
  showRadius: boolean;
  open: boolean;
  // 목록 아래에 붙는 한 줄(데이터 기준일, 예시 장소 안내)
  note: string | null;
  onToggle: () => void;
  onRadius: (radius: NearbyRadius) => void;
  onPick: (place: Place) => void;
  onRetry: () => void;
}) {
  const body = view.body;
  return (
    <section
      className={styles.sheet}
      aria-label="가까운 장소 목록"
      data-open={open}
      data-nearby-sheet=""
    >
      <button
        type="button"
        className={styles.head}
        aria-expanded={open}
        onClick={onToggle}
      >
        <span className={styles.grab} aria-hidden="true" />
        <span className={styles.heading}>
          <span className={styles.title}>{view.title}</span>
          <span className={styles.summary} role="status">
            {view.summary}
          </span>
        </span>
        <span className={styles.chevron} aria-hidden="true">
          <Icon name="chevron" size={18} />
        </span>
      </button>

      {open && (
        <div className={styles.body} aria-busy={body.kind === "loading"}>
          {showRadius && (
            <div className={styles.radiiBar}>
              <div className={styles.radii} role="group" aria-label="찾을 반경">
                {NEARBY_RADII.map((item) => (
                  <button
                    key={item}
                    type="button"
                    className={styles.radius}
                    aria-pressed={item === radius}
                    onClick={() => onRadius(item)}
                  >
                    {radiusLabel(item)}
                  </button>
                ))}
              </div>
            </div>
          )}

          {body.kind === "loading" && (
            <ul className={styles.skeleton} aria-hidden="true">
              {[0, 1, 2].map((item) => (
                <li key={item}>
                  <span />
                  <span />
                </li>
              ))}
            </ul>
          )}

          {body.kind === "error" && (
            <div className={styles.message} role="alert">
              <strong>장소를 불러오지 못했어요</strong>
              <p>{body.message}</p>
              <button type="button" className={styles.action} onClick={onRetry}>
                다시 불러오기
              </button>
            </div>
          )}

          {body.kind === "nothingChosen" && (
            <div className={styles.message}>
              <strong>{body.text}</strong>
              <p>위쪽 칩을 눌러 병원·미용·보호센터를 켤 수 있어요.</p>
            </div>
          )}

          {body.kind === "widen" && (
            <div className={styles.message}>
              <strong>{body.text}</strong>
              <button
                type="button"
                className={styles.action}
                onClick={() => onRadius(body.next)}
              >
                {body.button}
              </button>
            </div>
          )}

          {body.kind === "none" && (
            <div className={styles.message}>
              <strong>{body.text}</strong>
            </div>
          )}

          {body.kind === "rows" && (
            <ul className={styles.rows} aria-label="가까운 장소">
              {body.rows.map(({ place, distance }) => {
                const open24h = open24hLabel(place);
                return (
                  <li key={place.id}>
                    <button
                      type="button"
                      className={styles.row}
                      onClick={() => onPick(place)}
                    >
                      <span
                        className={styles.kind}
                        aria-hidden="true"
                        dangerouslySetInnerHTML={{
                          __html: placeMarkup(place.category, false, 30),
                        }}
                      />
                      <span className={styles.text}>
                        <span className={styles.name}>
                          <span>{place.name}</span>
                          {open24h && (
                            <span className={styles.badge24}>{open24h}</span>
                          )}
                        </span>
                        <span className={styles.meta}>
                          {PLACE_STYLE[place.category].label} ·{" "}
                          {place.address ?? "주소 정보 없음"}
                        </span>
                      </span>
                      {distance && (
                        <span className={styles.distance}>{distance}</span>
                      )}
                    </button>
                  </li>
                );
              })}
            </ul>
          )}

          {(note || (body.kind === "rows" && body.note)) && (
            <p className={styles.note}>
              {body.kind === "rows" && body.note}
              {body.kind === "rows" && body.note && note && <br />}
              {note}
            </p>
          )}
        </div>
      )}
    </section>
  );
}
