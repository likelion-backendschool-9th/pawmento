"use client";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useApp } from "@/app/AppProvider";
import type { WalkDayList } from "@/map/application/walkPorts";
import { datesEndingAt, seoulDate, shiftDate } from "@/map/domain/WalkRecord";

const WEEK = 7;

// 한 주(7일)의 산책을 읽어 주간 막대와 선택한 날의 목록에 쓴다.
export function useWalkHistory(petId: string) {
  const { dependencies } = useApp();
  const [today] = useState(() => seoulDate(Date.now()));
  const [weekEnd, setWeekEnd] = useState(today);
  const [selected, setSelected] = useState(today);
  const [days, setDays] = useState<Record<string, WalkDayList>>({});
  const [failed, setFailed] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [attempt, setAttempt] = useState(0);
  const dates = useMemo(() => datesEndingAt(weekEnd, WEEK), [weekEnd]);

  useEffect(() => {
    let cancelled = false;
    // 읽기 시작을 한 박자 미뤄 효과 안에서 바로 상태를 바꾸지 않는다.
    const timer = setTimeout(() => {
      setLoading(true);
      setError("");
      dependencies.walkHistory
        .listDays(petId, dates)
        .then((result) => {
          if (cancelled) return;
          setDays((current) => ({ ...current, ...result.byDate }));
          setFailed(result.failed);
        })
        .catch((cause) => {
          if (!cancelled)
            setError(cause instanceof Error ? cause.message : "산책 기록을 불러오지 못했어요.");
        })
        .finally(() => {
          if (!cancelled) setLoading(false);
        });
    }, 0);
    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [dependencies, petId, dates, attempt]);

  const moveWeek = useCallback(
    (direction: -1 | 1) => {
      const next = shiftDate(weekEnd, direction * WEEK);
      const end = next > today ? today : next;
      setWeekEnd(end);
      setSelected(end);
    },
    [weekEnd, today],
  );

  const current = days[selected];
  return {
    today,
    dates,
    days,
    selected,
    select: setSelected,
    walks: current?.walks ?? [],
    sample: Object.values(days).some((day) => day.sample),
    // 선택한 날을 아직 못 읽었거나 읽는 중이다
    pending: loading && !current,
    failedSelected: failed.includes(selected),
    error,
    canGoNext: weekEnd < today,
    moveWeek,
    retry: () => setAttempt((count) => count + 1),
  };
}
