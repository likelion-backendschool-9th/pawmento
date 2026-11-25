"use client";
import { TodayQuestion } from "@/features/question/components/TodayQuestion";
import { usePet } from "@/features/pet/hooks/PetContext";
export default function Page() {
  const pet = usePet();
  return <TodayQuestion key={pet.id + pet.mode} pet={pet} />;
}
