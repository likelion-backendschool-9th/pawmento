"use client";
import { useCallback, useEffect, useRef, useState } from "react";
import { useApp } from "@/app/AppProvider";
import type { WalkRecorder } from "@/map/application/WalkRecorder";
import {
  elapsedSeconds,
  FLUSH_EVERY_POINTS,
  SAMPLE_INTERVAL_MS,
  type Walk,
  type WalkPoint,
} from "@/map/domain/Walk";

export type WalkPhase = "checking" | "idle" | "starting" | "walking" | "ending" | "ended";
export type LocationState = "waiting" | "ok" | "denied" | "unsupported";

// 산책 한 번의 흐름(시작 → 위치 모으기 → 서버로 보내기 → 끝내기)을 화면에 이어 주는 훅이다.
export function useWalkSession(petId: string) {
  const { dependencies } = useApp();
  const walks = dependencies.walk;
  const [phase, setPhase] = useState<WalkPhase>("checking");
  const [walk, setWalk] = useState<Walk | null>(null);
  const [points, setPoints] = useState<WalkPoint[]>([]);
  const [now, setNow] = useState(() => Date.now());
  const [location, setLocation] = useState<LocationState>("waiting");
  const [error, setError] = useState("");
  const [unsent, setUnsent] = useState(false); // 서버로 못 보낸 좌표가 있다
  const [closed, setClosed] = useState(false); // 서버가 이미 닫은 산책이다
  const recorder = useRef<WalkRecorder | null>(null);
  const latest = useRef<{ latitude: number; longitude: number } | null>(null);
  const lastSampleAt = useRef(0);

  const begin = useCallback((next: Walk, active: WalkRecorder) => {
    recorder.current = active;
    lastSampleAt.current = 0;
    setWalk(next);
    setPoints([...active.points]);
    setNow(Date.now());
    setPhase("walking");
  }, []);

  // 새로고침 전에 걷던 산책이 있으면 이어 간다
  useEffect(() => {
    // 브라우저 저장소는 화면이 뜬 뒤에만 읽을 수 있어, 한 박자 뒤에 읽는다.
    const timer = setTimeout(() => {
      const resumed = walks.resume(petId);
      if (resumed) begin(resumed.walk, resumed.recorder);
      else setPhase("idle");
    }, 0);
    return () => clearTimeout(timer);
  }, [walks, petId, begin]);

  const send = useCallback(async () => {
    const active = recorder.current;
    if (!active) return;
    try {
      await active.flush();
      setUnsent(false);
    } catch (cause) {
      setUnsent(true);
      if (cause instanceof Error && "code" in cause && cause.code === "WALK_CLOSED") setClosed(true);
    }
  }, []);

  // 위치 보기: 걷는 동안만 켠다
  const walking = phase === "walking";
  useEffect(() => {
    if (!walking) return;
    if (!("geolocation" in navigator)) return;
    const id = navigator.geolocation.watchPosition(
      (position) => {
        latest.current = {
          latitude: position.coords.latitude,
          longitude: position.coords.longitude,
        };
        setLocation("ok");
      },
      (failure) => setLocation(failure.code === failure.PERMISSION_DENIED ? "denied" : "waiting"),
      { enableHighAccuracy: true, maximumAge: 5000, timeout: 20000 },
    );
    return () => navigator.geolocation.clearWatch(id);
  }, [walking]);

  // 1초마다 시간을 갱신하고, 15초마다 지금 위치를 한 점으로 모은다
  useEffect(() => {
    if (!walking || !walk) return;
    const timer = setInterval(() => {
      const current = Date.now();
      setNow(current);
      const active = recorder.current;
      const position = latest.current;
      if (!active || !position || current - lastSampleAt.current < SAMPLE_INTERVAL_MS) return;
      const added = active.record({
        ...position,
        recordedAt: new Date(current).toISOString(),
      });
      lastSampleAt.current = current;
      if (!added) return;
      setPoints([...active.points]);
      walks.remember(walk, active);
      if (active.pendingCount >= FLUSH_EVERY_POINTS) void send();
    }, 1000);
    return () => clearInterval(timer);
  }, [walking, walk, walks, send]);

  // 걷는 동안 화면이 꺼지지 않게 한다(지원하는 브라우저에서만)
  useEffect(() => {
    if (!walking) return;
    let sentinel: { release(): Promise<void> } | null = null;
    const request = () => {
      const wakeLock = (navigator as Navigator & {
        wakeLock?: { request(type: "screen"): Promise<{ release(): Promise<void> }> };
      }).wakeLock;
      wakeLock?.request("screen").then((lock) => (sentinel = lock)).catch(() => {});
    };
    const onVisible = () => {
      if (document.visibilityState === "visible") request();
    };
    request();
    document.addEventListener("visibilitychange", onVisible);
    return () => {
      document.removeEventListener("visibilitychange", onVisible);
      void sentinel?.release().catch(() => {});
    };
  }, [walking]);

  const start = useCallback(async () => {
    setError("");
    setPhase("starting");
    try {
      const started = await walks.start(petId);
      setClosed(false);
      setUnsent(false);
      latest.current = null;
      setLocation("waiting");
      begin(started.walk, started.recorder);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "산책을 시작하지 못했어요.");
      setPhase("idle");
    }
  }, [walks, petId, begin]);

  const finish = useCallback(
    async (options: { flush: boolean } = { flush: true }) => {
      if (!walk || !recorder.current) return;
      setError("");
      setPhase("ending");
      try {
        await walks.end(walk, recorder.current, options);
        setPhase("ended");
      } catch (cause) {
        setUnsent(true);
        setError(
          cause instanceof Error ? cause.message : "산책을 끝내지 못했어요.",
        );
        setPhase("walking");
      }
    },
    [walk, walks],
  );

  // 서버가 이미 닫은 산책은 이어 갈 수 없으니 기록을 비운다
  const leave = useCallback(() => {
    walks.forget(petId);
  }, [walks, petId]);

  const supported = typeof navigator === "undefined" || "geolocation" in navigator;
  const seconds = walk ? elapsedSeconds(walk.startedAt, now) : 0;
  return { phase, walk, points, seconds, location: supported ? location : "unsupported" as LocationState, error, unsent, closed, start, finish, leave };
}
