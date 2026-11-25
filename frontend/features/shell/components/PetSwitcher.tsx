"use client";
import Link from "next/link";
import { useRef, useState } from "react";
import { Icon } from "@/components/Icon";
import { Photo } from "@/components/Photo";
import type { Pet } from "@/features/pet/types/Pet";
import s from "./PetSwitcher.module.css";

export function PetSwitcher({ pets, selectedId, switching, onSelect }: {
  pets: Pet[];
  selectedId: string;
  switching: boolean;
  onSelect: (id: string) => Promise<void>;
}) {
  const selected = pets.find(p => p.id === selectedId);
  const dialog = useRef<HTMLDialogElement>(null);
  const [open, setOpen] = useState(false);
  const modeName = (pet: Pet) => pet.mode === "GARDEN" ? "정원" : "일상";
  function close() { dialog.current?.close(); setOpen(false); }
  return (
    <div className={s.switcher}>
      <button type="button" className={s.trigger} disabled={switching}
        aria-haspopup="dialog" aria-expanded={open}
        aria-label={selected ? `보고 있는 반려동물: ${selected.name}, ${modeName(selected)} 모드. 변경하기` : "반려동물 선택하기"}
        onClick={() => { dialog.current?.showModal(); setOpen(true); }}>
        <span className={s.selectedName} title={selected?.name}>{selected?.name ?? "아이를 선택해 주세요"}</span>
        {selected && <span className={s.mode} data-garden={selected.mode === "GARDEN"}>{modeName(selected)}</span>}
        <Icon name="chevron" size={14} />
      </button>
      <dialog ref={dialog} className={s.dialog} aria-labelledby="pet-switcher-title"
        onClose={() => setOpen(false)}
        onClick={event => { if (event.target === event.currentTarget) close(); }}>
        <div className={s.heading}>
          <div><h2 id="pet-switcher-title">누구의 공간으로 갈까요?</h2><p>함께할 아이를 선택해 주세요.</p></div>
          <button type="button" className="icon-button" aria-label="반려동물 선택 닫기" onClick={close}><Icon name="close" size={18} /></button>
        </div>
        <div className={s.options} role="group" aria-label="반려동물 목록">
          {pets.map(pet => (
            <button key={pet.id} type="button" className={s.option} aria-pressed={pet.id === selectedId}
              onClick={() => { close(); if (pet.id !== selectedId) void onSelect(pet.id); }}>
              <span className={s.photo}><Photo src={pet.photoUrl} alt="" /></span>
              <span className={s.optionCopy}><strong>{pet.name}</strong><span className={s.mode} data-garden={pet.mode === "GARDEN"}>{modeName(pet)}</span></span>
              <span className={s.check} aria-hidden="true">{pet.id === selectedId && <Icon name="check" size={17} />}</span>
            </button>
          ))}
          {!pets.length && <p className={s.empty}>등록된 아이가 없어요. 반려동물 관리에서 친구를 소개해 주세요.</p>}
        </div>
        <Link href="/pets" className={s.manage} onClick={close}><Icon name="paw" size={16} /> 반려동물 관리 <Icon name="chevron" size={14} /></Link>
      </dialog>
    </div>
  );
}
