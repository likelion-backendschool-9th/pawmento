"use client";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { useApp } from "@/app/AppProvider";
import { Icon } from "@/shared/ui/Icon";
import { PhotoPicker } from "@/shared/ui/PhotoPicker";
import { today } from "@/shared/date";
import type { Pet } from "../../domain/models";
import type { PetInput } from "../../domain/models";
import styles from "./Workspace.module.css";
import { PetSummary, EmptyState, PageHeading } from "./WorkspaceUi";
import { useUnsavedChanges } from "./useUnsavedChanges";
import { speciesName, messageOf } from "./presentation";

export function PetList() {
  const app = useApp();
  const router = useRouter();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  async function select(pet: Pet) {
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
        body="누구와 함께한 하루를 기록할까요?"
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
                  app.state.records.filter((record) => record.petId === pet.id)
                    .length
                }
              />
              <button
                className="button secondary full"
                disabled={busy}
                onClick={() => void select(pet)}
              >
                {app.state.selectedPetId === pet.id
                  ? "함께 기록하는 중"
                  : "이 친구와 기록하기"}
                <Icon name="arrow" size={17} />
              </button>
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
  function change<K extends keyof PetInput>(key: K, value: PetInput[K]) {
    setSaved(false);
    setInput((old) => ({ ...old, [key]: value }));
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
            onChange={(value) => change("photoUrl", value)}
          />
        </section>
        <section className={`${styles.editorFields} form-stack`}>
          <label>
            이름 <span className="required">필수</span>
            <input
              value={input.name}
              onChange={(event) => change("name", event.target.value)}
              placeholder="친구의 이름은 무엇인가요?"
              maxLength={20}
              required
            />
          </label>
          <label>
            어떤 친구인가요?
            <select
              value={input.species}
              onChange={(event) =>
                change("species", event.target.value as Pet["species"])
              }
            >
              {Object.entries(speciesName).map(([value, label]) => (
                <option value={value} key={value}>
                  {label}
                </option>
              ))}
            </select>
          </label>
          <label>
            품종 <span className="muted">선택</span>
            <input
              list="breed-options"
              value={input.breed}
              maxLength={50}
              onChange={(event) => change("breed", event.target.value)}
              placeholder="찾거나 직접 입력해 주세요"
            />
            <datalist id="breed-options">
              {(input.species === "DOG"
                ? [
                    "웰시코기",
                    "말티즈",
                    "푸들",
                    "포메라니안",
                    "골든리트리버",
                    "믹스",
                  ]
                : input.species === "CAT"
                  ? ["코리안숏헤어", "러시안블루", "랙돌", "페르시안", "믹스"]
                  : []
              ).map((breed) => (
                <option key={breed} value={breed} />
              ))}
            </datalist>
          </label>
          <label>
            생일 <span className="muted">선택</span>
            <input
              type="date"
              value={input.birthday}
              max={today()}
              onChange={(event) => change("birthday", event.target.value)}
            />
          </label>
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
              <Link
                className="text-link"
                href={`/pets/${pet.id}/settings/mode`}
              >
                ACTIVE / GARDEN 모드 설정
              </Link>
              <br />
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
