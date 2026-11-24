"use client";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { useApp } from "@/app/AppProvider";
import { Brand } from "@/components/Brand";
import { Icon } from "@/components/Icon";
import { Photo } from "@/components/Photo";
import { QuestionAnswer } from "@/components/QuestionAnswer";
import styles from "./LandingScreen.module.css";
export function LandingScreen() {
  const app = useApp();
  const router = useRouter();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  async function openDemo() {
    if (app.account) {
      router.push("/home");
      return;
    }
    setBusy(true);
    setError("");
    try {
      await app.startDemo();
      router.push("/home");
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "다시 시도해 주세요.");
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <Brand />
        <nav>
          <a href="#features">함께할 수 있는 것들</a>
          <Link href="/sign-in">로그인</Link>
          <Link className="button primary" href="/sign-up">
            기록 시작하기
            <Icon name="arrow" size={15} />
          </Link>
        </nav>
      </header>
      <main>
        <section className={styles.hero}>
          <div className={styles.copy}>
            <span className={styles.tag}>
              <Icon name="paw" size={15} />
              반려동물과 함께 쓰는 라이프 다이어리
            </span>
            <h1>
              질문 하나로 시작하는
              <br />
              <em>우리의 소중한 오늘.</em>
            </h1>
            <p>
              오늘을 묻는 질문과 나의 답변, 사진과 목소리까지.
              <br />
              함께한 모든 순간을 한곳에 차곡차곡 간직하세요.
            </p>
            <div className={styles.actions}>
              <button
                className="button primary"
                disabled={busy}
                onClick={() => void openDemo()}
              >
                {busy
                  ? "기록 공간을 여는 중…"
                  : app.account
                    ? "대시보드로 돌아가기"
                    : "우리의 기록 둘러보기"}
                <Icon name="arrow" size={17} />
              </button>
              <Link href="/sign-up" className="text-link">
                나만의 기록 시작하기
              </Link>
            </div>
            {error && (
              <p className="error" role="alert">
                {error}
              </p>
            )}
            <small>가입 없이 모든 화면을 둘러볼 수 있는 브라우저 데모</small>
          </div>
          <div className={styles.preview}>
            <div className={styles.previewBar}>
              <span />
              <span />
              <span />
              <strong>오늘의 질문 다이어리</strong>
              <small>미리보기</small>
            </div>
            <div className={styles.previewContent}>
              <Photo
                src="reference:running"
                alt="잔디밭에서 공을 쫓는 코기"
                className={styles.previewPhoto}
              />
              <QuestionAnswer
                question="오늘 우리 아이가 가장 신났던 순간은?"
                answer="공을 쫓아 달리던 모습. 내내 꼬리를 흔들며 웃어주던 그 순간이 오래 기억날 것 같아요."
                compact
              />
              <div className={styles.previewSaved}>
                <Icon name="check" size={13} />
                질문과 답변을 함께 간직했어요.
              </div>
            </div>
          </div>
        </section>
        <section className={styles.features} id="features">
          <header>
            <span>EVERY MOMENT, TOGETHER</span>
            <h2>오늘의 기록부터, 오래 간직할 기억까지.</h2>
            <p>
              PC에서는 넓게, 모바일에서는 가볍게. 우리의 시간을 편안하게 모아요.
            </p>
          </header>
          <div className={styles.featureGrid}>
            {[
              [
                "edit",
                "오늘의 질문 다이어리",
                "매일 도착하는 질문과 나의 답변을 함께 저장해요.",
              ],
              [
                "calendar",
                "달력과 타임라인",
                "그날의 질문과 마음을 날짜별로 다시 펼쳐봐요.",
              ],
              [
                "camera",
                "사진·영상·목소리",
                "소중한 순간의 모습과 소리를 한곳에 모아요.",
              ],
              [
                "leaf",
                "기억의 정원",
                "쌓인 기억을 천천히 돌아보는 조용한 공간이에요.",
              ],
              [
                "users",
                "커뮤니티와 가족",
                "서로의 경험을 나누고 가족과 함께 기록해요.",
              ],
              [
                "mail",
                "기억의 우체통",
                "기록을 골라 데모 엽서로 엮고 보관할 수 있어요.",
              ],
            ].map(([icon, title, body]) => (
              <article key={title}>
                <span>
                  <Icon name={icon} size={23} />
                </span>
                <h3>{title}</h3>
                <p>{body}</p>
              </article>
            ))}
          </div>
        </section>
      </main>
      <footer className={styles.footer}>
        <Brand />
        <p>우리의 오늘을, 오래도록.</p>
        <small>프론트엔드 데모 · 외부 전송 및 실제 AI 호출 없음</small>
      </footer>
    </div>
  );
}
