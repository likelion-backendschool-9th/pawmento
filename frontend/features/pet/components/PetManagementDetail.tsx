"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useLayoutEffect, useState } from "react";
import { useApp } from "@/app/AppProvider";
import { Photo } from "@/components/Photo";
import { formatDate } from "@/lib/date";
import styles from "../../shell/components/Workspace.module.css";
import { lifeStyles } from "../../shell/components/LifeUi";
import { PageHeading } from "../../shell/components/WorkspaceUi";
import { speciesName } from "../../shell/utils/presentation";
import type { createPetManagementApi } from "../api/petManagementApi";
import { PetManagementMode } from "./PetManagementMode";
import { PetManagementEditor } from "./PetManagementEditor";
import {
  loadPetManagementDetail,
  petDetailScreen,
  type PetDetailSession,
  type PetDetailState,
} from "../utils/petManagementDetail";

export function PetManagementDetail() {
  const { managedPetId: petId } = useParams<{ managedPetId: string }>();
  const { account, sessionReady, sessionSource, dependencies } = useApp();
  const session = { ready: sessionReady, source: sessionSource, accountId: account?.id ?? null, petId };
  const screen = petDetailScreen(session);
  const href = `/pets/manage/${encodeURIComponent(petId)}`;

  return (
    <>
      <Link href="/pets" className="text-link">← 함께하는 친구들</Link>
      {screen === "loading" ? (
        <p className="loading-bar" role="status">로그인 상태를 확인하고 있어요…</p>
      ) : screen === "server" ? (
        <ServerDetail
          key={JSON.stringify([session.accountId, petId])}
          session={session}
          api={dependencies.petManagement}
          href={href}
        />
      ) : (
        <DetailLoginRequired href={href} message="서버에 등록된 프로필을 보려면 로그인해 주세요." />
      )}
    </>
  );
}

function DetailLoginRequired({ href, message }: { href: string; message: string }) {
  return (
    <div className="error" role="alert">
      <span>{message}</span>{" "}
      <Link className="text-link" href={`/sign-in?returnTo=${encodeURIComponent(href)}`}>
        로그인
      </Link>
    </div>
  );
}

function ServerDetail({ session, api, href }: {
  session: PetDetailSession;
  api: ReturnType<typeof createPetManagementApi>;
  href: string;
}) {
  const [state, setState] = useState<PetDetailState>({ status: "loading" });
  const [view, setView] = useState<"profile" | "edit" | "mode">("profile");
  const [attempt, setAttempt] = useState(0);
  const { get } = api;
  const { accountId, petId, ready, source } = session;

  useLayoutEffect(() => {
    let current: PetDetailSession | null = { accountId, petId, ready, source };
    void loadPetManagementDetail(() => current, get, setState);
    return () => {
      current = null;
    };
  }, [accountId, attempt, get, petId, ready, source]);

  if (state.status === "loading") {
    return <p className="loading-bar" role="status">프로필을 불러오고 있어요…</p>;
  }
  if (state.status === "login-required") {
    return <DetailLoginRequired href={href} message="프로필을 보려면 로그인해 주세요." />;
  }
  if (state.status === "error") {
    return (
      <div className="error" role="alert">
        <span>{state.message}</span>{" "}
        {state.retry && (
          <button type="button" className="text-link" onClick={() => setAttempt((previous) => previous + 1)}>
            다시 시도
          </button>
        )}
      </div>
    );
  }

  const { pet } = state;
  function reloadProfile() {
    setView("profile");
    setState({ status: "loading" });
    setAttempt((previous) => previous + 1);
  }

  if (view === "mode") {
    return (
      <PetManagementMode
        pet={pet}
        changeMode={api.changeMode}
        href={href}
        onBack={() => setView("profile")}
        onReload={reloadProfile}
      />
    );
  }
  if (view === "edit") {
    return (
      <PetManagementEditor
        pet={pet}
        api={api}
        href={href}
        onCancel={() => setView("profile")}
        onSaved={reloadProfile}
      />
    );
  }
  return (
    <div className={styles.detailWrap}>
      <PageHeading
        eyebrow="MEET MY LITTLE FRIEND"
        title={`${pet.name}의 프로필`}
        body="등록된 프로필 정보를 확인해요."
      />
      <section className={styles.petSummary}>
        <Photo src={pet.profilePhotoUrl ?? undefined} alt={`${pet.name} 프로필`} className={styles.petAvatar} />
        <h2>
          <strong>{pet.name}</strong>
          <span>{pet.mode === "GARDEN" ? "기억의 정원" : "함께하는 중"}</span>
        </h2>
        <p>{speciesName[pet.species]}{pet.breed ? ` · ${pet.breed}` : ""}</p>
        <div className={styles.petStats}>
          <div><strong>{pet.tag}</strong><span>태그</span></div>
          <div><strong>{pet.birthday ? formatDate(pet.birthday) : "—"}</strong><span>생일</span></div>
        </div>
        <div className={lifeStyles.inlineActions}>
          <button type="button" className="button secondary" onClick={() => setView("edit")}>
            프로필 수정
          </button>
          <button type="button" className="button secondary" onClick={() => setView("mode")}>
            모드 변경
          </button>
        </div>
      </section>
    </div>
  );
}
