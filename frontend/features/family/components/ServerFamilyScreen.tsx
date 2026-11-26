"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { useApp } from "@/app/AppProvider";
import { Icon } from "@/components/Icon";
import { ApplicationError } from "@/lib/application/ApplicationError";
import { lifeStyles as s } from "../../shell/components/LifeUi";
import { PageHeading } from "../../shell/components/WorkspaceUi";
import { messageOf } from "../../shell/utils/presentation";
import type { FamilyList, FamilyPerson, InviteCode } from "../types/serverFamily";

type LoadError = { message: string; retry: boolean };

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
  const { account } = useApp();
  // 계정이나 반려동물이 바뀌면 새로 그려서 앞의 목록·초대 코드가 남지 않게 한다
  return <FamilyBody key={`${account?.id ?? "none"}:${petId}`} petId={petId} />;
}

function FamilyBody({ petId }: { petId: string }) {
  const { account, sessionReady, sessionSource, dependencies, notify } = useApp();
  const router = useRouter();
  const [family, setFamily] = useState<FamilyList | null>(null);
  const [error, setError] = useState<LoadError | null>(null);
  const [loginRequired, setLoginRequired] = useState(false);
  const [code, setCode] = useState<InviteCode | null>(null);
  const [notice, setNotice] = useState("");
  const [issueError, setIssueError] = useState("");
  const [busy, setBusy] = useState(false);
  const [attempt, setAttempt] = useState(0);
  // 새로: 확인을 기다리는 대상(가족의 memberId). 같은 값을 한 번 더 누르면 실행한다
  const [confirming, setConfirming] = useState<string | null>(null);
  const [actionError, setActionError] = useState("");
  const href = `/pets/manage/${encodeURIComponent(petId)}/family`;
  const server = sessionReady && sessionSource === "http" && !!account;

  // 상태는 서버 응답이 온 뒤의 콜백에서만 바꾼다
  useEffect(() => {
    if (!server) return;
    let active = true;
    dependencies.family.list(petId).then(
      (result) => {
        if (active) setFamily(result);
      },
      (cause) => {
        if (!active) return;
        if (cause instanceof ApplicationError && cause.code === "UNAUTHORIZED") {
          setLoginRequired(true);
          return;
        }
        // 403·404는 다시 불러도 같으므로 다시 시도를 보이지 않는다
        const final = cause instanceof ApplicationError && (cause.code === "FORBIDDEN" || cause.code === "NOT_FOUND");
        setError({ message: messageOf(cause), retry: !final });
      },
    );
    return () => {
      active = false;
    };
  }, [server, dependencies, petId, attempt]);

  async function issue() {
    setBusy(true);
    setNotice("");
    setIssueError("");
    try {
      setCode(await dependencies.family.issueCode(petId));
    } catch (cause) {
      setIssueError(messageOf(cause));
    } finally {
      setBusy(false);
    }
  }

  function copy(value: string) {
    navigator.clipboard.writeText(value).then(
      () => setNotice("코드를 복사했어요."),
      () => setNotice("복사하지 못했어요. 코드를 직접 옮겨 적어 주세요."),
    );
  }

  // 새로: 소유주가 가족을 내보낸다. 끝나면 목록을 다시 불러온다
  async function removeMember(memberId: string) {
    setBusy(true);
    setActionError("");
    try {
      await dependencies.family.removeMember(petId, memberId);
      setConfirming(null);
      setAttempt((n) => n + 1);
    } catch (cause) {
      setActionError(messageOf(cause));
    } finally {
      setBusy(false);
    }
  }

  // 새로: 가족이 스스로 나간다. 나가면 이 화면을 볼 수 없으니 목록으로 간다
  async function leave() {
    setBusy(true);
    setActionError("");
    try {
      await dependencies.family.leave(petId);
      notify("가족에서 나왔어요.");
      router.push("/pets");
    } catch (cause) {
      setActionError(messageOf(cause));
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
        <span>{error.message}</span>{" "}
        {error.retry && (
          <button type="button" className="text-link" onClick={() => { setError(null); setAttempt((n) => n + 1); }}>
            다시 시도
          </button>
        )}
      </div>
    );
  }
  if (!family) {
    return <p className="loading-bar" role="status">가족을 불러오고 있어요…</p>;
  }

  // 새로: 내가 소유주인지에 따라 버튼이 달라진다
  const isOwner = family.owner.memberId === account?.id;

  function actionFor(memberId: string) {
    const isMe = memberId === account?.id;
    if (!isOwner && !isMe) return null;
    const label = isMe ? "나가기" : "내보내기";
    if (confirming !== memberId) {
      return (
        <button type="button" className="text-link" disabled={busy} onClick={() => setConfirming(memberId)}>
          {label}
        </button>
      );
    }
    return (
      <span>
        <button
          type="button"
          className="text-link"
          disabled={busy}
          onClick={() => void (isMe ? leave() : removeMember(memberId))}
        >
          정말 {label}
        </button>{" "}
        <button type="button" className="text-link" disabled={busy} onClick={() => setConfirming(null)}>
          취소
        </button>
      </span>
    );
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
              action={actionFor(member.memberId)}
            />
          ))}
          {actionError && <p className="error" role="alert">{actionError}</p>}
        </section>
        <section className={s.panel}>
          <div className={s.panelHeading}>
            <h2>가족 초대</h2>
          </div>
          {code ? (
            <>
              <div className={s.inviteLink}>
                <strong>{code.code}</strong>
              </div>
              <p>
                {dateTime(code.expiresAt)}까지 한 번만 쓸 수 있어요.
                이 화면을 나가면 코드를 다시 볼 수 없어요.
              </p>
              <button type="button" className="button secondary" onClick={() => copy(code.code)}>
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
          {notice && <p role="status">{notice}</p>}
          {issueError && <p className="error" role="alert">{issueError}</p>}
        </section>
      </div>
    </>
  );
}

function PersonRow({ person, note, badge, action }: {
  person: FamilyPerson;
  note: string;
  badge: string;
  action?: React.ReactNode;
}) {
  const name = person.nickname ?? "알 수 없는 회원";
  return (
    <div className={s.memberRow}>
      <span className={s.avatar}>{name.slice(0, 1)}</span>
      <div>
        <strong>{name}{person.tag ? ` #${person.tag}` : ""}</strong>
        <small>{note}</small>
      </div>
      <span className="badge">{badge}</span>
      {action}
    </div>
  );
}