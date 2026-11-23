"use client";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { useApp } from "@/app/AppProvider";
import { useConfirmNavigation } from "@/components/UnsavedChangesProvider";
import { Icon } from "@/components/Icon";
import type { Pet } from "../../domain/models";
import styles from "./AppShell.module.css";
import { SpaceShell } from "./SpaceShell";
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
  const confirmNavigation = useConfirmNavigation();
  const [switching, setSwitching] = useState(false);
  const [open, setOpen] = useState(false);
  const garden = pet?.mode === "GARDEN";
  const p = pet ? `/pets/${pet.id}` : "/pets";
  const groups = [
    {
      title: "나의 기록",
      links: [
        {
          href: "/home",
          label: "홈",
          icon: "home",
        },
        {
          href: pet ? `${p}/diary` : "/pets",
          label: "다이어리",
          icon: "book",
        },
        {
          href: pet ? `${p}/questions` : "/pets",
          label: "오늘의 질문",
          icon: "question",
        },
      ],
    },
    {
      title: "함께하는 공간",
      links: [
        ...(garden
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
  const navigationRoute =
    pet &&
    ["calendar", "media", "records"].some((page) =>
      route.startsWith(`${p}/${page}`),
    )
      ? `${p}/diary`
      : pet && route.startsWith(`${p}/diary`)
        ? `${p}/questions`
        : route;
  const current = groups
    .flatMap((group) => group.links)
    .filter(
      (link) =>
        navigationRoute === link.href ||
        navigationRoute.startsWith(link.href + "/"),
    )
    .sort((a, b) => b.href.length - a.href.length)[0];
  async function select(id: string) {
    if (!confirmNavigation()) return;
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
    <div className={styles.app} data-mode={garden ? "GARDEN" : "ACTIVE"}>
      <SpaceShell
        pet={pet}
        route={route}
        navigation={nav}
        open={open}
        setOpen={setOpen}
        onSelect={select}
        switching={switching}
      >
        {children}
      </SpaceShell>
    </div>
  );
}
