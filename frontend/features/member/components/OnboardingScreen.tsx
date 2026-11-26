"use client";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { useApp } from "@/app/AppProvider";
import { Brand } from "@/components/Brand";
import { Icon } from "@/components/Icon";
import { Photo } from "@/components/Photo";
import { PhotoPicker } from "@/components/PhotoPicker";
import { isDemoAccount } from "../types/account";
import type { Pet, PetInput } from "@/features/pet/types/Pet";
import { COMPANION_KINDS } from "@/features/pet/types/companionKinds";
import { PetKindCombobox } from "@/features/pet/components/PetKindCombobox";
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
  const [input, setInput] = useState<Omit<PetInput, "species"> & { species: PetInput["species"] | null }>({ name: "", species: null, breed: "", birthday: "", photoUrl: "" });
  const [kind, setKind] = useState<(typeof COMPANION_KINDS)[number]["id"] | null>(null);
  const [kindPending, setKindPending] = useState(false);
  const [birthdayUnknown, setBirthdayUnknown] = useState(false);
  const [code, setCode] = useState("");
  const [choosingMode, setChoosingMode] = useState(false);
  const [mode, setMode] = useState<Pet["mode"] | null>(null);
  const createdPetId = useRef<string | undefined>(undefined);
  const [busy, setBusy] = useState(false);
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState("");
  const pending = useRef(false);
  const mounted = useRef(false);
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);
  const [memberPets] = useState(app.state.pets.length);
  const invitation = app.dependencies.onboardingPreview.invitation;
  useUnsavedChanges(!saved && step === "register" && Boolean(input.name || input.birthday || input.photoUrl || kind !== null || kindPending || birthdayUnknown));

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    if (pending.current || !app.account || !isDemoAccount(app.account)) return;
    pending.current = true; setBusy(true); setError("");
    try {
      if (step === "register") {
        if (!kind || !input.species || kindPending) throw Error("동물 종류를 목록에서 선택해 주세요.");
        if (input.birthday && input.birthday > today()) throw Error("생일은 오늘 또는 이전 날짜로 선택해 주세요.");
        if (!choosingMode) { setChoosingMode(true); return; }
        if (!mode) throw Error("시작할 공간을 선택해 주세요.");
        if (!createdPetId.current) {
          const pet = await app.dependencies.workspace.savePet(app.account.id, { ...input, species: input.species });
          createdPetId.current = pet.id;
        }
        await app.dependencies.workspace.changeMode(app.account.id, createdPetId.current, mode);
      } else await app.dependencies.onboardingPreview.join(app.account, code);
      if (!mounted.current) return;
      setSaved(true);
      await app.refresh();
      if (!mounted.current) return;
      router.replace("/home");
    } catch (cause) { if (mounted.current) setError(cause instanceof Error ? cause.message : "다시 시도해 주세요."); }
    finally { pending.current = false; if (mounted.current) setBusy(false); }
  }

  return <main id="main" className={s.screen} data-step={choosingMode ? "mode" : step}>
    <div className={s.brand}><Brand /></div>
    <section className={s.content}>
      {step === "join" && <div className={s.envelope} aria-hidden="true"><Icon name="mail" size={52} /><span><Icon name="paw" size={22} /></span></div>}
      <header className={s.heading}>
        <h1>{choosingMode ? "어떤 공간에서 시작할까요?" : step === "choose" ? "어떻게 시작할까요?" : step === "join" ? "함께할 초대가 도착했어요" : "우리 아이를 소개해 주세요"}</h1>
        <p>{choosingMode ? "함께하는 오늘도, 오래 간직할 기억도 소중하니까요." : step === "choose" ? "처음 만드는 공간도, 함께 나누는 공간도 좋아요." : step === "join" ? "초대한 분에게 받은 코드를 입력해 주세요." : "이름과 사진으로 첫 기억을 시작해요."}</p>
      </header>
      {step === "choose" ? <div className={s.choices}>
        <Link className={s.choice} href="/welcome/register">
          <span className={s.profilePreview} aria-hidden="true"><Photo src="reference:portrait" alt="" /><span><Icon name="paw" size={14} /></span></span>
          <span><strong>처음 기록해요</strong><span className={s.choiceAction}>아이 소개하기 <Icon name="arrow" size={14} /></span></span>
        </Link>
        <Link className={`${s.choice} ${s.inviteChoice}`} href="/welcome/join">
          <span className={s.invitationPreview} aria-hidden="true"><span className={s.invitationPhoto}><Photo src="reference:running" alt="" /></span><span className={s.invitationMail}><Icon name="mail" size={33} /><Icon name="paw" size={12} /></span></span>
          <span><strong>초대받았어요</strong><span className={s.choiceAction}>초대 코드로 함께하기 <Icon name="arrow" size={14} /></span></span>
        </Link>
        <p className={s.choiceNote}>어떤 방식으로 시작해도 괜찮아요.<br />나중에 다른 아이를 등록하거나 초대에 참여할 수 있어요.</p>
        {memberPets > 0 && <Link className={s.continue} href="/home">이미 함께하는 아이의 홈으로 →</Link>}
      </div> : <form onSubmit={submit} className={s.form}>
        <fieldset disabled={busy}>
          {choosingMode ? <>
            <div className={s.modeChoices} role="radiogroup" aria-label="시작할 공간">
              {([{ value: "ACTIVE", title: "일상", caption: "함께하는 오늘을 기록해요", icon: "sun" }, { value: "GARDEN", title: "기억의 정원", caption: "그리운 아이의 추억을 간직해요", icon: "sparkle" }] as const).map((option) => <label key={option.value} className={s.modeChoice} data-mode={option.value}>
                <input type="radio" name="mode" value={option.value} checked={mode === option.value} onChange={() => { setMode(option.value); setError(""); }} required />
                <span className={s.modeArt} aria-hidden="true"><Icon name={option.icon} size={32} /></span>
                <span><strong>{option.title}</strong><small>{option.caption}</small></span>
              </label>)}
            </div>
            <p className={s.modeNote}>선택한 공간은 나중에 반려동물 관리에서 바꿀 수 있어요.</p>
          </> : step === "register" ? <>
            <div className={s.photo}><PhotoPicker value={input.photoUrl} onChange={(photoUrl) => setInput((old) => ({ ...old, photoUrl }))} /></div>
            <label>이름<input value={input.name} onChange={(event) => setInput({ ...input, name: event.target.value })} placeholder="아이의 이름을 알려주세요" maxLength={20} required /></label>
            <PetKindCombobox value={kind} onPendingChange={setKindPending} onChange={(option) => { setSaved(false); setError(""); setKind(option.id); setInput((old) => ({ ...old, species: option.species, speciesDetail: option.species === "OTHER" ? option.label : undefined })); }} />
            <div className={s.field}>
              <span className={s.fieldLabel} id="birthday-choice-label">생일</span>
              <div className={s.birthdayChoices} role="group" aria-labelledby="birthday-choice-label">
                <button type="button" aria-pressed={!birthdayUnknown} onClick={() => { setSaved(false); setBirthdayUnknown(false); }}>생일 선택</button>
                <button type="button" aria-pressed={birthdayUnknown} onClick={() => { setSaved(false); setBirthdayUnknown(true); setInput((old) => ({ ...old, birthday: "" })); }}>나중에 알려줄게요</button>
              </div>
              {!birthdayUnknown && <BirthdayDateField value={input.birthday} onChange={(birthday) => { setSaved(false); setInput((old) => ({ ...old, birthday })); }} />}
            </div>
          </> : <>
            <label>초대 코드<input className={s.code} value={code} onChange={(event) => { setCode(event.target.value.toUpperCase()); setError(""); }} placeholder="8자리 코드를 입력해 주세요" autoComplete="off" autoCapitalize="characters" spellCheck={false} maxLength={8} required /></label>
            <div className={s.demoCode}><span>체험용 코드</span><button type="button" onClick={() => { setCode(invitation.code); setError(""); }}>{invitation.code}<small>입력하기</small></button></div>
          </>}
          {error && <p className={s.error} role="alert">{error}</p>}
          <button className={s.primary} type="submit" disabled={busy || (choosingMode && !mode)}>{busy ? "기록 공간을 여는 중…" : step === "join" ? "함께 기록 시작하기" : choosingMode ? "이 공간에서 시작하기" : "다음"}<Icon name="arrow" size={18} /></button>
        </fieldset>
      </form>}
    </section>
    <p className={s.notice}>화면 체험이에요. 실제 계정이나 초대에 연결되지 않아요.</p>
  </main>;
}
