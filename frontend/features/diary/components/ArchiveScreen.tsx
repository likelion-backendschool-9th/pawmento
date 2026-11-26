"use client";
import { RecordActionLink } from "@/components/RecordAction";
import { journalItems } from "@/features/diary/utils/journalItems";
import { PolaroidDiaryList } from "./PolaroidDiaryList";

import { CollectionHeading } from "@/components/CollectionHeading";
import { ArchiveNavigation } from "@/components/ArchiveNavigation";
import { useRouter } from "next/navigation";
import { useSearchParams } from "next/navigation";
import { useRef, useState, useSyncExternalStore } from "react";
import calendarStyles from "./Calendar.module.css";
import { calendarMonth, hasMonthPicker, isCalendarDate, isCalendarMonth, shiftCalendarMonth, swipeMonthDirection } from "../utils/calendarMonth";
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

// 데스크톱 Firefox·macOS Safari처럼 월 선택 화면이 없는 브라우저에서는 보이는 연월 입력란을 쓴다.
const noSubscribe = () => () => {};
function supportsMonthInput() {
  const input = document.createElement("input");
  input.type = "month";
  return hasMonthPicker({ inputType: input.type, vendor: navigator.vendor ?? "", maxTouchPoints: navigator.maxTouchPoints ?? 0 });
}

// 월 선택기가 없을 때 쓰는 연월 입력. 입력 중인 값은 따로 두고, Enter나 포커스를 옮길 때 검사해 반영한다.
function MonthTextInput({ value, onCommit }: { value: string; onCommit: (month: string) => void }) {
  const [draft, setDraft] = useState(value);
  const commit = () => {
    if (isCalendarMonth(draft)) onCommit(draft);
    else setDraft(value);
  };
  return (
    <input className={calendarStyles.monthTextInput} type="text" inputMode="numeric" placeholder="YYYY-MM" maxLength={7}
      aria-label="보고 있는 연월 입력 (예: 2025-02)" value={draft}
      onChange={event => setDraft(event.target.value)}
      onBlur={commit}
      onKeyDown={event => {
        if (event.key === "Enter") { event.preventDefault(); commit(); }
        else if (event.key === "Escape") setDraft(value);
      }} />
  );
}

export function Calendar({
  pet,
  selected,
  onSelect,
}: {
  pet: Pet;
  selected?: string;
  onSelect?: (date: string) => void;
}) {
  const app = useApp();
  const current = today();
  // 서버 렌더링에서는 기본 선택기가 있다고 보고, 브라우저에서 다시 확인한다
  const nativeMonth = useSyncExternalStore(noSubscribe, supportsMonthInput, () => true);
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
      className={`${styles.calendar} ${calendarStyles.calendar}`}
    >
      <div className={`${styles.calendarHeader} ${calendarStyles.header}`}>
        {nativeMonth ? (
          <label className={calendarStyles.monthLabel}>
            <span className={calendarStyles.monthText} aria-live="polite">{year}년 {num}월</span>
            <Icon name="calendar" size={16} />
            <input className={calendarStyles.monthInput} type="month" value={month} min="0001-01" max="9999-12"
              aria-label="보고 있는 연월 선택"
              onClick={event => {
                const input = event.currentTarget;
                try { input.showPicker?.(); } catch { input.focus(); }
              }}
              onChange={event => { if (isCalendarMonth(event.target.value)) setMonth(event.target.value); }} />
          </label>
        ) : (
          <div className={calendarStyles.monthLabel}>
            <span className={calendarStyles.monthText} aria-live="polite">{year}년 {num}월</span>
            {/* 다른 달로 옮기면 입력란도 그 달로 다시 채운다 */}
            <MonthTextInput key={month} value={month} onCommit={setMonth} />
          </div>
        )}
        <button type="button" className={calendarStyles.todayButton}
          aria-pressed={selected === current && month === current.slice(0, 7)}
          onClick={() => {
            setMonth(current.slice(0, 7));
            if (selected !== current) onSelect?.(current);
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
          return (
            <button
              type="button"
              className={`${styles.calendarDay} ${calendarStyles.day} ${date === selected ? styles.selectedDay : ""}`}
              key={date}
              onClick={() => onSelect?.(date)}
              aria-label={`${formatDate(date)} 기록 ${count}개`}
              aria-pressed={date === selected}
            >
              <span>{i + 1}</span>
              {count > 0 && <span className={calendarStyles.mark} aria-hidden="true"><Icon name="paw" size={22} /></span>}
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
    // 달력이 그릴 수 없는 연도(예: 0000년)도 잘못된 날짜와 같이 오늘로 연다
    if (!isCalendarDate(date)) throw Error("올바른 날짜를 선택해 주세요.");
  } catch {
    date = today();
    invalidDate = true;
  }
  const [limit, setLimit] = useState(9);
  const [result, setResult] = useState<{
    records: Extract<JournalItem, { kind: "DIARY" }>[];
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
        body={calendar ? "함께한 순간을 날짜마다 간직해요." : "우리의 일상을 기록해요."}
      >
        <RecordActionLink href={`/pets/${pet.id}/diary/new`}>기록 남기기</RecordActionLink>
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
            calendar ? (
              <div className={`${styles.recordGrid} ${styles.calendarRecords}`}>
                {result.records.map(record => <JournalCard key={record.id} record={record} />)}
              </div>
            ) : <PolaroidDiaryList records={result.records} currentAuthor={app.account ?? undefined} />
          ) : (
            <EmptyState
              title="아직 기록이 없는 날이에요"
              body="작은 순간 하나를 남겨볼까요?"
              href={`/pets/${pet.id}/diary/new${calendar ? `?date=${date}` : ""}`}
              action="이날의 기록 남기기"
              actionIcon="edit"
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

        </section>
      </div>
    </>
  );
}
