"use client";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { useApp } from "@/app/AppProvider";
import { useConfirmNavigation } from "@/components/UnsavedChangesProvider";
import type { Pet } from "../../pet/types/Pet";
import styles from "./AppShell.module.css";
import { SpaceShell } from "./SpaceShell";
import { messageOf } from "../utils/presentation";
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
  const garden = pet?.mode === "GARDEN";
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
  return (
    <div className={styles.app} data-mode={garden ? "GARDEN" : "ACTIVE"}>
      <SpaceShell
        pet={pet}
        route={route}
        onSelect={select}
        switching={switching}
      >
        {children}
      </SpaceShell>
    </div>
  );
}
