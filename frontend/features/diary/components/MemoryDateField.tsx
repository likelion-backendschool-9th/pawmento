"use client";
import { useEffect, useId, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { Icon } from "@/components/Icon";
import { today, formatDate } from "@/lib/date";
import { monthCells, moveMonth, isCalendarDate, swipeMonthDirection } from "../utils/dateSelection";
import styles from "./MemoryDateField.module.css";

type Props = { value: string; mode: "ACTIVE" | "GARDEN"; onChange: (value: string) => void };

export function MemoryDateField({ value, mode, onChange }: Props) {
  const [open, setOpen] = useState(false);
  const opener = useRef<HTMLButtonElement>(null);
  const id = useId();
  const valid = isCalendarDate(value);
  function close() {
    setOpen(false);
    requestAnimationFrame(() => opener.current?.focus());
  }
  return (
    <div className={styles.field}>
      <label htmlFor={id}>기억하고 싶은 날짜</label>
      <button id={id} ref={opener} type="button" className={styles.trigger} aria-haspopup="dialog" onClick={() => setOpen(true)}>
        <Icon name="calendar" size={19} />
        <span>{valid ? formatDate(value, { year: "numeric", month: "long", day: "numeric", weekday: "short" }) : "날짜를 선택해 주세요"}</span>
        <Icon name="chevron" size={16} />
      </button>
      {open && <DateDialog value={valid ? value : today()} mode={mode} onClose={close} onConfirm={(next) => { onChange(next); close(); }} />}
    </div>
  );
}

function DateDialog({ value, mode, onClose, onConfirm }: { value: string; mode: Props["mode"]; onClose: () => void; onConfirm: (value: string) => void }) {
  const [selected, setSelected] = useState(value);
  const [[year, month], setMonth] = useState<[number, number]>(() => [Number(value.slice(0, 4)), Number(value.slice(5, 7))]);
  const dialog = useRef<HTMLDialogElement>(null);
  const grid = useRef<HTMLDivElement>(null);
  const current = today();
  const gesture = useRef<{ x: number; y: number } | null>(null);
  const ignoreClick = useRef(false);
  useEffect(() => {
    const element = dialog.current;
    const previous = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    element?.showModal();
    return () => { document.body.style.overflow = previous; element?.close(); };
  }, []);
  function choose(next: string) {
    setSelected(next);
    setMonth([Number(next.slice(0, 4)), Number(next.slice(5, 7))]);
  }
  return createPortal(
    <dialog ref={dialog} className={styles.dialog} data-mode={mode} aria-label="기억 날짜 선택" onCancel={(event) => { event.preventDefault(); onClose(); }} onClick={(event) => {
      if (event.target !== event.currentTarget) return;
      const rect = event.currentTarget.getBoundingClientRect();
      if (event.clientX < rect.left || event.clientX > rect.right || event.clientY < rect.top || event.clientY > rect.bottom) onClose();
    }}>
      <header className={styles.header}>
        <h2>언제의 기억인가요?</h2>
      </header>
      <div className={styles.month}>
        <button type="button" className={styles.iconButton} aria-label="이전 달" disabled={year === 1 && month === 1} onClick={() => setMonth(moveMonth(year, month, -1))}><span className={styles.previous}><Icon name="chevron" size={20} /></span></button>
        <span className={styles.monthLabel} aria-live="polite">{year}년 {month}월</span>
        <button type="button" className={styles.iconButton} aria-label="다음 달" disabled={year === 9999 && month === 12} onClick={() => setMonth(moveMonth(year, month, 1))}><Icon name="chevron" size={20} /></button>
      </div>
      <div className={styles.swipeZone}
        onPointerDown={(event) => { if (event.isPrimary && event.button === 0) { gesture.current = { x: event.clientX, y: event.clientY }; ignoreClick.current = false; } }}
        onPointerMove={(event) => { const start = gesture.current; if (!start) return; const dx = event.clientX - start.x; const dy = event.clientY - start.y; if (Math.abs(dx) > 12 && Math.abs(dx) > Math.abs(dy) * 1.2) event.currentTarget.setPointerCapture(event.pointerId); }}
        onPointerUp={(event) => { const start = gesture.current; gesture.current = null; if (!start) return; const direction = swipeMonthDirection(event.clientX - start.x, event.clientY - start.y); if (direction) { ignoreClick.current = true; requestAnimationFrame(() => { ignoreClick.current = false; }); setMonth(moveMonth(year, month, direction)); } }}
        onPointerCancel={() => { gesture.current = null; }}
        onClickCapture={(event) => { if (ignoreClick.current) { event.preventDefault(); event.stopPropagation(); ignoreClick.current = false; } }}
      >
      <div className={styles.week} aria-hidden="true">{["일", "월", "화", "수", "목", "금", "토"].map((day) => <span key={day}>{day}</span>)}</div>
      <div ref={grid} className={styles.days} role="group" aria-label={`${year}년 ${month}월 날짜`}>
        {monthCells(year, month).map((day, index) => day ? <button type="button" key={day} aria-label={formatDate(day, { year: "numeric", month: "long", day: "numeric" })} aria-pressed={day === selected} aria-current={day === current ? "date" : undefined} onClick={() => choose(day)} onKeyDown={(event) => { const delta = { ArrowLeft: -1, ArrowRight: 1, ArrowUp: -7, ArrowDown: 7 }[event.key]; if (!delta) return; const buttons = Array.from(grid.current?.querySelectorAll<HTMLButtonElement>("button") ?? []); const next = buttons[buttons.indexOf(event.currentTarget) + delta]; if (next) { event.preventDefault(); next.focus(); } }}>{Number(day.slice(8))}</button> : <span key={`blank-${index}`} />)}
      </div>
      </div>
      <div className={styles.actions}><button type="button" onClick={() => onConfirm(selected)}>이 날짜로 선택</button></div>
    </dialog>, document.body,
  );
}
