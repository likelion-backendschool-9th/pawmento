"use client";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useApp } from "@/app/AppProvider";
import { isDemoAccount } from "@/features/member/types/account";
import type { WalkDayList } from "@/features/map/application/walkPorts";
import {
  datesEndingAt,
  followToday,
  initialNavigation,
  msUntilNextSeoulMidnight,
  seoulDate,
  shiftDate,
  type WalkDayNavigation,
} from "@/features/map/domain/WalkRecord";

const WEEK = 7;

// 한 주(7일)의 산책을 읽어 주간 막대와 선택한 날의 목록에 쓴다.
export function useWalkHistory(petId: string, initialDate?: string | null) {
  const { dependencies, account } = useApp();
  // 화면 확인용 예시 산책은 데모 계정에만 보여 준다
  const sample = isDemoAccount(account);
  const [navigation, setNavigation] = useState<WalkDayNavigation>(() =>
    initialNavigation(seoulDate(Date.now()), initialDate),
  );
  const { today, weekEnd, selected } = navigation;
  const [days, setDays] = useState<Record<string, WalkDayList>>({});
  const [failed, setFailed] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [attempt, setAttempt] = useState(0);
  const dates = useMemo(() => datesEndingAt(weekEnd, WEEK), [weekEnd]);

  // 한국 날짜가 바뀌면 "오늘"과 다음 주 상한을 갱신한다. 자정 경계와 탭으로 돌아왔을 때 다시 계산한다.
  const syncToday = useCallback(() => {
    setNavigation((current) => followToday(current, seoulDate(Date.now()), WEEK));
  }, []);
  useEffect(() => {
    let timer: ReturnType<typeof setTimeout>;
    const schedule = () => {
      timer = setTimeout(() => {
        syncToday();
        schedule();
      }, msUntilNextSeoulMidnight(Date.now()) + 500);
    };
    schedule();
    const onVisible = () => {
      if (document.visibilityState === "visible") syncToday();
    };
    document.addEventListener("visibilitychange", onVisible);
    window.addEventListener("focus", syncToday);
    return () => {
      clearTimeout(timer);
      document.removeEventListener("visibilitychange", onVisible);
      window.removeEventListener("focus", syncToday);
    };
  }, [syncToday]);

  useEffect(() => {
    let cancelled = false;
    // 읽기 시작을 한 박자 미뤄 효과 안에서 바로 상태를 바꾸지 않는다.
    const timer = setTimeout(() => {
      setLoading(true);
      setError("");
      dependencies.walkHistory
        .listDays(petId, dates, { sample })
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
  }, [dependencies, petId, dates, sample, attempt]);

  const moveWeek = useCallback((direction: -1 | 1) => {
    setNavigation((current) => {
      const next = shiftDate(current.weekEnd, direction * WEEK);
      const end = next > current.today ? current.today : next;
      return { ...current, weekEnd: end, selected: end };
    });
  }, []);
  const select = useCallback(
    (date: string) => setNavigation((current) => ({ ...current, selected: date })),
    [],
  );

  const current = days[selected];
  return {
    today,
    dates,
    days,
    selected,
    select,
    walks: current?.walks ?? [],
    sample: Object.values(days).some((day) => day.sample),
    // 선택한 날을 아직 못 읽었거나 읽는 중이다
    pending: loading && !current,
    failedSelected: failed.includes(selected),
    error,
    canGoNext: weekEnd < today,
    moveWeek,
    // 다시 불러올 때 날짜가 바뀌었는지도 함께 확인한다
    retry: () => {
      syncToday();
      setAttempt((count) => count + 1);
    },
  };
}
