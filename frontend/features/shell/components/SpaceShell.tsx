"use client";
import Link from "next/link";
import { useEffect, useRef } from "react";
import { useApp } from "@/app/AppProvider";
import { Brand } from "@/components/Brand";
import { Icon } from "@/components/Icon";
import type { Pet } from "../../pet/types/Pet";
import s from "./SpaceShell.module.css";

export function SpaceShell({
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
  pet?: Pet;
  route: string;
  navigation: React.ReactNode;
  open: boolean;
  setOpen: (value: boolean) => void;
  onSelect: (id: string) => Promise<void>;
  switching: boolean;
}) {
  const app = useApp();
  const dialog = useRef<HTMLDialogElement>(null);
  const garden = pet?.mode === "GARDEN";
  const base = pet ? `/pets/${pet.id}` : "/pets";
  useEffect(() => {
    if (open) dialog.current?.showModal();
    else dialog.current?.close();
  }, [open]);
  const archiveActive = ["diary", "calendar", "media", "records"].some((page) =>
    route.startsWith(`${base}/${page}`),
  );
  const links = [
    {
      href: "/home",
      label: "홈",
      icon: "home",
      active: route === "/home" || route === `${base}/garden`,
    },
    {
      href: pet ? `${base}/questions` : "/pets",
      label: "오늘의 질문",
      icon: "question",
      active:
        route.startsWith(`${base}/questions`) ||
        route.startsWith(`${base}/diary`),
    },
    {
      href: pet ? `${base}/diary` : "/pets",
      label: "다이어리",
      icon: "book",
      active: archiveActive,
    },
    ...(garden
      ? [
          {
            href: `${base}/garden/mailbox`,
            label: "우체통",
            icon: "mail",
            active: route.startsWith(`${base}/garden/mailbox`),
          },
        ]
      : []),
    {
      href: "/community",
      label: "커뮤니티",
      icon: "users",
      active: route.startsWith("/community"),
    },
  ];
  return (
    <div
      className={`${s.garden} ${garden ? "" : s.daily}`}
      data-experience={garden ? "garden" : "daily"}
    >
      <a className="skip-link" href="#main">
        본문으로 바로가기
      </a>
      <header className={s.header}>
        <div className={s.masthead}>
          <Brand href="/home" garden={garden} />
          <label className={s.identity}>
            <span className="visually-hidden">보고 있는 반려동물</span>
            <Icon name={garden ? "leaf" : "paw"} size={15} />
            <select
              aria-label="보고 있는 반려동물"
              value={pet?.id ?? app.state.selectedPetId}
              disabled={switching || !app.state.pets.length}
              onChange={(event) => void onSelect(event.target.value)}
            >
              {!app.state.pets.length && (
                <option value="">반려동물을 등록해 주세요</option>
              )}
              {app.state.pets.map((item) => (
                <option key={item.id} value={item.id}>
                  {item.name} · {item.mode === "GARDEN" ? "정원" : "일상"}
                </option>
              ))}
            </select>
          </label>
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
        <nav
          className={s.navigation}
          aria-label={garden ? "정원 주요 메뉴" : "일상 주요 메뉴"}
        >
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
        <Icon name="paw" size={21} />
        <span>함께한 오늘이, 내일의 소중한 기억으로.</span>
        <small>
          PawMento · 브라우저 데모 · 기록은 이 브라우저에 보관됩니다.
        </small>
      </footer>
      <nav
        className={s.mobileNav}
        aria-label={garden ? "모바일 정원 메뉴" : "모바일 일상 메뉴"}
      >
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
            <span>YOUR SPACE</span>
            <h2>전체 메뉴</h2>
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
            value={pet?.id ?? app.state.selectedPetId}
            disabled={switching || !app.state.pets.length}
            onChange={(event) => {
              setOpen(false);
              void onSelect(event.target.value);
            }}
          >
            {!app.state.pets.length && (
              <option value="">친구를 등록해 주세요</option>
            )}
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
