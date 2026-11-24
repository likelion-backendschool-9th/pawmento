"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useApp } from "@/app/AppProvider";
import { Icon } from "@/components/Icon";
import { Photo } from "@/components/Photo";
import { ApplicationError } from "@/lib/application/ApplicationError";
import { formatDate } from "@/lib/date";
import { PageHeading } from "../../shell/components/WorkspaceUi";
import styles from "../../shell/components/Workspace.module.css";
import { messageOf, speciesName } from "../../shell/utils/presentation";
import type { ManagedPet } from "../types/ManagedPet";
import { PetList } from "./PetScreens";

type ListState =
  | { status: "loading" | "login-required" }
  | { status: "ready"; pets: ManagedPet[] }
  | { status: "error"; message: string };

export function PetManagementList() {
  const { account, sessionReady, sessionSource, dependencies } = useApp();

  if (!sessionReady) return <ListLoading />;
  if (account && sessionSource === "demo") return <PetList />;
  if (!account || sessionSource !== "http") return <LoginRequired />;

  return (
    <ServerList key={account.id} listMine={dependencies.petManagement.listMine} />
  );
}

function ServerList({ listMine }: { listMine: () => Promise<ManagedPet[]> }) {
  const [state, setState] = useState<ListState>({ status: "loading" });
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    let cancelled = false;
    void listMine().then(
      (pets) => {
        if (!cancelled) setState({ status: "ready", pets });
      },
      (cause: unknown) => {
        if (cancelled) return;
        if (cause instanceof ApplicationError && cause.code === "UNAUTHORIZED") {
          setState({ status: "login-required" });
        } else {
          setState({ status: "error", message: messageOf(cause) });
        }
      },
    );
    return () => {
      cancelled = true;
    };
  }, [attempt, listMine]);

  if (state.status === "login-required") return <LoginRequired />;

  return (
    <>
      <PageHeading
        eyebrow="OUR LITTLE FAMILY"
        title="함께하는 친구들"
        body="등록된 친구들의 프로필과 일상·정원 모드를 확인해요."
      >
        <button type="button" className="button primary" disabled>
          <Icon name="plus" size={17} />
          반려동물 등록
        </button>
      </PageHeading>
      {state.status === "loading" && <ListLoading />}
      {state.status === "error" && (
        <div className="error" role="alert">
          <span>{state.message}</span>
          <button
            type="button"
            className="text-link"
            onClick={() => {
              setState({ status: "loading" });
              setAttempt((previous) => previous + 1);
            }}
          >
            다시 시도
          </button>
        </div>
      )}
      {state.status === "ready" &&
        (state.pets.length ? (
          <div className={styles.petList}>
            {state.pets.map((pet) => (
              <ManagedPetCard key={pet.id} pet={pet} />
            ))}
          </div>
        ) : (
          <EmptyServerList />
        ))}
    </>
  );
}

function ListLoading() {
  return (
    <div className="loading-bar" role="status">
      반려동물 목록을 불러오고 있어요…
    </div>
  );
}

function LoginRequired() {
  return (
    <div className="error" role="alert">
      <span>반려동물 목록을 보려면 로그인해 주세요.</span>
      <Link className="text-link" href="/sign-in?returnTo=/pets">
        로그인
      </Link>
    </div>
  );
}

function EmptyServerList() {
  return (
    <section className={styles.empty}>
      <span>
        <Icon name="leaf" size={30} />
      </span>
      <h2>첫 친구를 소개해 주세요</h2>
      <p>함께하는 친구만의 기록 공간을 만들어드릴게요.</p>
      <button type="button" className="button primary" disabled>
        등록하기
        <Icon name="arrow" size={16} />
      </button>
    </section>
  );
}

function ManagedPetCard({ pet }: { pet: ManagedPet }) {
  const birthday = pet.birthday
    ? formatDate(pet.birthday, { month: "numeric", day: "numeric" }).replace(/\.$/, "")
    : "—";

  return (
    <article>
      <section className={styles.petSummary}>
        <button
          type="button"
          className={styles.petEdit}
          aria-label={`${pet.name} 프로필 수정`}
          disabled
        >
          <Icon name="edit" size={17} />
        </button>
        <Photo
          src={pet.profilePhotoUrl ?? undefined}
          alt={`${pet.name} 프로필`}
          className={styles.petAvatar}
        />
        <h2>
          <strong>{pet.name}</strong>
          <span>{pet.mode === "GARDEN" ? "기억의 정원" : "함께하는 중"}</span>
        </h2>
        <p>
          {speciesName[pet.species]}
          {pet.breed ? ` · ${pet.breed}` : ""}
        </p>
        <div className={styles.petStats}>
          <div>
            <strong>—</strong>
            <span>쌓인 이야기</span>
          </div>
          <div>
            <strong>{birthday}</strong>
            <span>생일</span>
          </div>
        </div>
      </section>
      <div className={styles.petActions}>
        <button type="button" className="button secondary full" disabled>
          이 친구와 기록하기
          <Icon name="arrow" size={17} />
        </button>
        <button type="button" className="button secondary full" disabled>
          <Icon name="settings" size={16} />
          모드 변경
        </button>
      </div>
    </article>
  );
}
