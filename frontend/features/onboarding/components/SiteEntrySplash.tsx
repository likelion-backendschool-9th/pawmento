"use client";
import { useEffect, useState, type ReactNode } from "react";
import { SPLASH_DURATION_MS } from "../content";
import { WelcomeSplash } from "./WelcomeSplash";

// 루트 레이아웃은 사이트 내부 이동 시 유지된다. 문서를 새로 열 때만 스플래시를 재생하며 완료 여부를 저장하지 않는다.
export function SiteEntrySplash({ children }: { children: ReactNode }) {
  const [finished, setFinished] = useState(false);
  useEffect(() => {
    const reduced = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    const timer = window.setTimeout(() => setFinished(true), reduced ? 150 : SPLASH_DURATION_MS);
    return () => window.clearTimeout(timer);
  }, []);
  return finished ? children : <WelcomeSplash />;
}
