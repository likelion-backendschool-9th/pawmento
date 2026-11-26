"use client";
import { useEffect, useId, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { Icon } from "@/components/Icon";
import type { WalkOutcome, WalkPoint } from "../types/Walk";
import { formatDistance, formatDuration, pathDistance } from "../utils/walk";
import { NaverWalkCanvas } from "./NaverWalkCanvas";
import { StaticWalkCanvas } from "./StaticWalkCanvas";
import s from "./WalkResultDialog.module.css";

type Props = {
  points: WalkPoint[];
  seconds: number;
  outcome: WalkOutcome;
  clientId: string;
  mode: "ACTIVE" | "GARDEN";
  onClose: () => void;
};

export function WalkResultDialog({ points, seconds, outcome, clientId, mode, onClose }: Props) {
  const dialog = useRef<HTMLDialogElement>(null);
  const title = useId();
  const [mapFailed, setMapFailed] = useState(false);
  const distance = formatDistance(pathDistance(points));
  const partial = outcome.status === "INTERRUPTED" || !outcome.complete;
  const canvas = { points, current: points.at(-1) ?? null };
  useEffect(() => {
    const element = dialog.current;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    element?.showModal();
    return () => { element?.close(); document.body.style.overflow = previousOverflow; };
  }, []);
  return createPortal(<dialog ref={dialog} className={s.dialog} data-mode={mode} aria-labelledby={title} onCancel={(event) => { event.preventDefault(); onClose(); }} onClick={(event) => { if (event.target === event.currentTarget) { const rect = event.currentTarget.getBoundingClientRect(); if (event.clientX < rect.left || event.clientX > rect.right || event.clientY < rect.top || event.clientY > rect.bottom) onClose(); } }}>
    <header className={s.header}>
      <div><p>함께 걸은 발자국</p><h2 id={title}>오늘의 산책</h2></div>
      <button type="button" className={s.close} aria-label="산책 결과 닫기" onClick={onClose} autoFocus><Icon name="close" size={21} /></button>
    </header>
    <div className={s.map}>
      {points.length > 0 ? clientId && !mapFailed ? <NaverWalkCanvas {...canvas} fitRoute clientId={clientId} onUnavailable={() => setMapFailed(true)} /> : <StaticWalkCanvas {...canvas} /> : <div className={s.empty}><Icon name="route" size={32} /><p>기록된 위치가 없어요</p><span>다음 산책에는 위치 권한을 확인해 주세요.</span></div>}
      {points.length > 0 && (!clientId || mapFailed) && <span className={s.mapLabel}>예시 지도 위의 기록 경로</span>}
    </div>
    <dl className={s.metrics}>
      <div><dt><Icon name="route" size={17} />이동 거리</dt><dd>{distance.value}<span>{distance.unit}</span></dd></div>
      <div><dt><Icon name="clock" size={17} />이동 시간</dt><dd>{formatDuration(seconds)}</dd></div>
    </dl>
    {partial && <p className={s.warning} role="status">기기에서 기록한 경로예요. 일부 경로는 서버에 저장되지 않았을 수 있어요.</p>}
    <button className={s.confirm} type="button" onClick={onClose}>확인</button>
  </dialog>, document.body);
}
