"use client";
import { useCallback, useEffect, useState } from "react";
import { useApp } from "@/app/AppProvider";
import type { LifeState, Actor } from "@/features/shell/types/life";
export function useLife() {
  const app = useApp();
  const [state, setState] = useState<LifeState | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const actor: Actor = {
    id: app.account!.id,
    name: app.account!.nickname,
    isAdmin: app.account!.role === "ADMIN",
  };
  const reload = useCallback(async () => {
    try {
      setState(await app.dependencies.life.load());
      setError("");
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "불러오지 못했어요.");
    }
  }, [app.dependencies]);
  useEffect(() => {
    let active = true;
    app.dependencies.life
      .load()
      .then((value) => {
        if (active) {
          setState(value);
          setError("");
        }
      })
      .catch((cause) => {
        if (active)
          setError(
            cause instanceof Error ? cause.message : "불러오지 못했어요.",
          );
      });
    return () => {
      active = false;
    };
  }, [app.dependencies, app.state]);
  const processing = state?.postcards.some(
    (card) => card.ownerId === actor.id && card.status === "PROCESSING",
  );
  useEffect(() => {
    if (!processing) return;
    const timer = setInterval(() => void reload(), 700);
    return () => clearInterval(timer);
  }, [processing, reload]);
  async function run<T>(
    operation: () => Promise<T>,
    message?: string,
  ): Promise<T | undefined> {
    setBusy(true);
    setError("");
    try {
      const result = await operation();
      await reload();
      if (message) app.notify(message);
      return result;
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "저장에 실패했어요.");
      return undefined;
    } finally {
      setBusy(false);
    }
  }
  return { state, error, busy, actor, reload, run, app };
}
