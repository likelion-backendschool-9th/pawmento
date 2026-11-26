"use client";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { useApp } from "@/app/AppProvider";
import { Brand } from "@/components/Brand";
import { Icon } from "@/components/Icon";
import { PhotoPicker } from "@/components/PhotoPicker";
import { isDemoAccount } from "../types/account";
import type { PetInput } from "@/features/pet/types/Pet";
import { COMPANION_KINDS } from "@/features/pet/types/companionKinds";
import { BirthdayDateField } from "@/features/pet/components/BirthdayDateField";
import { today } from "@/lib/date";
import { useUnsavedChanges } from "@/features/shell/hooks/useUnsavedChanges";
import s from "./OnboardingScreen.module.css";

export type OnboardingStep = "choose" | "register" | "join";

export function OnboardingScreen({ step }: { step: OnboardingStep }) {
  const app = useApp();
  if (!app.sessionReady) return <main className="route-loading" role="status">기록 공간을 준비하고 있어요…</main>;
  if (!app.account || !isDemoAccount(app.account)) return <main className={s.screen}><Brand /><h1>첫 시작을 미리 만나보세요</h1><p>실제 계정과 구분되는 화면 체험이에요.</p><Link className={s.primary} href="/sign-in?preview=1">로그인 화면 체험하기</Link></main>;
  return <OnboardingBody key={`${app.account.id}:${step}`} step={step} />;
}

function OnboardingBody({ step }: { step: OnboardingStep }) {
  const app = useApp();
  const router = useRouter();
  const [input, setInput] = useState<PetInput>({ name: "", species: "DOG", breed: "", birthday: "", photoUrl: "" });
  const [kind, setKind] = useState<(typeof COMPANION_KINDS)[number]["id"]>("DOG");
  const [birthdayUnknown, setBirthdayUnknown] = useState(false);
  const [code, setCode] = useState("");
  const [busy, setBusy] = useState(false);
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState("");
  const pending = useRef(false);
  const mounted = useRef(false);
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);
  const [memberPets] = useState(app.state.pets.length);
  const invitation = app.dependencies.onboardingPreview.invitation;
  useUnsavedChanges(!saved && step === "register" && Boolean(input.name || input.birthday || input.photoUrl || kind !== "DOG" || birthdayUnknown));

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    if (pending.current || !app.account || !isDemoAccount(app.account)) return;
    pending.current = true; setBusy(true); setError("");
    try {
      if (step === "register") {
        if (input.birthday && input.birthday > today()) throw Error("생일은 오늘 또는 이전 날짜로 선택해 주세요.");
        await app.dependencies.workspace.savePet(app.account.id, input);
      } else await app.dependencies.onboardingPreview.join(app.account, code);
      if (!mounted.current) return;
      setSaved(true);
      await app.refresh();
      if (!mounted.current) return;
      router.replace("/home");
    } catch (cause) { if (mounted.current) setError(cause instanceof Error ? cause.message : "다시 시도해 주세요."); }
    finally { pending.current = false; if (mounted.current) setBusy(false); }
  }

  return <main id="main" className={s.screen}>
    <div className={s.brand}><Brand /></div>
    <section className={s.content}>
      <header className={s.heading}>
        <h1>{step === "choose" ? "어떻게 시작할까요?" : step === "join" ? "함께할 초대가 도착했어요" : "우리 아이를 소개해 주세요"}</h1>
        <p>{step === "choose" ? "처음 만드는 공간도, 함께 나누는 공간도 좋아요." : step === "join" ? "초대한 분에게 받은 코드를 입력해 주세요." : "이름과 사진으로 첫 기억을 시작해요."}</p>
      </header>
      {step === "choose" ? <div className={s.choices}>
        <Link className={s.choice} href="/welcome/register"><span className={s.choiceIcon}><Icon name="paw" size={26} /></span><span><strong>처음 기록해요</strong><small>아이의 프로필을 등록하고<br />우리만의 기록 공간을 만들어요.</small></span><Icon name="chevron" size={18} /></Link>
        <Link className={`${s.choice} ${s.inviteChoice}`} href="/welcome/join"><span className={s.choiceIcon}><Icon name="mail" size={26} /></span><span><strong>초대받았어요</strong><small>초대 코드로 들어가<br />소중한 순간을 함께 남겨요.</small></span><Icon name="chevron" size={18} /></Link>
        {memberPets > 0 && <Link className={s.continue} href="/home">이미 함께하는 아이의 홈으로 →</Link>}
      </div> : <form onSubmit={submit} className={s.form}>
        <fieldset disabled={busy}>
          {step === "register" ? <>
            <div className={s.photo}><PhotoPicker value={input.photoUrl} onChange={(photoUrl) => setInput((old) => ({ ...old, photoUrl }))} /></div>
            <label>이름<input value={input.name} onChange={(event) => setInput({ ...input, name: event.target.value })} placeholder="아이의 이름을 알려주세요" maxLength={20} required /></label>
            <div className={s.field}>
              <span className={s.fieldLabel} id="companion-kind-label">어떤 친구인가요?</span>
              <div className={s.kindChoices} role="group" aria-labelledby="companion-kind-label">
                {COMPANION_KINDS.map((option) => <button type="button" key={option.id} aria-pressed={kind === option.id} onClick={() => { setSaved(false); setKind(option.id); setInput((old) => ({ ...old, species: option.species, speciesDetail: option.species === "OTHER" ? option.label : undefined })); }}>{option.label}{kind === option.id && <Icon name="check" size={13} />}</button>)}
              </div>
            </div>
            <div className={s.field}>
              <span className={s.fieldLabel} id="birthday-choice-label">생일</span>
              <div className={s.birthdayChoices} role="group" aria-labelledby="birthday-choice-label">
                <button type="button" aria-pressed={!birthdayUnknown} onClick={() => { setSaved(false); setBirthdayUnknown(false); }}>생일 선택</button>
                <button type="button" aria-pressed={birthdayUnknown} onClick={() => { setSaved(false); setBirthdayUnknown(true); setInput((old) => ({ ...old, birthday: "" })); }}>생일을 몰라요</button>
              </div>
              {!birthdayUnknown && <BirthdayDateField value={input.birthday} onChange={(birthday) => { setSaved(false); setInput((old) => ({ ...old, birthday })); }} />}
            </div>
          </> : <>
            <div className={s.envelope} aria-hidden="true"><Icon name="mail" size={52} /><span><Icon name="paw" size={22} /></span></div>
            <label>초대 코드<input className={s.code} value={code} onChange={(event) => { setCode(event.target.value.toUpperCase()); setError(""); }} placeholder="8자리 코드를 입력해 주세요" autoComplete="off" autoCapitalize="characters" spellCheck={false} maxLength={8} required /></label>
            <div className={s.demoCode}><span>체험용 코드</span><button type="button" onClick={() => { setCode(invitation.code); setError(""); }}>{invitation.code}<small>입력하기</small></button></div>
          </>}
          {error && <p className={s.error} role="alert">{error}</p>}
          <button className={s.primary} type="submit" disabled={busy}>{busy ? "기록 공간을 여는 중…" : step === "join" ? "함께 기록 시작하기" : "우리의 기록 시작하기"}<Icon name="arrow" size={18} /></button>
        </fieldset>
      </form>}
    </section>
    <p className={s.notice}>화면 체험이에요. 실제 계정이나 초대에 연결되지 않아요.</p>
  </main>;
}
