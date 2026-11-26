import Image from "next/image";
import { Icon } from "@/components/Icon";
import { Photo } from "@/components/Photo";
import { AnimalFootprint } from "./AnimalFootprint";
import s from "./WelcomeOnboarding.module.css";

export function OnboardingArtwork({ kind, label }: { kind: "companions" | "days" | "memories"; label: string }) {
  return <div className={s.artwork} data-art={kind} role="img" aria-label={label}>
    <div className={s.artHalo} />
    {kind === "companions" ? <>
      <div className={s.symbol}><Image src="/images/4abce15e-8aa3-43d2-97ca-0b3435fecf61.png" alt="" draggable={false} fill sizes="320px" unoptimized loading="eager" /></div>
      <span className={s.smallPrint}><AnimalFootprint kind="bird" /></span><span className={s.smallLeaf}><Icon name="leaf" size={25} /></span>
    </> : kind === "days" ? <>
      <div className={s.photoBack}><Photo src="reference:sleeping" alt="" /></div>
      <div className={s.photoCard}><Photo src="reference:running" alt="" /><span>오늘도, 함께.</span></div>
      <div className={s.noteCard}><Icon name="book" size={20} /><span>평범해서 더 좋은 하루</span><i /><i /></div>
      <span className={s.voiceBadge}><Icon name="mic" size={19} /><span /><span /><span /><span /><span /></span>
    </> : <>
      <div className={s.garden} />
      <div className={s.memoryBack} /><div className={s.memoryCard}><Icon name="leaf" size={27} /><span>함께한 시간은<br />언제나 여기.</span><AnimalFootprint /></div>
      <span className={s.memorySpark}><Icon name="sparkle" size={28} /></span>
    </>}
  </div>;
}
