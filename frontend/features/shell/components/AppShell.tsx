"use client";
import type { Pet } from "../../pet/types/Pet";
import styles from "./AppShell.module.css";
import { SpaceShell } from "./SpaceShell";
export function Shell({
  children,
  pet,
  route,
}: {
  children: React.ReactNode;
  pet?: Pet;
  route: string;
}) {
  const garden = pet?.mode === "GARDEN";
  return (
    <div className={styles.app} data-mode={garden ? "GARDEN" : "ACTIVE"}>
      <SpaceShell
        pet={pet}
        route={route}
      >
        {children}
      </SpaceShell>
    </div>
  );
}
