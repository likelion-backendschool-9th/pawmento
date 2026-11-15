"use client";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { useApp } from "@/app/AppProvider";
import { today } from "@/shared/date";
import { Brand } from "@/shared/ui/Brand";
import { Icon } from "@/shared/ui/Icon";
import type { Pet } from "../../domain/models";
import styles from "./AppShell.module.css";
import { messageOf } from "./presentation";
export function Shell({
  children,
  pet,
  route,
}: {
  children: React.ReactNode;
  pet?: Pet;
  route: string;
}) {
  const app = useApp();
  const router = useRouter();
  const [switching, setSwitching] = useState(false);
  const [open, setOpen] = useState(false);
  const p = pet ? `/pets/${pet.id}` : "/pets";
  const todayDiary = app.state.records.find(
    (record) =>
      record.petId === pet?.id &&
      record.type === "DIARY" &&
      record.recordedDate === today(),
  );
  const groups = [
    {
      title: "나의 기록",
      links: [
        { href: "/home", label: "대시보드", icon: "home" },
        {
          href: pet
            ? pet.accessRole === "VIEWER"
              ? `${p}/questions`
              : todayDiary
                ? `${p}/diary/${todayDiary.id}`
                : `${p}/diary/new`
            : "/pets",
          label: "질문 다이어리",
          icon: "edit",
        },
        {
          href: pet ? `${p}/calendar` : "/pets",
          label: "기록 달력",
          icon: "calendar",
        },
        {
          href: pet ? `${p}/timeline` : "/pets",
          label: "타임라인",
          icon: "book",
        },
        {
          href: pet ? `${p}/media` : "/pets",
          label: "미디어 앨범",
          icon: "camera",
        },
        {
          href: pet ? `${p}/questions` : "/pets",
          label: "질문과 답변 이력",
          icon: "question",
        },
      ],
    },
    {
      title: "함께하는 공간",
      links: [
        {
          href: pet ? `${p}/garden` : "/pets",
          label: "기억의 정원",
          icon: "leaf",
        },
        ...(pet?.mode === "GARDEN"
          ? [
              {
                href: `${p}/garden/mailbox`,
                label: "기억의 우체통",
                icon: "mail",
              },
            ]
          : []),
        { href: "/community", label: "커뮤니티", icon: "users" },
        {
          href: pet ? `${p}/family` : "/pets",
          label: "가족 공동 기록",
          icon: "heart",
        },
      ],
    },
    {
      title: "관리",
      links: [
        { href: "/pets", label: "반려동물 관리", icon: "paw" },
        { href: "/me", label: "마이페이지", icon: "user" },
        { href: "/settings", label: "설정 · 설치 안내", icon: "settings" },
        ...(app.account?.role === "ADMIN"
          ? [{ href: "/admin/reports", label: "관리자 데모", icon: "shield" }]
          : []),
      ],
    },
  ];
  const current = groups
    .flatMap((group) => group.links)
    .filter((link) => route === link.href || route.startsWith(link.href + "/"))
    .sort((a, b) => b.href.length - a.href.length)[0];
  async function select(id: string) {
    setSwitching(true);
    try {
      await app.dependencies.workspace.selectPet(app.account!.id, id);
      await app.refresh();
      router.push("/home");
    } catch (cause) {
      app.notify(messageOf(cause));
    } finally {
      setSwitching(false);
    }
  }
  const nav = (
    <>
      {groups.map((group) => (
        <section className={styles.navGroup} key={group.title}>
          <p>{group.title}</p>
          {group.links.map((link) => (
            <Link
              key={link.label}
              href={link.href}
              onClick={() => setOpen(false)}
              className={current?.label === link.label ? styles.active : ""}
              aria-current={current?.label === link.label ? "page" : undefined}
            >
              <Icon name={link.icon} size={19} />
              {link.label}
            </Link>
          ))}
        </section>
      ))}
    </>
  );
  return (
    <div className={styles.app}>
      <a className="skip-link" href="#main">
        본문으로 바로가기
      </a>
      <aside className={`${styles.sidebar} ${open ? styles.sidebarOpen : ""}`}>
        <div className={styles.logo}>
          <Brand href="/home" garden={pet?.mode === "GARDEN"} />
          <button
            className={`icon-button ${styles.closeMenu}`}
            onClick={() => setOpen(false)}
            aria-label="메뉴 닫기"
          >
            <Icon name="close" />
          </button>
        </div>
        <div className={styles.petSwitcher}>
          <label htmlFor="pet-switcher">함께 기록할 반려동물</label>
          <select
            id="pet-switcher"
            value={pet?.id ?? app.state.selectedPetId}
            disabled={!app.state.pets.length || switching}
            onChange={(event) => void select(event.target.value)}
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
        </div>
        <nav aria-label="주요 메뉴" className={styles.nav}>
          {nav}
        </nav>
        <div className={styles.sideBottom}>
          <span className={styles.demoDot} />
          브라우저 데모<small>함께한 오늘을, 오래도록.</small>
        </div>
      </aside>
      {open && (
        <button
          className={styles.scrim}
          aria-label="메뉴 닫기"
          onClick={() => setOpen(false)}
        />
      )}
      <div className={styles.workspace}>
        <header className={styles.topbar}>
          <button
            className={`icon-button ${styles.menuToggle}`}
            aria-label="전체 메뉴 열기"
            aria-expanded={open}
            onClick={() => setOpen(true)}
          >
            <Icon name="menu" />
          </button>
          <div className={styles.mobileBrand}>
            <Brand href="/home" garden={pet?.mode === "GARDEN"} />
          </div>
          <div className={styles.breadcrumb}>
            <span>{pet?.name ?? "PawMento"}</span>
            <Icon name="chevron" size={13} />
            <strong>{current?.label ?? "우리의 이야기"}</strong>
          </div>
          <div className={styles.topActions}>
            <span className={styles.demoPill}>프론트엔드 데모</span>
            {pet?.accessRole !== "VIEWER" && (
              <Link
                className={`button primary ${styles.writeButton}`}
                href={pet ? `${p}/records/new` : "/pets/new"}
              >
                <Icon name="plus" size={16} />
                기록 남기기
              </Link>
            )}
            <Link href="/me" aria-label="내 정보" className={styles.account}>
              <span>{app.account!.nickname.slice(0, 1)}</span>
              <small>{app.account!.nickname}</small>
            </Link>
          </div>
        </header>
        <main id="main" className={styles.main}>
          {children}
        </main>
        <footer className={styles.footer}>
          <span>PawMento</span>
          <small>
            기록은 이 브라우저에 보관됩니다 · 실제 AI 호출과 외부 전송은 하지
            않습니다.
          </small>
        </footer>
      </div>
      <nav className={styles.mobileNav} aria-label="모바일 주요 메뉴">
        {[
          { href: "/home", label: "홈", icon: "home" },
          {
            href: pet ? `${p}/calendar` : "/pets",
            label: "기록",
            icon: "calendar",
          },
          {
            href: pet ? `${p}/records/new` : "/pets/new",
            label: "기록하기",
            icon: "plus",
          },
          { href: "/community", label: "커뮤니티", icon: "users" },
          { href: "/me", label: "마이", icon: "user" },
        ].map((link) => (
          <Link
            key={link.label}
            href={link.href}
            aria-current={route === link.href ? "page" : undefined}
          >
            <Icon name={link.icon} size={21} />
            <span>{link.label}</span>
          </Link>
        ))}
      </nav>
    </div>
  );
}
