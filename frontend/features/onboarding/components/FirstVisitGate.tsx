"use client";
import { useSyncExternalStore, type ReactNode } from "react";
import { useRouter } from "next/navigation";
import { useApp } from "@/app/AppProvider";
import { createOnboardingPreference, ONBOARDING_STORAGE_KEY } from "../utils/onboardingPreference";
import { WelcomeOnboarding } from "./WelcomeOnboarding";
import s from "./WelcomeOnboarding.module.css";

const preference = createOnboardingPreference(() => typeof window === "undefined" ? null : window.localStorage);
function snapshot() { return preference.hasCompleted() ? "complete" : "first"; }
function serverSnapshot() { return "loading" as const; }
function subscribe(notify: () => void) {
  const onStorage = (event: StorageEvent) => { if (event.key === ONBOARDING_STORAGE_KEY || event.key === null) notify(); };
  window.addEventListener("storage", onStorage);
  return () => window.removeEventListener("storage", onStorage);
}

// 루트 진입에만 붙인다. 로그인·회원가입·보호 페이지의 경로/세션/등록 흐름에는 관여하지 않는다.
export function FirstVisitGate({ children }: { children: ReactNode }) {
  const { sessionReady, account } = useApp();
  const router = useRouter();
  const status = useSyncExternalStore(subscribe, snapshot, serverSnapshot);
  if (!sessionReady || status === "loading") return <main id="main" className={s.screen} aria-busy="true"><span className={s.srOnly}>화면을 준비하고 있어요.</span></main>;
  if (account || status === "complete") return children;
  return <WelcomeOnboarding onComplete={() => { preference.complete(); router.replace("/sign-in"); }} />;
}
