"use client";
import Link from "next/link";
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
        <Link href="/me" className={s.profile} aria-label="마이페이지">
          <Icon name="user" size={21} />
        </Link>
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
    </div>
  );
}
