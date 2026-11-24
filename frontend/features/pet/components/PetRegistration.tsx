"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useLayoutEffect, useRef, useState, type FormEvent } from "react";
import { useApp } from "@/app/AppProvider";
import { Icon } from "@/components/Icon";
import { useUnsavedChanges } from "../../shell/hooks/useUnsavedChanges";
import { PageHeading } from "../../shell/components/WorkspaceUi";
import styles from "../../shell/components/Workspace.module.css";
import type { RegisterPetInput } from "../types/ManagedPet";
import {
  createPetRegistrationSubmission,
  type RegistrationState,
} from "../utils/petRegistrationSubmission";
import { PetForm } from "./PetScreens";
import { PetProfileFields } from "./PetProfileFields";

const initial: RegisterPetInput = { name: "", species: "DOG", breed: "", birthday: "" };

export function PetRegistration() {
  const { account, sessionReady, sessionSource } = useApp();

  if (!sessionReady) return null;
  if (account && sessionSource === "demo") return <PetForm key={account.id} />;
  if (!account || sessionSource !== "http") {
    return (
      <p className="error" role="alert">
        반려동물을 등록하려면 로그인해 주세요. <Link href="/sign-in?returnTo=/pets/new">로그인</Link>
      </p>
    );
  }

  return <ServerRegistration key={account.id} accountId={account.id} />;
}

function ServerRegistration({ accountId }: { accountId: string }) {
  const { dependencies, notify } = useApp();
  const router = useRouter();
  const submission = useRef<ReturnType<typeof createPetRegistrationSubmission> | null>(null);
  const [input, setInput] = useState(initial);
  const [state, setState] = useState<RegistrationState>({ status: "idle" });
  const error = "message" in state ? state.message : undefined;

  useLayoutEffect(() => {
    let mounted = true;
    submission.current = createPetRegistrationSubmission(
      () => mounted ? { source: "http", accountId } : null,
    );
    return () => {
      mounted = false;
      submission.current = null;
    };
  }, [accountId]);

  useUnsavedChanges(
    state.status !== "saved" && JSON.stringify(input) !== JSON.stringify(initial),
  );

  function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void submission.current?.(input, dependencies.petManagement.register, setState, (pet) => {
      notify(`${pet.name} 프로필을 등록했어요. 태그 ${pet.tag}`);
      router.replace("/pets");
    });
  }

  return (
    <>
      <Link href="/pets" className="text-link">← 함께하는 친구들</Link>
      <PageHeading
        eyebrow="MEET MY LITTLE FRIEND"
        title="함께하는 친구를 소개해 주세요"
        body="서로를 알아가는 작은 첫걸음."
      />
      <form onSubmit={save} className={styles.editor}>
        <section className={styles.editorPhoto}>
          <div className="photo-picker">
            <div className="upload-placeholder">
              <Icon name="camera" size={34} />
              <strong>이 순간을 함께 담아볼까요?</strong>
              <span>사진 등록은 아직 연결되지 않았어요.</span>
            </div>
            <button type="button" className="button secondary" disabled>
              <Icon name="plus" size={16} />
              사진 선택하기
            </button>
          </div>
        </section>
        <section className={`${styles.editorFields} form-stack`}>
          <PetProfileFields
            input={input}
            change={(key, value) => setInput((previous) => ({ ...previous, [key]: value }))}
            breedMaxLength={100}
            disabled={state.status !== "idle"}
          />
          {error && (
            <p className="error" role="alert">
              {error}
              {state.status === "unconfirmed" && <Link className="text-link" href="/pets">목록 확인</Link>}
              {state.status === "login-required" && <Link className="text-link" href="/sign-in?returnTo=/pets/new">로그인</Link>}
            </p>
          )}
          <button className="button primary full" disabled={state.status !== "idle"}>
            {state.status === "saving" ? "저장하는 중…" : "반려동물 등록"}
            <Icon name="arrow" size={17} />
          </button>
        </section>
      </form>
    </>
  );
}
