"use client";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useState } from "react";
import { useApp } from "@/app/AppProvider";
import { Brand } from "@/shared/ui/Brand";

import { Icon } from "@/shared/ui/Icon";
import styles from "./AuthScreen.module.css";
export function AuthScreen({
  signUp = false,
  returnTo = "/home",
}: {
  signUp?: boolean;
  returnTo?: string;
}) {
  const app = useApp();
  const router = useRouter();
  const search = useSearchParams();
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const target = search.get("returnTo") ?? returnTo;
  const destination =
    target.startsWith("/") && !target.startsWith("//") && !target.includes("\\")
      ? target
      : "/home";
  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setBusy(true);
    const data = new FormData(event.currentTarget);
    try {
      const input = {
        email: String(data.get("email")),
        password: String(data.get("password")),
      };
      const account = signUp
        ? await app.dependencies.auth.signUp({
            ...input,
            nickname: String(data.get("nickname")),
            confirmation: String(data.get("confirmation")),
          })
        : await app.dependencies.auth.signIn(input);
      await app.authenticate(account);
      router.push(signUp ? "/pets/new" : destination);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "로그인에 실패했어요.");
    } finally {
      setBusy(false);
    }
  }
  async function demo() {
    setError("");
    setBusy(true);
    try {
      await app.startDemo();
      router.push(destination);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "다시 시도해 주세요.");
    } finally {
      setBusy(false);
    }
  }
  return (
    <main id="main" className={styles.auth}>
      <section className={styles.art}>
        <Brand />
        <div>
          <div className={styles.authPreview}>
            <span>오늘의 질문</span>
            <h3>오늘 함께하며 가장 행복했던 순간은?</h3>
            <p>
              “산책길에서 나를 올려다보며 웃던 얼굴. 그 평범한 순간을 오래
              기억하고 싶어요.”
            </p>
            <small>질문과 답변을 함께 간직하는 우리만의 다이어리</small>
          </div>
          <h2>우리의 이야기가 자라는 곳.</h2>
          <p>오늘도 함께해 줘서 고마워.</p>
        </div>
        <small>PAWMENTO · MOMENTS INTO MEMORIES</small>
      </section>
      <section className={styles.formPanel}>
        <Link className="text-link" href="/">
          ← 처음으로
        </Link>
        <div className={styles.formContent}>
          <p className="eyebrow">{signUp ? "A NEW CHAPTER" : "WELCOME BACK"}</p>
          <h1>{signUp ? "첫 이야기를 시작해요" : "다시 만나 반가워요"}</h1>
          <p className="muted">
            {signUp
              ? "반려동물과 함께하는 작은 기록 습관."
              : "차곡차곡 모아둔 우리의 하루를 만나보세요."}
          </p>
          <div className="demo-banner">
            <Icon name="leaf" size={18} />
            <span>
              브라우저 데모입니다. 계정은 현재 탭에서만 유효하며 새로고침하면
              다시 가입해야 해요. 실제 비밀번호는 사용하지 마세요.
            </span>
          </div>
          <form onSubmit={submit} className="form-stack">
            {signUp && (
              <label>
                닉네임
                <input
                  name="nickname"
                  autoComplete="nickname"
                  placeholder="어떻게 불러드릴까요?"
                  required
                  maxLength={20}
                />
              </label>
            )}
            <label>
              이메일
              <input
                name="email"
                type="email"
                autoComplete="email"
                placeholder="hello@pawmento.com"
                required
              />
            </label>
            <label>
              비밀번호
              <input
                name="password"
                type="password"
                autoComplete={signUp ? "new-password" : "current-password"}
                placeholder="8자 이상 입력해 주세요"
                required
                minLength={8}
                maxLength={72}
              />
            </label>
            {signUp && (
              <label>
                비밀번호 확인
                <input
                  name="confirmation"
                  type="password"
                  autoComplete="new-password"
                  placeholder="한 번 더 입력해 주세요"
                  required
                  minLength={8}
                />
              </label>
            )}
            {error && (
              <p className="error" role="alert">
                {error}
              </p>
            )}
            <button className="button primary full" disabled={busy}>
              {busy
                ? "잠시만 기다려 주세요…"
                : signUp
                  ? "가입하고 시작하기"
                  : "로그인"}
              <Icon name="arrow" size={18} />
            </button>
          </form>
          <div className={styles.separator}>또는</div>
          <div className={styles.socialButtons}>
            {(
              [
                ["google", "Google"],
                ["kakao", "카카오"],
              ] as const
            ).map(([key, label]) => (
              <button
                key={key}
                type="button"
                className="button secondary"
                disabled={busy}
                onClick={async () => {
                  setBusy(true);
                  setError("");
                  try {
                    await app.authenticate(
                      await app.dependencies.auth.signInDemoProvider(key),
                    );
                    router.push(destination);
                  } catch (cause) {
                    setError(
                      cause instanceof Error
                        ? cause.message
                        : "로그인하지 못했어요.",
                    );
                  } finally {
                    setBusy(false);
                  }
                }}
              >
                {label} 데모 로그인
              </button>
            ))}
          </div>
          <Link className="text-link" href="/reset-password">
            데모 비밀번호 변경
          </Link>
          <button
            className="button secondary full"
            disabled={busy}
            onClick={demo}
          >
            가입 없이 데모 둘러보기
          </button>
          <p className={styles.switch}>
            {signUp ? "이미 이 탭에서 가입했나요?" : "아직 계정이 없나요?"}{" "}
            <Link className="text-link" href={signUp ? "/sign-in" : "/sign-up"}>
              {signUp ? "로그인" : "회원가입"}
            </Link>
          </p>
        </div>
      </section>
    </main>
  );
}
