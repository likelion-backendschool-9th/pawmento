"use client";
import Link from "next/link";
import { useEffect, useId, useRef, useState } from "react";
import { useApp } from "@/app/AppProvider";
import { Brand } from "@/components/Brand";
import { Icon } from "@/components/Icon";
import type { Pet } from "../../pet/types/Pet";
import { activeNavigation, appNavigation } from "../utils/navigation";
import s from "./SpaceShell.module.css";

export function SpaceShell({
  children,
  pet,
  route,
  onSelect,
  switching,
}: {
  children: React.ReactNode;
  pet?: Pet;
  route: string;
  onSelect: (id: string) => Promise<void>;
  switching: boolean;
}) {
  const app = useApp();
  const [moreOpen, setMoreOpen] = useState(false);
  const more = useRef<HTMLDialogElement>(null);
  const moreId = useId();
  useEffect(() => {
    if (moreOpen && !more.current?.open) more.current?.showModal();
    if (!moreOpen && more.current?.open) more.current.close();
  }, [moreOpen]);
  const base = pet
    ? `/pets/${pet.id}`
    : app.state.selectedPetId
      ? `/pets/${app.state.selectedPetId}`
      : null;
  const menu = [
    { label: "마이페이지", href: "/me", icon: "user" },
    { label: "반려동물 관리", href: "/pets", icon: "paw" },
    ...(base
      ? [{ label: "함께 기록하는 가족", href: `${base}/family`, icon: "users" }]
      : []),
    ...(pet?.mode === "GARDEN" && base
      ? [
          {
            label: "기억의 우체통",
            href: `${base}/garden/mailbox`,
            icon: "mail",
          },
        ]
      : []),
    { label: "나의 활동", href: "/me/activity", icon: "heart" },
    { label: "설정 · 앱 설치", href: "/settings", icon: "settings" },
    ...(app.account?.role === "ADMIN"
      ? [{ label: "운영 관리", href: "/admin/reports", icon: "shield" }]
      : []),
  ];
  const garden = pet?.mode === "GARDEN";
  const active = activeNavigation(route, pet?.id);
  const immersive = garden && active === "home";
  return (
    <div
      className={s.space}
      data-experience={garden ? "garden" : "daily"}
      data-immersive={immersive}
    >
      <a className="skip-link" href="#main">
        본문으로 바로가기
      </a>
      <header className={s.header}>
        <Brand href="/home" />
        <label className={s.identity}>
          <span className="visually-hidden">보고 있는 반려동물</span>
          <select
            aria-label="보고 있는 반려동물"
            value={pet?.id ?? app.state.selectedPetId}
            disabled={switching || !app.state.pets.length}
            onChange={(e) => void onSelect(e.target.value)}
          >
            {!app.state.pets.length && <option value="">아이 선택</option>}
            {app.state.pets.map((item) => (
              <option key={item.id} value={item.id}>
                {item.name} · {item.mode === "GARDEN" ? "정원" : "일상"}
              </option>
            ))}
          </select>
        </label>
        <button
          type="button"
          className={s.moreButton}
          aria-label="더보기"
          aria-haspopup="dialog"
          aria-expanded={moreOpen}
          aria-controls={moreId}
          onClick={() => setMoreOpen(true)}
        >
          <Icon name="menu" size={22} />
        </button>
      </header>
      <main id="main" className={s.main}>
        {children}
      </main>
      <nav className={s.bottomNav} aria-label="주요 메뉴">
        {appNavigation(pet?.id).map((link) => (
          <Link
            key={link.key}
            href={link.href}
            aria-current={active === link.key ? "page" : undefined}
          >
            <Icon name={link.icon} size={22} />
            <span>{link.label}</span>
          </Link>
        ))}
      </nav>
      <dialog
        ref={more}
        id={moreId}
        className={s.moreSheet}
        aria-labelledby={`${moreId}-title`}
        onClose={() => setMoreOpen(false)}
        onClick={(event) => {
          if (event.currentTarget === event.target) setMoreOpen(false);
        }}
      >
        <div className={s.moreHeading}>
          <div>
            <span>MY SPACE</span>
            <h2 id={`${moreId}-title`}>더보기</h2>
          </div>
          <button
            className="icon-button"
            type="button"
            aria-label="더보기 닫기"
            onClick={() => setMoreOpen(false)}
          >
            <Icon name="close" />
          </button>
        </div>
        <div className={s.moreLinks}>
          {menu.map((item) => (
            <Link
              key={item.href}
              href={item.href}
              onClick={() => setMoreOpen(false)}
            >
              <Icon name={item.icon} size={20} />
              <span>{item.label}</span>
              <Icon name="chevron" size={15} />
            </Link>
          ))}
        </div>
      </dialog>
    </div>
  );
}
