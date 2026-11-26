import { LandingScreen } from "@/features/member/components/LandingScreen";
import { FirstVisitGate } from "@/features/onboarding/components/FirstVisitGate";
export default function Page() {
  return <FirstVisitGate><LandingScreen /></FirstVisitGate>;
}
