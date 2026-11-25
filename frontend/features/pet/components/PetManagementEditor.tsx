"use client";

import Link from "next/link";
import { useLayoutEffect, useRef, useState, type FormEvent } from "react";
import { Icon } from "@/components/Icon";
import { Photo } from "@/components/Photo";
import { formatDate } from "@/lib/date";
import { ApplicationError } from "@/lib/application/ApplicationError";
import { PageHeading } from "../../shell/components/WorkspaceUi";
import styles from "../../shell/components/Workspace.module.css";
import { useUnsavedChanges } from "../../shell/hooks/useUnsavedChanges";
import { messageOf, speciesName } from "../../shell/utils/presentation";
import type { createPetManagementApi } from "../api/petManagementApi";
import type { ManagedPet } from "../types/ManagedPet";
import { profileChanges, profileInput, rebaseProfileInput } from "../utils/petManagementEdit";
import { PetProfileFields } from "./PetProfileFields";

export function PetManagementEditor({ pet, api, href, onCancel, onSaved }: {
  pet: ManagedPet;
  api: Pick<ReturnType<typeof createPetManagementApi>, "edit" | "get">;
  href: string;
  onCancel: () => void;
  onSaved: () => void;
}) {
  const [original, setOriginal] = useState(() => profileInput(pet));
  const [input, setInput] = useState(() => profileInput(pet));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [blocked, setBlocked] = useState(false);
  const [loginRequired, setLoginRequired] = useState(false);
  const [conflict, setConflict] = useState(false);
  const [latest, setLatest] = useState<ManagedPet | null>(null);
  const active = useRef(false);
  const pending = useRef(false);
  const dirty = JSON.stringify(input) !== JSON.stringify(original);
  useUnsavedChanges(dirty);

  useLayoutEffect(() => {
    active.current = true;
    return () => { active.current = false; };
  }, []);

  function showError(cause: unknown) {
    const code = cause instanceof ApplicationError ? cause.code : undefined;
    const unauthorized = code === "UNAUTHORIZED";
    const forbidden = code === "FORBIDDEN";
    const missing = code === "NOT_FOUND";
    setLoginRequired(unauthorized);
    setBlocked(unauthorized || forbidden || missing);
    if (code === "CONFLICT") setConflict(true);
    setError(
      unauthorized ? "프로필을 수정하려면 로그인해 주세요." :
      forbidden ? "이 반려동물의 프로필을 수정할 권한이 없어요." :
      missing ? "반려동물을 찾을 수 없어요." :
      code === "CONFLICT" ? "저장 중 충돌이 발생했어요. 최신 프로필을 확인해 주세요." :
      messageOf(cause),
    );
  }

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (pending.current || blocked || conflict) return;
    pending.current = true;
    setBusy(true);
    setError("");
    try {
      const changes = profileChanges(original, input);
      if (Object.keys(changes).length) await api.edit(pet.id, changes);
      if (active.current) onSaved();
    } catch (cause) {
      if (active.current) showError(cause);
    } finally {
      pending.current = false;
      if (active.current) setBusy(false);
    }
  }

  async function checkLatest() {
    if (pending.current || blocked) return;
    pending.current = true;
    setBusy(true);
    try {
      const result = await api.get(pet.id);
      if (active.current) {
        setLatest(result);
        setError("");
      }
    } catch (cause) {
      if (active.current) showError(cause);
    } finally {
      pending.current = false;
      if (active.current) setBusy(false);
    }
  }

  return (
    <>
      <PageHeading
        eyebrow="MEET MY LITTLE FRIEND"
        title={`${pet.name}의 프로필`}
        body="서로를 알아가는 작은 첫걸음."
      />
      <form onSubmit={save} className={styles.editor}>
        <section className={styles.editorPhoto}>
          <div className="photo-picker">
            <Photo src={pet.profilePhotoUrl ?? undefined} alt={`${pet.name} 프로필`} />
          </div>
        </section>
        <section className={`${styles.editorFields} form-stack`}>
          <PetProfileFields
            input={input}
            change={(key, value) => setInput((previous) => ({ ...previous, [key]: value }))}
            breedMaxLength={100}
            disabled={busy || blocked}
          />
          {error && (
            <p className="error" role="alert">
              {error}{" "}
              {loginRequired && (
                <Link className="text-link" href={`/sign-in?returnTo=${encodeURIComponent(href)}`}>
                  로그인
                </Link>
              )}
            </p>
          )}
          {conflict && !blocked && (
            <button type="button" className="text-link" disabled={busy} onClick={checkLatest}>
              최신 프로필 확인
            </button>
          )}
          {latest && (
            <div>
              <p role="status">
                서버 프로필 · {latest.name} · {speciesName[latest.species]} · {latest.breed || "품종 미입력"} ·{" "}
                {latest.birthday ? formatDate(latest.birthday) : "생일 미입력"}
              </p>
              <button
                type="button"
                className="text-link"
                disabled={busy || blocked}
                onClick={() => {
                  const current = profileInput(latest);
                  setInput(rebaseProfileInput(original, input, current));
                  setOriginal(current);
                  setLatest(null);
                  setConflict(false);
                }}
              >
                최신 값 기준으로 계속 편집
              </button>
            </div>
          )}
          <button className="button primary full" disabled={busy || blocked || conflict || !dirty}>
            {busy ? "저장하는 중…" : "프로필 저장하기"}
            <Icon name="arrow" size={17} />
          </button>
          <button
            type="button"
            className="text-link"
            disabled={busy}
            onClick={() => {
              if (!dirty || window.confirm("저장하지 않은 변경 사항을 버릴까요?")) onCancel();
            }}
          >
            취소
          </button>
        </section>
      </form>
    </>
  );
}
