"use client";
import { notFound, useParams } from "next/navigation";
import { OnboardingScreen } from "@/features/member/components/OnboardingScreen";
export default function Page() {
  const { step = [] } = useParams<{ step?: string[] }>();
  if (step.length > 1 || (step[0] && step[0] !== "register" && step[0] !== "join")) notFound();
  return <OnboardingScreen step={step[0] === "register" ? "register" : step[0] === "join" ? "join" : "choose"} />;
}
