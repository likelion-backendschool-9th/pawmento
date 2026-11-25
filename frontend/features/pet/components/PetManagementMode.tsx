"use client";

import Link from "next/link";
import { useLayoutEffect, useRef, useState } from "react";
import type { ManagedPet } from "../types/ManagedPet";
import { createModeSubmission } from "../utils/petManagementMode";
import { PetModeForm } from "./ModeScreen";

export function PetManagementMode({
  pet,
  changeMode,
  href,
  onBack,
  onReload,
}: {
  pet: ManagedPet;
  changeMode: (petId: string, mode: ManagedPet["mode"]) => Promise<void>;
  href: string;
  onBack: () => void;
  onReload: () => void;
}) {
  const [mode, setMode] = useState(pet.mode);
  const [status, setStatus] = useState<"idle" | "saving" | "error" | "login-required">("idle");
  const [error, setError] = useState("");
  const submission = useRef<ReturnType<typeof createModeSubmission> | null>(null);
  const busy = status === "saving";

  // The parent unmounts this screen on account, session source or pet change.
  useLayoutEffect(() => {
    let active = true;
    submission.current = createModeSubmission(() => active, changeMode);
    return () => {
      active = false;
      submission.current = null;
    };
  }, [changeMode]);

  async function save() {
    if (status !== "idle" || mode === pet.mode || !submission.current) return;
    setStatus("saving");
    const result = await submission.current(pet, mode);
    if (!result) return;
    if (result.status === "saved") {
      onReload();
      return;
    }
    if (result.status === "error") setError(result.message);
    setStatus(result.status);
  }

  return (
    <>
      <button type="button" className="text-link" disabled={busy} onClick={onBack}>
        ← 프로필로 돌아가기
      </button>
      <PetModeForm
        pet={pet}
        mode={mode}
        onSelect={setMode}
        busy={busy}
        blocked={status !== "idle" && !busy}
        onSave={save}
        server
        feedback={
          status === "login-required" ? (
            <p className="error" role="alert">
              모드를 변경하려면 로그인해 주세요.{" "}
              <Link className="text-link" href={`/sign-in?returnTo=${encodeURIComponent(href)}`}>로그인</Link>
            </p>
          ) : status === "error" ? (
            <p className="error" role="alert">
              {error}{" "}
              <button type="button" className="text-link" onClick={onReload}>프로필 다시 조회</button>
            </p>
          ) : null
        }
      />
    </>
  );
}
