"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useEffect, useState } from "react"; // ★ useCallback 뺐음
import { useApp } from "@/app/AppProvider";
import { Icon } from "@/components/Icon";
import { ApplicationError } from "@/lib/application/ApplicationError";
import { lifeStyles as s } from "../../shell/components/LifeUi";
import { PageHeading } from "../../shell/components/WorkspaceUi";
import { messageOf } from "../../shell/utils/presentation";
import type { FamilyList, FamilyPerson, InviteCode } from "../types/serverFamily";

const dateTime = (iso: string) =>
  new Intl.DateTimeFormat("ko-KR", {
    month: "long",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
    timeZone: "Asia/Seoul",
  }).format(new Date(iso));

export function ServerFamilyScreen() {
  const { managedPetId: petId } = useParams<{ managedPetId: string }>();
  const { account, sessionReady, sessionSource, dependencies } = useApp();
  const [family, setFamily] = useState<FamilyList | null>(null);
  const [error, setError] = useState("");
  const [loginRequired, setLoginRequired] = useState(false);
  const [code, setCode] = useState<InviteCode | null>(null);
  const [issueError, setIssueError] = useState("");
  const [busy, setBusy] = useState(false);
  const [attempt, setAttempt] = useState(0); // ★ 다시 시도 횟수
  const href = `/pets/manage/${encodeURIComponent(petId)}/family`;
  const server = sessionReady && sessionSource === "http" && !!account;

  // ★ load 함수 대신, effect 안에서 .then() 콜백으로만 상태를 바꾼다
  useEffect(() => {
    if (!server) return;
    let active = true;
    dependencies.family.list(petId).then(
      (result) => {
        if (active) setFamily(result);
      },
      (cause) => {
        if (!active) return;
        if (cause instanceof ApplicationError && cause.code === "UNAUTHORIZED") setLoginRequired(true);
        else setError(messageOf(cause));
      },
    );
    return () => {
      active = false;
    };
  }, [server, dependencies, petId, attempt]);

  async function issue() {
    setBusy(true);
    setIssueError("");
    try {
      setCode(await dependencies.family.issueCode(petId));
    } catch (cause) {
      setIssueError(messageOf(cause));
    } finally {
      setBusy(false);
    }
  }

  if (!sessionReady) {
    return <p className="loading-bar" role="status">로그인 상태를 확인하고 있어요…</p>;
  }
  if (!server || loginRequired) {
    return (
      <div className="error" role="alert">
        <span>가족을 보려면 로그인해 주세요.</span>{" "}
        <Link className="text-link" href={`/sign-in?returnTo=${encodeURIComponent(href)}`}>로그인</Link>
      </div>
    );
  }
  if (error) {
    return (
      <div className="error" role="alert">
        <span>{error}</span>{" "}
        {/* ★ load() 대신 attempt를 올려서 effect를 다시 돌린다 */}
        <button type="button" className="text-link" onClick={() => { setError(""); setAttempt((n) => n + 1); }}>
          다시 시도
        </button>
      </div>
    );
  }
  if (!family) {
    return <p className="loading-bar" role="status">가족을 불러오고 있어요…</p>;
  }

  return (
    <>
      <Link href="/pets" className="text-link">← 함께하는 친구들</Link>
      <PageHeading
        eyebrow="OUR LITTLE FAMILY"
        title="함께 기록하는 가족"
        body="초대 코드로 들어온 가족은 이 친구의 기록을 함께 보고 써요."
      />
      <div className={s.twoGrid}>
        <section className={s.panel}>
          <div className={s.panelHeading}>
            <h2>함께하는 가족</h2>
            <span className="badge">{1 + family.members.length}명</span>
          </div>
          <PersonRow person={family.owner} note="기록과 가족을 관리해요." badge="소유주" />
          {family.members.map((member) => (
            <PersonRow
              key={member.memberId}
              person={member}
              note={`${dateTime(member.joinedAt)}부터 함께`}
              badge="가족"
            />
          ))}
        </section>
        <section className={s.panel}>
          <div className={s.panelHeading}>
            <h2>가족 초대</h2>
          </div>
          {code ? (
            <>
              <p><strong style={{ fontSize: 28, letterSpacing: 4 }}>{code.code}</strong></p>
              <p>
                {dateTime(code.expiresAt)}까지 한 번만 쓸 수 있어요.
                이 화면을 나가면 코드를 다시 볼 수 없어요.
              </p>
              <button
                type="button"
                className="button secondary"
                onClick={() => void navigator.clipboard.writeText(code.code)}
              >
                <Icon name="copy" size={16} />
                코드 복사
              </button>
            </>
          ) : (
            <>
              <p>코드를 만들어 가족에게 전해 주세요. 받은 사람은 [초대 코드로 참여]에서 입력해요.</p>
              <button type="button" className="button primary" disabled={busy} onClick={() => void issue()}>
                초대 코드 만들기
              </button>
            </>
          )}
          {issueError && <p className="error" role="alert">{issueError}</p>}
        </section>
      </div>
    </>
  );
}

function PersonRow({ person, note, badge }: { person: FamilyPerson; note: string; badge: string }) {
  const name = person.nickname ?? "알 수 없는 회원";
  return (
    <div className={s.memberRow}>
      <span className={s.avatar}>{name.slice(0, 1)}</span>
      <div>
        <strong>{name}{person.tag ? ` #${person.tag}` : ""}</strong>
        <small>{note}</small>
      </div>
      <span className="badge">{badge}</span>
    </div>
  );
}