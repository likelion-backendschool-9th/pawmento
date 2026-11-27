import Image from "next/image";
import { Icon } from "@/components/Icon";
import { AnimalFootprint } from "./AnimalFootprint";
import s from "./WelcomeOnboarding.module.css";

export function OnboardingArtwork({ kind, label }: { kind: "companions" | "questions" | "walks" | "together" | "memories"; label: string }) {
  return <div className={s.artwork} data-art={kind} role="img" aria-label={label}>
    <div className={s.artHalo} />
    {kind === "companions" ? <>
      <div className={s.symbol}><Image src="/images/4abce15e-8aa3-43d2-97ca-0b3435fecf61.png" alt="" draggable={false} fill sizes="320px" unoptimized loading="eager" /></div>
      <span className={s.smallPrint}><AnimalFootprint kind="bird" /></span><span className={s.smallLeaf}><Icon name="leaf" size={25} /></span>
    </> : kind === "questions" ? <>
      <div className={s.questionCard}><span><Icon name="book" size={18} />오늘의 질문</span><strong>함께 웃었던 순간은<br />언제였나요?</strong><div className={s.answerLine}>산책길에서 눈을 마주쳤을 때요.</div></div>
      <div className={s.familyAnswer}><AnimalFootprint kind="cat" /><span>저는 같이 쉬던 오후가 생각나요.</span></div>
    </> : kind === "walks" ? <>
      <div className={s.walkMap}><svg viewBox="0 0 260 220" aria-hidden="true"><rect width="260" height="220" fill="#eef1e6" /><path d="M-10 80 270 140M60-10 120 230M190-10 150 230M-10 190 270 20" fill="none" stroke="#fffdf6" strokeWidth="12" /><ellipse cx="130" cy="105" rx="86" ry="70" fill="#dbe6c8" /><path d="M70 158c-15-35 12-88 53-97s85 30 55 66-65-2-91 16" fill="none" stroke="#fffdf6" strokeWidth="9" strokeLinecap="round" /><path d="M70 158c-15-35 12-88 53-97s85 30 55 66-65-2-91 16" fill="none" stroke="#7b956b" strokeWidth="5" strokeLinecap="round" /><circle cx="70" cy="158" r="7" fill="#7b956b" stroke="#fff" strokeWidth="3" /><circle cx="87" cy="143" r="5" fill="#efbd94" stroke="#fff" strokeWidth="3" /></svg><span className={s.mapPin}><Icon name="pin" size={25} /></span></div>
      <div className={s.walkStats}><span><Icon name="route" size={17} />1.2 km</span><span><Icon name="clock" size={17} />24분</span></div>
    </> : kind === "together" ? <>
      <div className={s.togetherCard}><div className={s.avatars}><span><AnimalFootprint kind="dog" /></span><span><AnimalFootprint kind="cat" /></span><span><AnimalFootprint kind="bird" /></span></div><strong>같은 아이, 서로 다른 순간</strong><div className={s.sharedLine}><Icon name="book" size={18} /><span>가족과 함께 남기는 기록</span></div><div className={s.sharedLine}><Icon name="heart" size={18} /><span>이웃과 나누는 이야기</span></div></div>
      <span className={s.inviteBadge}><Icon name="mail" size={22} />함께해요</span>
    </> : <>
      <div className={s.garden} />
      <div className={s.memoryBack} /><div className={s.memoryCard}><Icon name="leaf" size={27} /><span>함께한 시간은<br />언제나 여기.</span><AnimalFootprint /></div>
      <span className={s.memorySpark}><Icon name="sparkle" size={28} /></span>
    </>}
  </div>;
}
