"use client";
import { journalItems } from "@/features/diary/utils/journalItems";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { useApp } from "@/app/AppProvider";
import { useConfirmNavigation } from "@/components/UnsavedChangesProvider";
import { Icon } from "@/components/Icon";
import { PhotoPicker } from "@/components/PhotoPicker";
import { today } from "@/lib/date";
import type { Pet } from "../types/Pet";
import type { PetInput } from "../types/Pet";
import styles from "../../shell/components/Workspace.module.css";
import { PetSummary, EmptyState, PageHeading } from "../../shell/components/WorkspaceUi";
import { PetProfileFields } from "./PetProfileFields";
import { useUnsavedChanges } from "../../shell/hooks/useUnsavedChanges";
import { messageOf } from "../../shell/utils/presentation";

export function PetList() {
  const app = useApp();
  const router = useRouter();
  const confirmNavigation = useConfirmNavigation();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  async function select(pet: Pet) {
    if (pet.id === app.state.selectedPetId || !confirmNavigation()) return;
    setBusy(true);
    try {
      await app.dependencies.workspace.selectPet(app.account!.id, pet.id);
      await app.refresh();
      router.push("/home");
    } catch (cause) {
      setError(messageOf(cause));
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <PageHeading
        eyebrow="OUR LITTLE FAMILY"
        title="함께하는 친구들"
        body="아이를 선택해 공간을 전환하고, 프로필과 일상·정원 모드를 관리해요."
      >
        <Link className="button primary" href="/pets/new">
          <Icon name="plus" size={17} />
          반려동물 등록
        </Link>
      </PageHeading>
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
      {app.state.pets.length ? (
        <div className={styles.petList}>
          {app.state.pets.map((pet) => (
            <article key={pet.id}>
              <PetSummary
                pet={pet}
                count={
                  journalItems(app.state).filter(
                    (record) => record.petId === pet.id,
                  ).length
                }
              />
              <div className={styles.petActions}>
                <button
                  className="button secondary full"
                  disabled={busy || app.state.selectedPetId === pet.id}
                  aria-pressed={app.state.selectedPetId === pet.id}
                  aria-label={`${pet.name}${app.state.selectedPetId === pet.id ? " · 현재 보고 있는 아이" : "의 공간으로 전환"}`}
                  onClick={() => void select(pet)}
                >
                  {app.state.selectedPetId === pet.id
                    ? "현재 보고 있는 아이"
                    : "이 아이의 공간으로"}
                  <Icon name="arrow" size={17} />
                </button>
                {!pet.accessRole || pet.accessRole === "OWNER" ? (
                  <Link
                    className="button secondary full"
                    href={`/pets/${pet.id}/settings/mode`}
                    aria-label={`${pet.name} 모드 설정`}
                  >
                    <Icon name="settings" size={16} />
                    모드 변경
                  </Link>
                ) : (
                  <p className="muted">모드는 소유자가 변경할 수 있어요.</p>
                )}
              </div>
            </article>
          ))}
        </div>
      ) : (
        <EmptyState
          title="첫 친구를 소개해 주세요"
          body="함께하는 친구만의 기록 공간을 만들어드릴게요."
          href="/pets/new"
          action="등록하기"
        />
      )}
    </>
  );
}

export function PetForm({ pet }: { pet?: Pet }) {
  const app = useApp();
  const router = useRouter();
  const initial: PetInput = {
    name: pet?.name ?? "",
    species: pet?.species ?? "DOG",
    breed: pet?.breed ?? "",
    birthday: pet?.birthday ?? "",
    photoUrl: pet?.photoUrl ?? "",
  };
  const [input, setInput] = useState<PetInput>(initial);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [saved, setSaved] = useState(false);
  useUnsavedChanges(
    JSON.stringify(input) !== JSON.stringify(initial) && !saved,
  );
  function update(fields: Partial<PetInput>) {
    setSaved(false);
    setInput((old) => ({ ...old, ...fields }));
  }
  async function save(event: React.FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError("");
    try {
      if (input.birthday && input.birthday > today())
        throw Error("생일은 오늘 또는 이전 날짜로 선택해 주세요.");
      await app.dependencies.workspace.savePet(app.account!.id, input, pet?.id);
      setSaved(true);
      await app.refresh();
      app.notify(
        pet ? "프로필을 수정했어요." : "새 친구의 기록 공간을 만들었어요.",
      );
      router.push("/home");
    } catch (cause) {
      setError(messageOf(cause));
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <Link href="/pets" className="text-link">
        ← 함께하는 친구들
      </Link>
      <PageHeading
        eyebrow="MEET MY LITTLE FRIEND"
        title={pet ? `${pet.name}의 프로필` : "함께하는 친구를 소개해 주세요"}
        body="서로를 알아가는 작은 첫걸음."
      />
      <form onSubmit={save} className={styles.editor}>
        <section className={styles.editorPhoto}>
          <PhotoPicker
            value={input.photoUrl}
            onChange={(photoUrl) => update({ photoUrl })}
          />
        </section>
        <section className={`${styles.editorFields} form-stack`}>
          <PetProfileFields
            input={input}
            change={(key, value) => update({ [key]: value })}
            breedMaxLength={50}
          />
          {error && (
            <p className="error" role="alert">
              {error}
            </p>
          )}
          <button className="button primary full" disabled={busy}>
            {busy
              ? "저장하는 중…"
              : pet
                ? "프로필 저장하기"
                : "함께 기록 시작하기"}
            <Icon name="arrow" size={17} />
          </button>
          {pet && (
            <div>
              <Link className="text-link" href={`/pets/${pet.id}/family`}>
                가족 공동 기록 설정
              </Link>
              <br />
              <button
                type="button"
                className="text-link danger"
                disabled={busy}
                onClick={async () => {
                  if (
                    !confirm(
                      "이 친구의 프로필을 삭제할까요? 기록이 있으면 프로필을 유지합니다.",
                    )
                  )
                    return;
                  setBusy(true);
                  setError("");
                  try {
                    await app.dependencies.workspace.deletePet(
                      app.account!.id,
                      pet.id,
                    );
                    await app.refresh();
                    setSaved(true);
                    app.notify("프로필을 삭제했어요.");
                    router.push("/pets");
                  } catch (cause) {
                    setError(messageOf(cause));
                  } finally {
                    setBusy(false);
                  }
                }}
              >
                프로필 삭제
              </button>
            </div>
          )}
        </section>
      </form>
    </>
  );
}
