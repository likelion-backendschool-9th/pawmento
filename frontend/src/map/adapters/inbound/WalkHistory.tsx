"use client";
import Link from "next/link";
import { useEffect, useState } from "react";
import { Icon } from "@/shared/ui/Icon";
import {
  dayOfMonth,
  formatHourMinute,
  formatMinutes,
  formatMonthDay,
  formatTimeRange,
  formatYearMonth,
  walkDistanceText,
  walkEndedAt,
  walkMinutes,
  weekdayLabel,
  type WalkRecord,
} from "@/map/domain/WalkRecord";
import { NaverWalkRoutesCanvas } from "./NaverWalkRoutesCanvas";
import { StaticWalkRoutesCanvas } from "./StaticWalkRoutesCanvas";
import { useWalkHistory } from "./useWalkHistory";
import styles from "./WalkHistory.module.css";

type Props = { pet: { id: string; name: string; mode: string } };

const STATUS_LABEL: Record<WalkRecord["status"], string> = {
  ENDED: "끝냄",
  INTERRUPTED: "중단됨",
  IN_PROGRESS: "산책 중",
};

function summary(walk: WalkRecord): string {
  const base = `${formatMinutes(walkMinutes(walk))} · ${walkDistanceText(walk)}`;
  return walk.status === "INTERRUPTED"
    ? `${base} · 마지막 기록 ${formatHourMinute(walkEndedAt(walk))}`
    : base;
}

// "산책 길" 보기: 날짜를 고르면 그날의 산책 경로를 지도에 그리고 아래에 목록으로 보여 준다.
export function WalkHistory({ pet }: Props) {
  const clientId = process.env.NEXT_PUBLIC_NAVER_MAP_CLIENT_ID ?? "";
  const [naverFailed, setNaverFailed] = useState(false);
  const history = useWalkHistory(pet.id);
  const { walks, selected } = history;
  const [pickedId, setPickedId] = useState<string | null>(null);
  // 날짜를 바꾸면 선택을 첫 산책으로 되돌린다. 고른 산책이 그날에 없으면 첫 산책을 쓴다.
  const selectedWalk = walks.find((walk) => walk.walkId === pickedId) ?? walks[0] ?? null;
  const selectedId = selectedWalk?.walkId ?? null;
  useEffect(() => {
    const timer = setTimeout(() => setPickedId(null), 0);
    return () => clearTimeout(timer);
  }, [selected]);

  const canvasProps = { walks, selectedId, onSelect: setPickedId };
  const first = history.dates[0];
  const last = history.dates[history.dates.length - 1];

  return (
    <div className={styles.stage} data-mode={pet.mode} aria-busy={history.pending}>
      {clientId && !naverFailed ? (
        <NaverWalkRoutesCanvas {...canvasProps} clientId={clientId} onUnavailable={() => setNaverFailed(true)} />
      ) : (
        <StaticWalkRoutesCanvas {...canvasProps} />
      )}

      <section className={styles.week} aria-label="날짜 선택">
        <div className={styles.weekHead}>
          <span>{formatYearMonth(last)}</span>
          <span className={styles.weekNav}>
            <button
              type="button"
              className={styles.navButton + " " + styles.flip}
              aria-label={`이전 주 (${formatMonthDay(first)}부터)`}
              onClick={() => history.moveWeek(-1)}
            >
              <Icon name="chevron" size={18} />
            </button>
            <button
              type="button"
              className={styles.navButton}
              aria-label="다음 주"
              disabled={!history.canGoNext}
              onClick={() => history.moveWeek(1)}
            >
              <Icon name="chevron" size={18} />
            </button>
          </span>
        </div>
        <ol className={styles.days}>
          {history.dates.map((date) => {
            const count = history.days[date]?.walks.length ?? 0;
            return (
              <li key={date} className={count > 0 ? styles.hasWalk : undefined}>
                <button
                  type="button"
                  className={styles.day}
                  aria-pressed={date === selected}
                  aria-label={`${formatMonthDay(date)} ${weekdayLabel(date)}요일${count > 0 ? `, 산책 ${count}번` : ""}`}
                  onClick={() => history.select(date)}
                >
                  <small>{weekdayLabel(date)}</small>
                  <b>{dayOfMonth(date)}</b>
                  <span className={styles.dot} aria-hidden="true" />
                </button>
              </li>
            );
          })}
        </ol>
      </section>

      <aside className={styles.sheet} aria-label={`${formatMonthDay(selected)}의 산책`}>
        <h2>
          {formatMonthDay(selected)}의 산책
          {walks.length > 0 && <small>{walks.length}번</small>}
        </h2>
        {history.error && (
          <p className={styles.error} role="alert">
            {history.error}{" "}
            <button type="button" className="button" onClick={history.retry}>다시 불러오기</button>
          </p>
        )}
        {!history.error && history.failedSelected && (
          <p className={styles.error} role="alert">
            이 날의 산책을 불러오지 못했어요.{" "}
            <button type="button" className="button" onClick={history.retry}>다시 불러오기</button>
          </p>
        )}
        {history.pending && <p className={styles.status} role="status">불러오는 중이에요…</p>}
        {!history.pending && !history.error && !history.failedSelected && walks.length === 0 && (
          <>
            <p className={styles.empty}>
              이 날은 {pet.name}와 함께한 산책 기록이 없어요.
            </p>
            <Link className="button primary" href={`/pets/${pet.id}/map/walk`}>
              <Icon name="route" size={17} />
              산책 시작
            </Link>
          </>
        )}
        {walks.length > 0 && (
          <ul className={styles.list}>
            {walks.map((walk) => (
              <li key={walk.walkId}>
                <button
                  type="button"
                  className={styles.item + (walk.status === "INTERRUPTED" ? " " + styles.paused : "")}
                  aria-pressed={walk.walkId === selectedId}
                  onClick={() => setPickedId(walk.walkId)}
                >
                  <span className={styles.badgeIcon}>
                    <Icon name={walk.status === "INTERRUPTED" ? "clock" : "route"} size={22} />
                  </span>
                  <span className={styles.times}>
                    <strong>{formatTimeRange(walk)}</strong>
                    <span>{summary(walk)}</span>
                  </span>
                  <span className={styles.badge}>{STATUS_LABEL[walk.status]}</span>
                </button>
              </li>
            ))}
          </ul>
        )}
        {history.sample && (
          <p className={styles.note}>
            데모 계정에서는 예시 산책을 보여 드려요. 로그인하면 내 산책 기록이 나타나요.
          </p>
        )}
      </aside>
    </div>
  );
}
