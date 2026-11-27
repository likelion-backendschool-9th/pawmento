"use client";
import Link from "next/link";
import { useEffect, useState, useSyncExternalStore } from "react";
import { useApp } from "@/app/AppProvider";
import { Icon } from "@/components/Icon";
import { useLife } from "@/features/shell/hooks/useLife";
import { LifeHeading, DemoNote, lifeStyles as s } from "@/features/shell/components/LifeUi";
type InstallEvent = Event & {
  prompt: () => Promise<void>;
  userChoice: Promise<{ outcome: string }>;
};
const subscribe = (callback: () => void) => {
  window.addEventListener("online", callback);
  window.addEventListener("offline", callback);
  return () => {
    window.removeEventListener("online", callback);
    window.removeEventListener("offline", callback);
  };
};
export function SettingsScreen() {
  const app = useApp();
  const life = useLife();
  const [install, setInstall] = useState<InstallEvent | null>(null);
  const offline = useSyncExternalStore(
    subscribe,
    () => !navigator.onLine,
    () => false,
  );
  useEffect(() => {
    const listener = (event: Event) => {
      event.preventDefault();
      setInstall(event as InstallEvent);
    };
    window.addEventListener("beforeinstallprompt", listener);
    if (process.env.NODE_ENV === "production" && "serviceWorker" in navigator)
      void navigator.serviceWorker
        .register("/service-worker.js")
        .catch(() => undefined);
    return () => window.removeEventListener("beforeinstallprompt", listener);
  }, []);
  return (
    <>
      <LifeHeading
        label="PREFERENCES"
        title="설정과 서비스 안내"
        body="나에게 맞는 기록 공간을 차분히 준비해요."
      />
      <div className={s.twoGrid}>
        <section className={s.panel}>
          <h2>앱처럼 사용하기</h2>
          <p>
            설치를 지원하는 브라우저에서는 PawMento를 홈 화면에 추가할 수
            있어요.
          </p>
          <button
            className="button primary"
            disabled={!install}
            onClick={async () => {
              if (!install) return;
              await install.prompt();
              const choice = await install.userChoice;
              app.notify(
                choice.outcome === "accepted"
                  ? "설치를 시작했어요."
                  : "설치를 취소했어요.",
              );
              setInstall(null);
            }}
          >
            <Icon name="download" size={16} />홈 화면에 설치
          </button>
          {!install && (
            <p className={s.meta}>
              버튼이 활성화되지 않으면 브라우저 메뉴의 ‘앱 설치’ 또는 ‘홈 화면에
              추가’를 확인해 주세요.
            </p>
          )}
          <div className={s.statusLine}>
            <span className={offline ? s.offlineDot : s.onlineDot} />
            {offline ? "현재 오프라인이에요." : "현재 온라인이에요."}
          </div>
          <Link className="text-link" href="/offline">
            오프라인 안내 보기
            <Icon name="arrow" size={15} />
          </Link>
        </section>
        <section className={s.panel}>
          <h2>서비스 공지</h2>
          {life.state?.notices
            .filter((notice) => notice.published)
            .map((notice) => (
              <details key={notice.id} className={s.notice}>
                <summary>{notice.title}</summary>
                <p>{notice.body}</p>
              </details>
            ))}
          {!life.state?.notices.some((notice) => notice.published) && (
            <p className="muted">등록된 공지가 없어요.</p>
          )}
        </section>
        <section className={s.panel}>
          <h2>내 계정과 활동</h2>
          <Link className={s.sourceLink} href="/account">
            <strong>내 정보 관리</strong>
            <Icon name="chevron" size={16} />
          </Link>
          <Link className={s.sourceLink} href="/me/activity">
            <strong>내가 쓴 글 · 댓글 · 북마크</strong>
            <Icon name="chevron" size={16} />
          </Link>
          <Link className={s.sourceLink} href="/reset-password">
            <strong>데모 비밀번호 변경</strong>
            <Icon name="chevron" size={16} />
          </Link>
        </section>
      </div>
      <DemoNote>
        외부 푸시 알림과 자동 음성 재생은 사용하지 않아요. 계정은 현재 탭의
        메모리, 기록과 공유 상태는 이 브라우저에 저장되는 데모입니다. 브라우저
        데이터를 삭제하면 복구할 수 없습니다.
      </DemoNote>
    </>
  );
}
