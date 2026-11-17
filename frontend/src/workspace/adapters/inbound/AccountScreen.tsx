"use client";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { useApp } from "@/app/AppProvider";
import { Icon } from "@/shared/ui/Icon";
import styles from "./Workspace.module.css";
import { PageHeading } from "./WorkspaceUi";
import { messageOf } from "./presentation";

export function AccountScreen() {
  const app = useApp();
  const router = useRouter();
  const [nickname, setNickname] = useState(app.account!.nickname);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  async function save(event: React.FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError("");
    try {
      const account = await app.dependencies.auth.update(nickname);
      await app.authenticate(account);
      app.notify("닉네임을 변경했어요.");
    } catch (cause) {
      setError(messageOf(cause));
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <PageHeading
        eyebrow="MY LITTLE SPACE"
        title="나의 작은 공간"
        body="함께 기록하는 당신의 이야기."
      />
      <div className={styles.accountGrid}>
        <section className={styles.accountCard}>
          <span className={styles.largeAvatar}>
            {app.account!.nickname.slice(0, 1)}
          </span>
          <h2>{app.account!.nickname}</h2>
          <p className="muted">{app.account!.email}</p>
          <form onSubmit={save} className="form-stack">
            <label>
              닉네임
              <input
                value={nickname}
                onChange={(event) => setNickname(event.target.value)}
                required
                maxLength={20}
              />
            </label>
            {error && (
              <p className="error" role="alert">
                {error}
              </p>
            )}
            <button className="button primary" disabled={busy}>
              {busy ? "저장 중…" : "내 정보 저장"}
            </button>
          </form>
          <button
            className={styles.logout}
            onClick={async () => {
              await app.signOut();
              router.push("/");
            }}
          >
            <Icon name="logout" size={17} />
            로그아웃
          </button>
          <button
            className="text-link danger"
            style={{ marginTop: 18 }}
            disabled={busy}
            onClick={async () => {
              if (
                !confirm(
                  "이 데모 계정과 직접 작성한 기록을 삭제할까요? 다른 가족의 원본 기록은 유지됩니다.",
                )
              )
                return;
              setBusy(true);
              setError("");
              try {
                await app.dependencies.workspace.clear(app.account!.id);
                await app.dependencies.life.withdrawActor({
                  id: app.account!.id,
                  name: app.account!.nickname,
                  isAdmin: app.account!.role === "ADMIN",
                });
                await app.dependencies.auth.withdraw();
                await app.signOut();
                router.push("/");
              } catch (cause) {
                setError(
                  cause instanceof Error ? cause.message : "처리하지 못했어요.",
                );
              } finally {
                setBusy(false);
              }
            }}
          >
            데모 계정 탈퇴
          </button>
        </section>
        <section className={styles.accountInfo}>
          <h2>차곡차곡 쌓인 우리의 시간</h2>
          <Link href="/me/activity" className="text-link">
            내 글 · 댓글 · 북마크 보기
          </Link>
          <div className={styles.accountStats}>
            <div>
              <strong>{app.state.pets.length}</strong>
              <span>함께하는 친구</span>
            </div>
            <div>
              <strong>{app.state.records.length}</strong>
              <span>남긴 이야기</span>
            </div>
          </div>
          <Link className="button secondary full" href="/pets">
            반려동물 관리
            <Icon name="arrow" size={17} />
          </Link>
          <div className="demo-banner">
            <Icon name="leaf" />
            <span>
              프론트엔드 데모 안내
              <br />
              계정은 현재 탭의 메모리에서만 유지됩니다. 기록은 계정별로 이
              브라우저에 보관되며, 브라우저 데이터를 지우면 복구할 수 없어요.
              서버 인증·사진 업로드는 아직 연결되지 않았습니다.
            </span>
          </div>
          {app.account!.id === "demo" && (
            <small className="muted">
              샘플 사진은 제공된 레퍼런스를 사용하는 UI 예시입니다.
            </small>
          )}
        </section>
      </div>
    </>
  );
}
