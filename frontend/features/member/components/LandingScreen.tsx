"use client";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { useApp } from "@/app/AppProvider";
import { Brand } from "@/components/Brand";
import { Icon } from "@/components/Icon";
import styles from "./LandingScreen.module.css";

export function LandingScreen() {
  const app = useApp();
  const router = useRouter();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  async function openDemo() {
    if (busy || !app.sessionReady) return;
    if (app.account) { router.push("/home"); return; }
    setBusy(true); setError("");
    try { await app.startDemo(); router.push("/home"); }
    catch (cause) { setError(cause instanceof Error ? cause.message : "다시 시도해 주세요."); }
    finally { setBusy(false); }
  }
  return <main id="main" className={styles.page}>
    <div className={styles.brand}><Brand /></div>
    <div className={styles.illustration} aria-hidden="true"><span className={styles.gardenMark}><Icon name="leaf" size={24} /></span></div>
    <section className={styles.copy}>
      <h1>함께한 순간을,<br /><span>오래도록.</span></h1>
      <p>지금 곁에 있는 하루도, 마음속에 남은 기억도.<br />우리만의 공간에 차곡차곡 간직해요.</p>
    </section>
    <div className={styles.actions}>
      {app.sessionReady && !busy ? <Link className={styles.primary} href={app.account ? "/home" : "/sign-in"}>{app.account ? "기록 공간으로" : "시작하기"}<Icon name="arrow" size={18} /></Link> : <button type="button" className={styles.primary} disabled>{busy ? "기록 공간을 여는 중…" : "준비하고 있어요…"}</button>}
      {!app.account && <>
        <button className={styles.demo} type="button" disabled={busy || !app.sessionReady} onClick={() => void openDemo()}>우리의 기록 둘러보기<Icon name="chevron" size={15} /></button>
      </>}
      {error && <p className={styles.error} role="alert">{error}</p>}
    </div>
  </main>;
}
