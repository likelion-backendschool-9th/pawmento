"use client";
import { useRef, useState } from "react";
import { Icon } from "@/components/Icon";
import { ONBOARDING_SLIDES } from "../content";
import { AnimalFootprint } from "./AnimalFootprint";
import { OnboardingArtwork } from "./OnboardingArtwork";
import s from "./WelcomeOnboarding.module.css";

export function WelcomeOnboarding({ onComplete }: { onComplete: () => void }) {
  const [index, setIndex] = useState(0);
  const [leaving, setLeaving] = useState(false);
  const heading = useRef<HTMLHeadingElement>(null);
  const gesture = useRef<{ x: number; y: number } | null>(null);
  const content = ONBOARDING_SLIDES[index];
  const last = index === ONBOARDING_SLIDES.length - 1;
  function select(next: number) {
    if (next < 0 || next >= ONBOARDING_SLIDES.length || next === index) return;
    setIndex(next);
    requestAnimationFrame(() => heading.current?.focus({ preventScroll: true }));
  }
  function finish() { if (leaving) return; setLeaving(true); onComplete(); }
  return <main id="main" className={s.screen}>
    <header className={s.header}><span className={s.smallWordmark}>Paw<span>Mento</span></span><button className={s.skip} type="button" disabled={leaving} onClick={finish}>건너뛰기</button></header>
    <section className={s.carousel} aria-roledescription="슬라이드 모음" aria-label="PawMento 소개" onDragStart={(event) => event.preventDefault()} onKeyDown={(event) => {
      if (event.key === "ArrowRight" || event.key === "ArrowLeft") { event.preventDefault(); select(index + (event.key === "ArrowRight" ? 1 : -1)); }
    }} onPointerDown={(event) => { if (!event.isPrimary || event.button !== 0) return; event.currentTarget.setPointerCapture(event.pointerId); gesture.current = { x: event.clientX, y: event.clientY }; }} onPointerCancel={() => { gesture.current = null; }} onPointerUp={(event) => {
      const start = gesture.current; gesture.current = null; if (!start) return;
      const dx = event.clientX - start.x; const dy = event.clientY - start.y;
      if (Math.abs(dx) > 45 && Math.abs(dx) > Math.abs(dy) * 1.5) select(index + (dx < 0 ? 1 : -1));
    }}>
      <div key={content.id} className={s.slide} role="group" aria-roledescription="슬라이드" aria-label={`${index + 1} / ${ONBOARDING_SLIDES.length}`}>
        <OnboardingArtwork kind={content.artwork} label={content.artworkLabel} />
        <div className={s.copy}><p className={s.eyebrow}>{content.eyebrow}</p><h1 ref={heading} tabIndex={-1}>{content.title}</h1><p className={s.description}>{content.description}</p></div>
      </div>
    </section>
    <footer className={s.footer}>
      <div className={s.indicators} role="group" aria-label="소개 페이지 선택">{ONBOARDING_SLIDES.map((slide, page) => <button key={slide.id} type="button" aria-label={`소개 ${page + 1}페이지: ${slide.title.replace(/\n/g, " ")}`} aria-current={page === index ? "step" : undefined} onClick={() => select(page)}><AnimalFootprint /></button>)}</div>
      <button className={s.next} type="button" disabled={leaving} onClick={() => last ? finish() : select(index + 1)}>{leaving ? "이동하고 있어요…" : last ? "로그인하고 시작하기" : "다음"}<Icon name="arrow" size={19} /></button>
    </footer>
  </main>;
}
