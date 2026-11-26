"use client";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useId, useRef, useState } from "react";
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
  const [email, setEmail] = useState("");
  const [verification] = useState(() => app.dependencies.emailVerificationPreview.createSession());
  const [challenge, setChallenge] = useState<Awaited<ReturnType<typeof verification.send>> | null>(null);
  const [code, setCode] = useState("");
  const [verified, setVerified] = useState(false);
  const [verificationBusy, setVerificationBusy] = useState(false);
  const [verificationError, setVerificationError] = useState("");
  const [now, setNow] = useState(0);
  const emailRef = useRef<HTMLInputElement>(null);
  const emailId = useId();
  const codeId = useId();
  const hintId = useId();
  const revision = useRef(0);
  const pendingVerification = useRef(false);
  useEffect(() => {
    if (!challenge) return;
    const timer = setInterval(() => {
      const timestamp = Date.now();
      setNow(timestamp);
      if (timestamp >= challenge.expiresAt) setVerified(false);
    }, 1000);
    return () => clearInterval(timer);
  }, [challenge]);
  useEffect(() => () => { revision.current += 1; }, []);
  const remaining = challenge ? Math.max(0, Math.ceil((challenge.expiresAt - now) / 1000)) : 0;
  const resendRemaining = challenge ? Math.max(0, Math.ceil((challenge.resendAt - now) / 1000)) : 0;

  const preview = search.get("preview") === "1";
  const verifiedForPreview = preview && verified;
  const previewQuery = preview ? "&preview=1" : "";
  const target = search.get("returnTo") ?? returnTo;
  const destination =
    target.startsWith("/") && !target.startsWith("//") && !target.includes("\\")
      ? target
      : "/home";
  async function sendCode() {
    if (!preview || pendingVerification.current || !emailRef.current?.reportValidity()) return;
    const current = revision.current;
    pendingVerification.current = true; setVerificationBusy(true); setVerificationError("");
    try {
      const next = await verification.send(email);
      if (current !== revision.current) return;
      setNow(Date.now()); setChallenge(next); setCode(""); setVerified(false); setError("");
    } catch (cause) { if (current === revision.current) setVerificationError(cause instanceof Error ? cause.message : "코드를 요청하지 못했어요."); }
    finally { pendingVerification.current = false; setVerificationBusy(false); }
  }
  async function verifyCode() {
    if (!preview || pendingVerification.current) return;
    const current = revision.current;
    pendingVerification.current = true; setVerificationBusy(true); setVerificationError("");
    try {
      await verification.verify(email, code);
      if (current !== revision.current) return;
      setVerified(true); setCode(""); setError("");
    } catch (cause) { if (current === revision.current) setVerificationError(cause instanceof Error ? cause.message : "인증 코드를 확인해 주세요."); }
    finally { pendingVerification.current = false; setVerificationBusy(false); }
  }
  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (signUp && (!preview || !verification.isVerified(email))) {
      setError("이메일 인증을 먼저 완료해 주세요.");
      return;
    }
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
            <div className={styles.emailField}>
              <label htmlFor={emailId}>이메일</label>
              <div className={styles.emailRow}>
                <input id={emailId} ref={emailRef} name="email" type="email" autoComplete="email" inputMode="email" autoCapitalize="none" spellCheck={false} placeholder="이메일 주소를 입력해 주세요" required value={email} onChange={(event) => {
                  revision.current += 1; verification.reset(); setEmail(event.target.value); setChallenge(null); setCode(""); setVerified(false); setVerificationError(""); setError("");
                }} />
                {signUp && <button type="button" className={styles.codeButton} disabled={!preview || !email.trim() || verificationBusy || verifiedForPreview || resendRemaining > 0} onClick={() => void sendCode()}>{verifiedForPreview ? "인증 완료" : verificationBusy ? "확인 중…" : preview && challenge ? resendRemaining > 0 ? `${resendRemaining}초 후 재전송` : "다시 받기" : "인증 코드 받기"}</button>}
              </div>
              {signUp && !preview && <p className={styles.emailHelp}>이메일 인증 연결을 준비 중이에요. 아래 화면 체험에서 미리 확인할 수 있어요.</p>}
              {signUp && preview && challenge && !verified && <div className={styles.verificationField}>
                <label htmlFor={codeId}>인증 코드<span className={styles.codeTime} role="timer" aria-label="인증 코드 남은 시간">{Math.floor(remaining / 60)}:{String(remaining % 60).padStart(2, "0")}</span></label>
                <div className={styles.emailRow}><input id={codeId} aria-label="인증 코드" aria-describedby={hintId} value={code} onChange={(event) => { setCode(event.target.value.replace(/\D/g, "").slice(0, 6)); setVerificationError(""); }} placeholder="6자리 숫자를 입력해 주세요" inputMode="numeric" autoComplete="one-time-code" maxLength={6} /><button type="button" className={styles.codeButton} disabled={code.length !== 6 || verificationBusy || remaining === 0} onClick={() => void verifyCode()}>확인</button></div>
                <p id={hintId} className={styles.emailHelp}>체험용 코드 <strong>{challenge.demoCode}</strong> · 실제 메일은 발송되지 않아요.</p>
                {remaining === 0 && <p className={styles.emailError} role="status">인증 코드가 만료됐어요. 다시 받아 주세요.</p>}
              </div>}
              {signUp && verifiedForPreview && <p className={styles.emailSuccess} role="status"><Icon name="check" size={15} />이메일 인증을 완료했어요.</p>}
              {signUp && preview && verificationError && <p className={styles.emailError} role="alert">{verificationError}</p>}
            </div>
            <PasswordField name="password" label="비밀번호" signUp={signUp} />
            {signUp && <PasswordField name="confirmation" label="비밀번호 확인" signUp />}
            {!signUp && <Link className={styles.forgot} href="/reset-password">비밀번호를 잊으셨나요?</Link>}
            {error && <p className={styles.error} role="alert">{error}</p>}
            <button className={styles.primary} type="submit" disabled={busy || verificationBusy || (signUp && !verifiedForPreview)}>{busy ? "잠시만 기다려 주세요…" : signUp ? "가입하고 계속하기" : "로그인"}<Icon name="arrow" size={18} /></button>
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
