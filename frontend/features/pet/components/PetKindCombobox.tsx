"use client";
import { useEffect, useId, useRef, useState } from "react";
import { Icon } from "@/components/Icon";
import { COMPANION_KINDS } from "../types/companionKinds";
import { searchCompanionKinds } from "../utils/companionSearch";
import s from "./PetKindCombobox.module.css";

type Kind = (typeof COMPANION_KINDS)[number];
export function PetKindCombobox({ value, onChange, onPendingChange }: { value: Kind["id"] | null; onChange: (value: Kind) => void; onPendingChange: (pending: boolean) => void }) {
  const current = COMPANION_KINDS.find((kind) => kind.id === value);
  const [query, setQuery] = useState<string>(current?.label ?? "");
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(0);
  const [edited, setEdited] = useState(false);
  const id = useId();
  const listId = useId();
  const input = useRef<HTMLInputElement>(null);
  const popup = useRef<HTMLDivElement>(null);
  const results = searchCompanionKinds(edited ? query : "");
  useEffect(() => {
    const list = popup.current;
    const option = list?.querySelectorAll<HTMLElement>('[role="option"]')[active];
    if (!open || !list || !option) return;
    if (option.offsetTop < list.scrollTop) list.scrollTop = option.offsetTop;
    else if (option.offsetTop + option.offsetHeight > list.scrollTop + list.clientHeight) list.scrollTop = option.offsetTop + option.offsetHeight - list.clientHeight;
  }, [active, open, query]);
  function choose(kind: Kind) {
    setQuery(kind.label); setEdited(false); setOpen(false); onChange(kind); onPendingChange(false); input.current?.focus();
  }
  return <div className={s.field} onBlur={(event) => { if (!event.currentTarget.contains(event.relatedTarget)) setOpen(false); }}>
    <label htmlFor={id}>어떤 친구인가요?</label>
    <div className={s.control}>
      <input ref={input} id={id} role="combobox" aria-expanded={open} aria-controls={listId} aria-autocomplete="list" aria-activedescendant={open && results[active] ? `${listId}-${results[active].id}` : undefined} value={query} placeholder="강아지, 고양이, 토끼…" autoComplete="off" required onClick={() => setOpen(true)} onFocus={() => { setOpen(true); setActive(0); }} onChange={(event) => { const next = event.target.value; setQuery(next); setEdited(true); setOpen(true); setActive(0); onPendingChange(!current || next !== current.label); }} onKeyDown={(event) => {
        if (event.key === "ArrowDown" || event.key === "ArrowUp") { event.preventDefault(); setOpen(true); const delta = event.key === "ArrowDown" ? 1 : -1; setActive((index) => open ? Math.max(0, Math.min(results.length - 1, index + delta)) : 0); }
        else if (event.key === "Enter" && open && results[active]) { event.preventDefault(); choose(results[active]); }
        else if (event.key === "Escape") { event.preventDefault(); event.stopPropagation(); setOpen(false); }
      }} />
      <button type="button" aria-label="동물 종류 목록" aria-expanded={open} onPointerDown={(event) => event.preventDefault()} onClick={() => { input.current?.focus(); setOpen(!open); setActive(0); }}><Icon name="chevron" size={17} /></button>
    </div>
    {open && <div ref={popup} className={s.popup}>
      <ul id={listId} role="listbox" aria-label="동물 종류 추천">
        {results.map((kind, index) => <li key={kind.id}><button type="button" id={`${listId}-${kind.id}`} role="option" aria-selected={value === kind.id} tabIndex={-1} data-active={index === active} onPointerDown={(event) => event.preventDefault()} onClick={() => choose(kind)}><span>{kind.label}</span>{value === kind.id && <Icon name="check" size={16} />}</button></li>)}
      </ul>
      {results.length === 0 && <p role="status">맞는 친구가 없으면 ‘그 외’를 선택해 주세요.</p>}
    </div>}
  </div>;
}
