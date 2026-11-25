"use client";

import Link from "next/link";
import { useState } from "react";
import { useApp } from "@/app/AppProvider";
import { ApplicationError } from "@/lib/application/ApplicationError";
import { lifeStyles as s } from "../../shell/components/LifeUi";
import { PageHeading } from "../../shell/components/WorkspaceUi";
import { messageOf } from "../../shell/utils/presentation";
import type { JoinedPet } from "../types/serverFamily";

export function JoinFamilyScreen() {
  const { account, sessionReady, sessionSource, dependencies } = useApp();
  const [code, setCode] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [joined, setJoined] = useState<JoinedPet | null>(null);
  const [loginRequired, setLoginRequired] = useState(false);

  if (!sessionReady) {
    return <p className="loading-bar" role="status">로그인 상태를 확인하고 있어요…</p>;
  }
  if (sessionSource !== "http" || !account || loginRequired) {
    return (
      <div className="error" role="alert">
        <span>초대 코드로 참여하려면 로그인해 주세요.</span>{" "}
        <Link className="text-link" href="/sign-in?returnTo=%2Ffamily%2Fjoin">로그인</Link>
      </div>
    );
  }
  if (joined) {
    return (
      <>
        <PageHeading
          eyebrow="WELCOME TO THE FAMILY"
          title={`${joined.name}의 가족이 됐어요`}
          body="이제 함께 기록을 보고 쓸 수 있어요."
        />
        <Link className="button primary" href={`/pets/manage/${encodeURIComponent(joined.id)}/family`}>
          가족 보기
        </Link>
      </>
    );
  }

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!code.trim()) {
      setError("초대 코드를 입력해 주세요.");
      return;
    }
    setBusy(true);
    setError("");
    try {
      setJoined(await dependencies.family.join(code));
    } catch (cause) {
      if (cause instanceof ApplicationError && cause.code === "UNAUTHORIZED") setLoginRequired(true);
      else setError(messageOf(cause));
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <Link href="/pets" className="text-link">← 함께하는 친구들</Link>
      <PageHeading
        eyebrow="JOIN THE FAMILY"
        title="초대 코드로 참여하기"
        body="가족에게 받은 8자리 코드를 입력해 주세요."
      />
      <form onSubmit={submit} className={`${s.panel} form-stack`}>
        <label>
          초대 코드
          <input
            value={code}
            onChange={(event) => setCode(event.target.value)}
            maxLength={20}
            autoComplete="off"
            placeholder="K7Q2M9XA"
          />
        </label>
        {error && <p className="error" role="alert">{error}</p>}
        <button type="submit" className="button primary" disabled={busy}>
          참여하기
        </button>
      </form>
    </>
  );
}
