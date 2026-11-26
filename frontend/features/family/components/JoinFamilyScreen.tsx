"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { useApp } from "@/app/AppProvider";
import { lifeStyles as s } from "../../shell/components/LifeUi";
import { PageHeading } from "../../shell/components/WorkspaceUi";
import {
  joinScreen,
  submitJoin,
  type JoinSession,
  type JoinState,
} from "../utils/joinFamily";

export function JoinFamilyScreen() {
  const { account, sessionSource } = useApp();
  // 계정이나 세션 출처가 바뀌면 새로 그려서 앞 계정의 입력·결과가 남지 않게 한다
  return <JoinBody key={`${account?.id ?? "none"}:${sessionSource ?? "none"}`} />;
}

function JoinBody() {
  const { account, sessionReady, sessionSource, dependencies } = useApp();
  const session: JoinSession = { ready: sessionReady, source: sessionSource, accountId: account?.id ?? null };
  const [code, setCode] = useState("");
  const [state, setState] = useState<JoinState>({ status: "idle" });
  // 응답이 왔을 때의 세션을 읽는다. 화면을 떠나면 null이 되어 늦은 응답을 버린다
  const latest = useRef<JoinSession | null>(null);

  useEffect(() => {
    latest.current = session;
    return () => {
      latest.current = null;
    };
  });

  const screen = joinScreen(session);
  if (screen === "loading") {
    return <p className="loading-bar" role="status">로그인 상태를 확인하고 있어요…</p>;
  }
  if (screen === "login-required" || state.status === "login-required") {
    return (
      <div className="error" role="alert">
        <span>초대 코드로 참여하려면 로그인해 주세요.</span>{" "}
        <Link className="text-link" href="/sign-in?returnTo=%2Ffamily%2Fjoin">로그인</Link>
      </div>
    );
  }
  if (state.status === "joined") {
    return (
      <>
        <PageHeading
          eyebrow="WELCOME TO THE FAMILY"
          title={`${state.pet.name}의 가족이 됐어요`}
          body="이제 함께 기록을 보고 쓸 수 있어요."
        />
        {state.pet.id ? (
          <Link className="button primary" href={`/pets/manage/${encodeURIComponent(state.pet.id)}/family`}>
            가족 보기
          </Link>
        ) : (
          <Link className="button primary" href="/pets">
            함께하는 친구 보기
          </Link>
        )}
      </>
    );
  }

  function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void submitJoin(() => latest.current, dependencies.family.join, code, setState);
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
        {state.status === "error" && <p className="error" role="alert">{state.message}</p>}
        <button type="submit" className="button primary" disabled={state.status === "submitting"}>
          참여하기
        </button>
      </form>
    </>
  );
}
