"use client";
import Link from "next/link";
import Image from "next/image";
import { useApp } from "@/app/AppProvider";
import { Icon } from "@/components/Icon";
import { PageHeading } from "@/features/shell/components/WorkspaceUi";
import s from "./MoreScreen.module.css";

export function MoreScreen() {
  const app = useApp();
  const account = app.account!;
  const pet = app.state.pets.find((p) => p.id === app.state.selectedPetId);
  const menus = [
    { href: "/pets", label: "반려동물 관리", icon: "paw" },
    ...(pet
      ? [
          {
            href: `/pets/${pet.id}/family`,
            label: "함께 기록하는 가족",
            icon: "users",
          },
        ]
      : []),
    ...(pet?.mode === "GARDEN"
      ? [
          {
            href: "/places",
            label: "정보 지도",
            icon: "map",
          },
        ]
      : []),
    { href: "/me/activity", label: "나의 활동", icon: "heart" },
    { href: "/settings", label: "설정 · 앱 설치", icon: "settings" },
    ...(account.role === "ADMIN"
      ? [{ href: "/admin/reports", label: "운영 관리", icon: "shield" }]
      : []),
  ];
  return (
    <>
      <PageHeading
        eyebrow="MY SPACE"
        title="더보기"
        body="우리의 기록과 함께하는 공간을 관리해요."
      />
      <Link href="/me" className={s.account}>
        <span className={s.avatar}>{account.nickname.slice(0, 1)}</span>
        <span>
          <strong>{account.nickname}</strong>
          <small>마이페이지 · 내 정보 관리</small>
        </span>
        <Icon name="chevron" size={18} />
      </Link>
      <section className="profile-destinations" aria-label="더보기 메뉴">
        {menus.map((menu) => (
          <Link key={menu.href} href={menu.href}>
            <Icon name={menu.icon} size={20} />
            <span>{menu.label}</span>
            <Icon name="chevron" size={16} />
          </Link>
        ))}
      </section>
      <div className={s.developers}>
        <Image
          src="/images/developer-lineup.png"
          alt="PawMento 개발자들의 대표 캐릭터: 샴고양이, 고슴도치, 초록 뱀, 왕관앵무, 친칠라"
          width={2160}
          height={720}
          sizes="(max-width: 480px) calc(100vw - 36px), 444px"
          className={s.developerLineup}
        />
      </div>
    </>
  );
}
