"use client";
import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { Icon } from "@/components/Icon";
import { today, formatDate } from "@/lib/date";
import { calendarMonth, isCalendarDate, shiftCalendarMonth, swipeMonthDirection } from "@/features/diary/utils/calendarMonth";
import s from "./BirthdayDateField.module.css";

export function BirthdayDateField({ value, onChange }: { value: string; onChange: (value: string) => void }) {
  const [open, setOpen] = useState(false);
  const trigger = useRef<HTMLButtonElement>(null);
  function close() { setOpen(false); requestAnimationFrame(() => trigger.current?.focus()); }
  return <>
    <button ref={trigger} className={s.trigger} type="button" aria-label="생일 날짜 선택" aria-haspopup="dialog" onClick={() => setOpen(true)}><Icon name="calendar" size={19} /><span>{isCalendarDate(value) ? formatDate(value, { year: "numeric", month: "long", day: "numeric" }) : "생일을 선택해 주세요"}</span><Icon name="chevron" size={16} /></button>
    {open && <BirthdayDialog value={value} onClose={close} onConfirm={(next) => { onChange(next); close(); }} />}
  </>;
}

function BirthdayDialog({ value, onClose, onConfirm }: { value: string; onClose: () => void; onConfirm: (value: string) => void }) {
  const max = today();
  const [selected, setSelected] = useState(isCalendarDate(value) && value <= max ? value : "");
  const [month, setMonth] = useState((isCalendarDate(value) && value <= max ? value : max).slice(0, 7));
  const { year, month: monthNumber, first, days } = calendarMonth(month);
  const maxYear = Number(max.slice(0, 4));
  const minYear = Math.min(year, maxYear - 150);
  const dialog = useRef<HTMLDialogElement>(null);
  const gesture = useRef<{ x: number; y: number } | null>(null);
  const ignoreClick = useRef(false);
  useEffect(() => {
    const previous = document.body.style.overflow;
    const element = dialog.current;
    document.body.style.overflow = "hidden"; element?.showModal();
    return () => { document.body.style.overflow = previous; element?.close(); };
  }, []);
  function move(delta: number) { setMonth((current) => { const next = shiftCalendarMonth(current, delta); return next > max.slice(0, 7) ? max.slice(0, 7) : next; }); }
  return createPortal(<dialog ref={dialog} className={s.dialog} aria-label="생일 선택" onCancel={(event) => { event.preventDefault(); onClose(); }} onClick={(event) => {
    if (event.target !== event.currentTarget) return;
    const rect = event.currentTarget.getBoundingClientRect();
    if (event.clientX < rect.left || event.clientX > rect.right || event.clientY < rect.top || event.clientY > rect.bottom) onClose();
  }}>
    <header className={s.header}><h2>언제 태어났나요?</h2></header>
    <div className={s.month}>
      <button type="button" className={s.arrow} aria-label="이전 달" disabled={month === "0001-01"} onClick={() => move(-1)}><span className={s.previous}><Icon name="chevron" size={20} /></span></button>
      <div className={s.monthLabel}><select aria-label="태어난 연도" value={year} onChange={(event) => { const next = `${event.target.value.padStart(4, "0")}-${String(monthNumber).padStart(2, "0")}`; setMonth(next > max.slice(0, 7) ? max.slice(0, 7) : next); }}>{Array.from({ length: maxYear - Math.max(1, minYear) + 1 }, (_, index) => <option key={maxYear - index} value={maxYear - index}>{maxYear - index}년</option>)}</select><span aria-live="polite">{monthNumber}월</span></div>
      <button type="button" className={s.arrow} aria-label="다음 달" disabled={month >= max.slice(0, 7)} onClick={() => move(1)}><Icon name="chevron" size={20} /></button>
    </div>
    <div className={s.swipe} onPointerDown={(event) => { if (event.isPrimary && event.button === 0) { gesture.current = { x: event.clientX, y: event.clientY }; ignoreClick.current = false; } }} onPointerMove={(event) => { const start = gesture.current; if (start && Math.abs(event.clientX - start.x) > 12 && Math.abs(event.clientX - start.x) > Math.abs(event.clientY - start.y) * 1.25) event.currentTarget.setPointerCapture(event.pointerId); }} onPointerUp={(event) => { const start = gesture.current; gesture.current = null; if (!start) return; const direction = swipeMonthDirection(event.clientX - start.x, event.clientY - start.y); if (direction) { ignoreClick.current = true; requestAnimationFrame(() => { ignoreClick.current = false; }); move(direction); } }} onPointerCancel={() => { gesture.current = null; }} onClickCapture={(event) => { if (ignoreClick.current) { event.preventDefault(); event.stopPropagation(); ignoreClick.current = false; } }}>
      <div className={s.week} aria-hidden="true">{["일", "월", "화", "수", "목", "금", "토"].map((day) => <span key={day}>{day}</span>)}</div>
      <div className={s.days} role="group" aria-label={`${year}년 ${monthNumber}월 날짜`}>
        {Array.from({ length: first }, (_, index) => <span key={`blank-${index}`} />)}
        {Array.from({ length: days }, (_, index) => { const day = `${month}-${String(index + 1).padStart(2, "0")}`; return <button key={day} type="button" disabled={day > max} aria-label={day} aria-pressed={selected === day} onClick={() => setSelected(day)}>{index + 1}</button>; })}
      </div>
    </div>
    <button type="button" className={s.confirm} disabled={!selected} onClick={() => onConfirm(selected)}>이 날짜로 선택</button>
  </dialog>, document.body);
}
