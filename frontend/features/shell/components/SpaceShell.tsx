"use client";
import Link from "next/link";
import { useApp } from "@/app/AppProvider";
import { PetSwitcher } from "./PetSwitcher";
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
        <PetSwitcher pets={app.state.pets} selectedId={pet?.id ?? app.state.selectedPetId}
          switching={switching} onSelect={onSelect} />
        <Link
          href="/more"
          className={s.moreButton}
          aria-label="더보기"
          aria-current={route === "/more" ? "page" : undefined}
        >
          <Icon name="menu" size={22} />
        </Link>
      </header>
      <main id="main" className={s.main}>
        {children}
      </main>
      <nav className={s.bottomNav} aria-label="주요 메뉴">
        {appNavigation(pet?.id, pet?.mode).map((link) => (
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
