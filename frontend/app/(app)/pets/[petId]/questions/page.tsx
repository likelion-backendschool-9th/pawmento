"use client";
import { QuestionHistory } from "@/features/life/adapters/inbound/QuestionHistory";
import { usePet } from "@/features/pet/hooks/PetContext";
export default function Page() {
  const pet = usePet();
  return <QuestionHistory key={pet.id + pet.mode} pet={pet} />;
}
