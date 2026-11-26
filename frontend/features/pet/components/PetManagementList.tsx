"use client";

import { useLayoutEffect, useState } from "react";
import Link from "next/link";
import { useApp } from "@/app/AppProvider";
import { Icon } from "@/components/Icon";
import { Photo } from "@/components/Photo";
import { formatDate } from "@/lib/date";
import { PageHeading } from "../../shell/components/WorkspaceUi";
import styles from "../../shell/components/Workspace.module.css";
import { speciesName } from "../../shell/utils/presentation";
import { MyFamilyPets } from "../../family/components/MyFamilyPets";
import type { ManagedPet } from "../types/ManagedPet";
import {
  loadPetManagementList,
  petListScreen,
  type PetListSession,
  type PetListState,
} from "../utils/petManagementList";
import { PetList, WorkspacePetSwitch } from "./PetScreens";

export function PetManagementList() {
  const { account, sessionReady, sessionSource, dependencies } = useApp();
  const session = {
    ready: sessionReady,
    source: sessionSource,
    accountId: account?.id ?? null,
  };
  const screen = petListScreen(session);

  if (screen === "loading") return <ListLoading />;
  if (screen === "demo") return <PetList />;
  if (screen === "login-required") return <LoginRequired />;

  return (
    <ServerList
      key={session.accountId}
      session={session}
      listMine={dependencies.petManagement.listMine}
    />
  );
}

function ServerList({ session, listMine }: {
  session: PetListSession;
  listMine: () => Promise<ManagedPet[]>;
}) {
  const [state, setState] = useState<PetListState>({ status: "loading" });
  const [attempt, setAttempt] = useState(0);
  const { ready, source, accountId } = session;

  useLayoutEffect(() => {
    let current: PetListSession | null = { ready, source, accountId };
    void loadPetManagementList(() => current, listMine, setState);
    return () => {
      current = null;
    };
  }, [accountId, attempt, listMine, ready, source]);

  if (state.status === "login-required") return <LoginRequired />;

  return (
    <>
      <PageHeading
        eyebrow="OUR LITTLE FAMILY"
        title="함께하는 친구들"
        body="등록된 친구들의 프로필과 일상·정원 모드를 확인해요."
      >
        <Link className="button primary" href="/pets/new">
          <Icon name="plus" size={17} />
          반려동물 등록
        </Link>
        <Link className="button secondary" href="/family/join">
          초대 코드로 참여
        </Link>
      </PageHeading>
      {state.status === "loading" && <ListLoading />}
      {state.status === "error" && (
        <div className="error" role="alert">
          <span>{state.message}</span>
          <button
            type="button"
            className="text-link"
            onClick={() => setAttempt((previous) => previous + 1)}
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
      {/* 서버 목록은 아직 공간 전환을 지원하지 않아, 브라우저 기록의 기존 전환 경로를 따로 둔다 */}
      <WorkspacePetSwitch />
      {/* 가족 반려동물은 내 목록 조회의 성공 여부와 따로 불러온다 */}
      <MyFamilyPets />
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
      <Link className="button primary" href="/pets/new">
        등록하기
        <Icon name="arrow" size={16} />
      </Link>
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
        <Link
          className={styles.petEdit}
          href={`/pets/manage/${encodeURIComponent(pet.id)}`}
          aria-label={`${pet.name} 프로필 보기`}
        >
          <Icon name="search" size={17} />
        </Link>
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
        <Link
          className="button secondary full"
          href={`/pets/manage/${encodeURIComponent(pet.id)}/family`}
        >
          <Icon name="user" size={16} />
          가족
        </Link>
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
