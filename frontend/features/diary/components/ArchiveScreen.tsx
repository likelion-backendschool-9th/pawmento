"use client";
import { PetDescription } from "@/features/pet/components/PetDescription";
import { journalItems } from "@/features/diary/utils/journalItems";

import Link from "next/link";
import { CollectionHeading } from "@/components/CollectionHeading";
import { ArchiveNavigation } from "@/components/ArchiveNavigation";
import { useRouter } from "next/navigation";
import { useSearchParams } from "next/navigation";
import { useRef, useState } from "react";
import calendarStyles from "./Calendar.module.css";
import { calendarMonth, shiftCalendarMonth, swipeMonthDirection } from "../utils/calendarMonth";
import { useEffect } from "react";
import { useApp } from "@/app/AppProvider";
import { Icon } from "@/components/Icon";
import { today } from "@/lib/date";
import { formatDate } from "@/lib/date";
import { validateDate } from "@/lib/date";
import type { JournalItem } from "../types/JournalItem";
import type { Pet } from "../../pet/types/Pet";
import styles from "../../shell/components/Workspace.module.css";
import { JournalCard, EmptyState } from "../../shell/components/WorkspaceUi";
import { messageOf } from "../../shell/utils/presentation";

export function Calendar({
  pet,
  compact = false,
  selected,
  onSelect,
}: {
  pet: Pet;
  compact?: boolean;
  selected?: string;
  onSelect?: (date: string) => void;
}) {
  const app = useApp();
  const router = useRouter();
  const current = today();
  const [month, setMonth] = useState((selected ?? current).slice(0, 7));
  const { year, month: num, first, days } = calendarMonth(month);
  const gesture = useRef<{ id: number; x: number; y: number } | null>(null);
  const dragged = useRef(false);
  const records = journalItems(app.state).filter(
    (record) => record.petId === pet.id && record.type !== "QUESTION_ANSWER",
  );
  function shift(delta: number) {
    setMonth(value => shiftCalendarMonth(value, delta));
  }
  return (
    <div
      className={`${styles.calendar} ${calendarStyles.calendar} ${compact ? `${styles.compactCalendar} ${calendarStyles.compact}` : ""}`}
    >
      <div className={`${styles.calendarHeader} ${calendarStyles.header}`}>
        <label className={calendarStyles.monthLabel}>
          <span className={calendarStyles.monthText} aria-live="polite">{year}년 {num}월</span>
          <Icon name="calendar" size={16} />
          <input className={calendarStyles.monthInput} type="month" value={month} min="0001-01" max="9999-12"
            aria-label="보고 있는 연월 선택"
            onClick={event => {
              const input = event.currentTarget;
              try { input.showPicker?.(); } catch { input.focus(); }
            }}
            onChange={event => { if (/^\d{4}-(0[1-9]|1[0-2])$/.test(event.target.value)) setMonth(event.target.value); }} />
        </label>
        <button type="button" className={calendarStyles.todayButton}
          aria-pressed={selected === current && month === current.slice(0, 7)}
          onClick={() => {
            setMonth(current.slice(0, 7));
            if (selected === current) return;
            if (compact) router.push(`/pets/${pet.id}/calendar?date=${current}`);
            else onSelect?.(current);
          }} aria-label="오늘 날짜로 이동">오늘</button>
      </div>
      <div className={`${styles.calendarGrid} ${calendarStyles.grid}`} tabIndex={0} role="group" aria-label="달력 날짜 · 좌우로 넘겨 월 이동"
        onKeyDown={event => {
          if (event.target !== event.currentTarget) return;
          if (event.key === "ArrowLeft" || event.key === "ArrowRight") { event.preventDefault(); shift(event.key === "ArrowRight" ? 1 : -1); }
        }}
        onPointerDown={event => {
          if (!event.isPrimary || event.button !== 0) return;
          gesture.current = { id: event.pointerId, x: event.clientX, y: event.clientY };
          dragged.current = false;
        }}
        onPointerMove={event => {
          const start = gesture.current;
          if (!start || start.id !== event.pointerId) return;
          const dx = event.clientX - start.x, dy = event.clientY - start.y;
          if (Math.abs(dx) > 12 && Math.abs(dx) > Math.abs(dy) * 1.25) {
            dragged.current = true;
            event.currentTarget.setPointerCapture(event.pointerId);
          }
        }}
        onPointerUp={event => {
          const start = gesture.current;
          gesture.current = null;
          if (!start || start.id !== event.pointerId) return;
          const direction = swipeMonthDirection(event.clientX - start.x, event.clientY - start.y);
          if (direction) { dragged.current = true; shift(direction); }
        }}
        onPointerCancel={() => { gesture.current = null; }}
        onClickCapture={event => {
          if (dragged.current && event.detail > 0) { event.preventDefault(); event.stopPropagation(); dragged.current = false; }
        }}>

        {["일", "월", "화", "수", "목", "금", "토"].map((day) => (
          <span className={styles.weekday} key={day}>
            {day}
          </span>
        ))}
        {Array.from({ length: first }, (_, i) => (
          <span key={`blank-${i}`} aria-hidden="true" />
        ))}
        {Array.from({ length: days }, (_, i) => {
          const day = String(i + 1).padStart(2, "0");
          const date = `${month}-${day}`;
          const count = records.filter(
            (record) => record.recordedDate === date,
          ).length;
          const href = `/pets/${pet.id}/calendar?date=${date}`;
          const contents = (
            <>
              <span>{i + 1}</span>
              {count > 0 && <span className={calendarStyles.mark} aria-hidden="true"><Icon name="paw" size={22} /></span>}
            </>
          );
          const cls = `${styles.calendarDay} ${calendarStyles.day} ${date === selected ? styles.selectedDay : ""}`;
          return compact ? (
            <Link
              className={cls}
              key={date}
              href={href}
              aria-label={`${formatDate(date)} 기록 ${count}개`}
            >
              {contents}
            </Link>
          ) : (
            <button
              className={cls}
              key={date}
              onClick={() => onSelect?.(date)}
              aria-label={`${formatDate(date)} 기록 ${count}개`}
              aria-pressed={date === selected}
            >
              {contents}
            </button>
          );
        })}
      </div>
    </div>
  );
}

