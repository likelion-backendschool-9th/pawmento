import { AnimalFootprint, type FootprintKind } from "./AnimalFootprint";
import s from "./WelcomeOnboarding.module.css";
const prints: FootprintKind[] = ["dog", "cat", "bird", "rabbit", "reptile"];
export function WelcomeSplash() {
  return <main id="main" className={`${s.screen} ${s.splash}`} aria-label="PawMento 시작">
    <div className={s.trail} aria-hidden="true">{prints.map((kind, index) => <span key={kind} data-step={index}><AnimalFootprint kind={kind} /></span>)}</div>
    <div className={s.splashBrand}><p className={s.wordmark}>Paw<span>Mento</span></p><p className={s.splashCopy}>함께한 순간을, 오래도록.</p></div>
    <span className={s.srOnly} role="status">우리의 이야기를 준비하고 있어요.</span>
  </main>;
}
