"use client";
import { journalItems } from "@/features/diary/utils/journalItems";
import { PolaroidDiaryList } from "./PolaroidDiaryList";

import Link from "next/link";
import { CollectionHeading } from "@/components/CollectionHeading";
import { ArchiveNavigation } from "@/components/ArchiveNavigation";
import { useRouter } from "next/navigation";
import { useSearchParams } from "next/navigation";
import { useState } from "react";
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
  const current = today();
  const [month, setMonth] = useState((selected ?? current).slice(0, 7));
  const [year, num] = month.split("-").map(Number);
  const first = new Date(Date.UTC(year, num - 1, 1)).getUTCDay();
  const days = new Date(Date.UTC(year, num, 0)).getUTCDate();
  const records = journalItems(app.state).filter(
    (record) => record.petId === pet.id && record.type !== "QUESTION_ANSWER",
  );
  function shift(delta: number) {
    const next = new Date(Date.UTC(year, num - 1 + delta, 1));
    setMonth(next.toISOString().slice(0, 7));
  }
  return (
    <div
      className={`${styles.calendar} ${compact ? styles.compactCalendar : ""}`}
    >
      <div className={styles.calendarHeader}>
        <strong>
          {year}년 {num}월
        </strong>
        <div>
          <button
            className="icon-button"
            onClick={() => shift(-1)}
            aria-label="이전 달"
          >
            ‹
          </button>
          <button
            className="icon-button"
            onClick={() => setMonth(current.slice(0, 7))}
            aria-label="이번 달로 이동"
          >
            오늘
          </button>
          <button
            className="icon-button"
            onClick={() => shift(1)}
            aria-label="다음 달"
          >
            ›
          </button>
        </div>
      </div>
      <div className={styles.calendarGrid}>
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
              {count > 0 && <i />}
            </>
          );
          const cls = `${styles.calendarDay} ${date === current ? styles.today : ""} ${date === selected ? styles.selectedDay : ""}`;
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
      <p className={styles.calendarLegend}>
        <i /> 이야기를 남긴 날
      </p>
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
        title={calendar ? "다이어리 달력" : "다이어리"}
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