export function ArchiveScreen({
  pet,
  calendar = false,
}: {
  pet: Pet;
  calendar?: boolean;
}) {
  const app = useApp();
  const search = useSearchParams();
  const router = useRouter();
  const requestedDate = search.get("date") || today();
  let date = requestedDate;
  let invalidDate = false;
  try {
    validateDate(date);
  } catch {
    date = today();
    invalidDate = true;
  }
  const [limit, setLimit] = useState(9);
  const [result, setResult] = useState<{
    records: JournalItem[];
    total: number;
    hasMore: boolean;
  }>({ records: [], total: 0, hasMore: false });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    let active = true;
    app.dependencies.workspace.diary
      .list(app.account!.id, pet.id, {
        date: calendar ? date : undefined,
        limit,
      })
      .then((value) => {
        if (active) {
          setResult({
            ...value,
            records: value.entries.map((d) => ({
              ...d,
              kind: "DIARY" as const,
            })),
          });
          setError("");
        }
      })
      .catch((cause) => {
        if (active) setError(messageOf(cause));
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [
    app.dependencies,
    app.account,
    app.state,
    pet.id,
    calendar,
    date,
    limit,
    retry,
  ]);
  return (
    <>
      <CollectionHeading
        title={calendar ? "캘린더" : "다이어리"}
        body={calendar ? "함께한 순간을 날짜마다 간직해요." : <PetDescription name={pet.name} suffix="와 함께한 하루,">한 편씩 모았어요.</PetDescription>}
      >
        <Link className="button primary" href={`/pets/${pet.id}/diary/new`}>
          <Icon name="plus" size={17} />
          기록 남기기
        </Link>
      </CollectionHeading>
      {invalidDate && (
        <p className="error" role="alert">
          날짜 주소가 올바르지 않아 오늘의 기록을 보여드려요.
        </p>
      )}
      <ArchiveNavigation
        petId={pet.id}
        active={calendar ? "calendar" : "diary"}
      />
      <div className={calendar ? styles.calendarArchive : ""}>
        {calendar && (
          <section className={styles.fullCalendar}>
            <Calendar
              key={date.slice(0, 7)}
              pet={pet}
              selected={date}
              onSelect={(value) => {
                setLoading(true);
                router.replace(`/pets/${pet.id}/calendar?date=${value}`, {
                  scroll: false,
                });
              }}
            />
          </section>
        )}
        <section aria-busy={loading}>
          {calendar && (
            <h2 className={styles.dayHeading}>{formatDate(date)}의 이야기</h2>
          )}
          {error ? (
            <div className="error" role="alert">
              {error}
              <button
                onClick={() => {
                  setLoading(true);
                  setRetry((value) => value + 1);
                }}
                className="button secondary"
              >
                다시 시도
              </button>
            </div>
          ) : loading && !result.records.length ? (
            <p role="status" className={styles.empty}>
              기억을 펼치는 중…
            </p>
          ) : result.records.length ? (
            <div
              className={`${styles.recordGrid} ${calendar ? styles.calendarRecords : ""}`}
            >
              {result.records.map((record) => (
                <JournalCard key={record.id} record={record} />
              ))}
            </div>
          ) : (
            <EmptyState
              title="아직 기록이 없는 날이에요"
              body="작은 순간 하나를 남겨볼까요?"
              href={`/pets/${pet.id}/diary/new${calendar ? `?date=${date}` : ""}`}
              action="이날의 기록 남기기"
            />
          )}
          {result.hasMore && !error && (
            <div className={styles.loadMore}>
              <button
                className="button secondary"
                disabled={loading}
                onClick={() => {
                  setLoading(true);
                  setLimit((value) => value + 9);
                }}
              >
                {loading ? "불러오는 중…" : "이전 이야기 더 보기"}
                <Icon name="down" size={16} />
              </button>
            </div>
          )}
          {result.total > 0 && !result.hasMore && (
            <p className={styles.endNote}>
              <Icon name="leaf" size={16} />
              여기까지, 우리가 쌓아온 이야기예요.
            </p>
          )}
        </section>
      </div>
    </>
  );
}
