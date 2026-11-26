"use client";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useId, useState } from "react";
import { useApp } from "@/app/AppProvider";
import { validateCredentials, validateNickname } from "../utils/input";
import { Brand } from "@/components/Brand";

import { Icon } from "@/components/Icon";
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
  const preview = search.get("preview") === "1";
  const previewQuery = preview ? "&preview=1" : "";
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
      if (preview) {
        validateCredentials(input);
        if (signUp) {
          validateNickname(String(data.get("nickname")));
          if (input.password !== String(data.get("confirmation"))) throw Error("비밀번호 확인이 일치하지 않아요.");
        }
        await app.authenticate(await app.dependencies.auth.startDemo({ fresh: true, nickname: signUp ? String(data.get("nickname")) : undefined }));
        router.push("/welcome");
        return;
      }
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
      <header className={styles.top}>
        <Link className={styles.back} href="/" aria-label="처음으로 돌아가기"><span aria-hidden="true">←</span></Link>
        <span>{signUp ? "새로운 시작" : "다시 만나는 우리의 기록"}</span>
      </header>
      <div className={styles.brand}><Brand /></div>
      <section className={styles.formContent}>
        <header className={styles.heading}>
          <span className={styles.mark}><Icon name={signUp ? "leaf" : "paw"} size={20} /></span>
          <h1>{signUp ? "함께할 이야기를 시작해요" : "우리의 기억을 만나러 가요"}</h1>
          <p>{signUp ? "작은 일상부터 오래 간직할 추억까지." : "함께한 순간은 언제나 여기 있어요."}</p>
        </header>
        {preview && <p className={styles.previewNotice}>화면 체험 중이에요. 실제 계정을 만들거나 비밀번호를 저장하지 않아요.</p>}
        <form onSubmit={submit} className={styles.form}>
          <fieldset disabled={busy}>
            {signUp && <label>닉네임<input name="nickname" autoComplete="nickname" placeholder="기록에 남길 이름을 알려주세요" required maxLength={20} /></label>}
            <label>이메일<input name="email" type="email" autoComplete="email" inputMode="email" autoCapitalize="none" spellCheck={false} placeholder="이메일 주소를 입력해 주세요" required /></label>
            <PasswordField name="password" label="비밀번호" signUp={signUp} />
            {signUp && <PasswordField name="confirmation" label="비밀번호 확인" signUp />}
            {!signUp && <Link className={styles.forgot} href="/reset-password">비밀번호를 잊으셨나요?</Link>}
            {error && <p className={styles.error} role="alert">{error}</p>}
            <button className={styles.primary} type="submit" disabled={busy}>{busy ? "잠시만 기다려 주세요…" : signUp ? "가입하고 계속하기" : "로그인"}<Icon name="arrow" size={18} /></button>
          </fieldset>
        </form>
        <p className={styles.switch}>{signUp ? "이미 함께하고 있나요?" : "아직 계정이 없나요?"}<Link href={signUp ? `/sign-in?returnTo=${encodeURIComponent(destination)}${previewQuery}` : `/sign-up?returnTo=${encodeURIComponent(destination)}${previewQuery}`}>{signUp ? "로그인" : "회원가입"}</Link></p>
        <div className={styles.separator}><span>먼저 만나보고 싶다면</span></div>
        <button className={styles.demo} type="button" disabled={busy} onClick={demo}><Icon name="book" size={17} />가입 없이 데모 둘러보기<Icon name="chevron" size={15} /></button>
        {!preview && <Link className={styles.previewEntry} href="/sign-in?preview=1">처음 시작하는 화면 체험하기 <span aria-hidden="true">↗</span></Link>}
        <details className={styles.preview}>
          <summary>소셜 로그인 화면 체험</summary>
          <p>실제 소셜 계정에 연결하지 않는 데모예요.</p>
          <div className={styles.socialButtons}>
            {([ ["google", "Google"], ["kakao", "카카오"] ] as const).map(([key, label]) => <button key={key} type="button" disabled={busy} onClick={async () => {
              setBusy(true); setError("");
              try { await app.authenticate(await app.dependencies.auth.signInDemoProvider(key)); router.push(destination); }
              catch (cause) { setError(cause instanceof Error ? cause.message : "로그인하지 못했어요."); }
              finally { setBusy(false); }
            }}>{label} 데모</button>)}
          </div>
        </details>
      </section>
      <footer className={styles.footer}><Icon name="leaf" size={15} /><span>오늘의 마음을, 오래도록.</span></footer>
    </main>
  );
}

function PasswordField({ name, label, signUp = false }: { name: string; label: string; signUp?: boolean }) {
  const [visible, setVisible] = useState(false);
  const id = useId();
  return <div className={styles.passwordField}><label htmlFor={id}>{label}</label><span className={styles.password}>
    <input id={id} name={name} type={visible ? "text" : "password"} autoComplete={signUp ? "new-password" : "current-password"} placeholder={name === "confirmation" ? "비밀번호를 한 번 더 입력해 주세요" : "8자 이상 입력해 주세요"} required minLength={8} maxLength={72} />
    <button type="button" aria-label={`${label} ${visible ? "숨기기" : "보기"}`} aria-pressed={visible} onClick={() => setVisible(!visible)}>{visible ? "숨김" : "보기"}</button>
  </span></div>;
}
