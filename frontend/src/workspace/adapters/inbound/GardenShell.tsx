"use client";
import Link from "next/link";
import { useEffect, useRef } from "react";
import { useApp } from "@/app/AppProvider";
import { Brand } from "@/shared/ui/Brand";
import { Icon } from "@/shared/ui/Icon";
import type { Pet } from "../../domain/models";
import s from "./GardenShell.module.css";

export function GardenShell({
  children,
  pet,
  route,
  navigation,
  open,
  setOpen,
  onSelect,
  switching,
}: {
  children: React.ReactNode;
  pet: Pet;
  route: string;
  navigation: React.ReactNode;
  open: boolean;
  setOpen: (value: boolean) => void;
  onSelect: (id: string) => Promise<void>;
  switching: boolean;
}) {
  const app = useApp();
  const dialog = useRef<HTMLDialogElement>(null);
  const base = `/pets/${pet.id}`;
  useEffect(() => {
    if (open) dialog.current?.showModal();
    else dialog.current?.close();
  }, [open]);
  const links = [
    {
      href: "/home",
      label: "정원",
      icon: "leaf",
      active: route === "/home" || route === `${base}/garden`,
    },
    {
      href: `${base}/media`,
      label: "추억 앨범",
      icon: "camera",
      active: route.startsWith(`${base}/media`),
    },
    {
      href: `${base}/garden/mailbox`,
      label: "기억의 우체통",
      icon: "mail",
      active: route.startsWith(`${base}/garden/mailbox`),
    },
    {
      href: `${base}/timeline`,
      label: "함께한 시간",
      icon: "book",
      active: route.startsWith(`${base}/timeline`),
    },
    {
      href: "/community",
      label: "기억 나누기",
      icon: "users",
      active: route.startsWith("/community"),
    },
  ];
  return (
    <div className={s.garden} data-experience="garden">
      <a className="skip-link" href="#main">
        본문으로 바로가기
      </a>
      <header className={s.header}>
        <div className={s.masthead}>
          <Brand href="/home" garden />
          <div className={s.identity}>
            <span>THE MEMORY GARDEN</span>
            <p>{pet.name}와 함께한 계절을 거닐어요.</p>
          </div>
          <div className={s.headerActions}>
            <Link href="/settings" className="icon-button" aria-label="설정">
              <Icon name="settings" size={21} />
            </Link>
            <button
              type="button"
              className={s.menuButton}
              aria-label="전체 메뉴 열기"
              aria-expanded={open}
              onClick={() => setOpen(true)}
            >
              <Icon name="menu" size={20} />
              <span>전체 메뉴</span>
            </button>
          </div>
        </div>
        <nav className={s.navigation} aria-label="정원 주요 메뉴">
          {links.map((link) => (
            <Link
              key={link.label}
              href={link.href}
              aria-current={link.active ? "page" : undefined}
            >
              <Icon name={link.icon} size={19} />
              <span>{link.label}</span>
            </Link>
          ))}
        </nav>
      </header>
      <main id="main" className={s.main}>
        {children}
      </main>
      <footer className={s.footer}>
        <Icon name="leaf" size={21} />
        <span>우리의 기억은, 언제나 여기에.</span>
        <small>
          PawMento · 브라우저 데모 · 기록은 이 브라우저에 보관됩니다.
        </small>
      </footer>
      <nav className={s.mobileNav} aria-label="모바일 정원 메뉴">
        {links.slice(0, 4).map((link) => (
          <Link
            key={link.label}
            href={link.href}
            aria-current={link.active ? "page" : undefined}
          >
            <Icon name={link.icon} size={21} />
            <span>
              {link.label === "기억의 우체통"
                ? "우체통"
                : link.label === "함께한 시간"
                  ? "시간"
                  : link.label}
            </span>
          </Link>
        ))}
        <button
          type="button"
          aria-label="전체 메뉴 열기"
          onClick={() => setOpen(true)}
        >
          <Icon name="menu" size={21} />
          <span>더보기</span>
        </button>
      </nav>
      <dialog
        ref={dialog}
        className={s.menuDialog}
        onClose={() => setOpen(false)}
        onClick={(event) => {
          if (event.target === event.currentTarget) setOpen(false);
        }}
      >
        <div className={s.menuHeading}>
          <div>
            <span>YOUR GARDEN</span>
            <h2>정원의 모든 공간</h2>
          </div>
          <button
            type="button"
            className="icon-button"
            aria-label="메뉴 닫기"
            onClick={() => setOpen(false)}
          >
            <Icon name="close" />
          </button>
        </div>
        <label className={s.petChoice}>
          함께 기록할 반려동물
          <select
            value={pet.id}
            disabled={switching}
            onChange={(event) => {
              setOpen(false);
              void onSelect(event.target.value);
            }}
          >
            {app.state.pets.map((item) => (
              <option key={item.id} value={item.id}>
                {item.name}의 이야기
              </option>
            ))}
          </select>
        </label>
        <nav className={s.allNavigation} aria-label="주요 메뉴">
          {navigation}
        </nav>
      </dialog>
    </div>
  );
}
