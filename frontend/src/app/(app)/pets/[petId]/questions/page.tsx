"use client";
import { QuestionHistory } from "@/life/adapters/inbound/QuestionHistory";
import { usePet } from "@/workspace/adapters/inbound/PetContext";
export default function Page() {
  const pet = usePet();
  return <QuestionHistory key={pet.id + pet.mode} pet={pet} />;
}
