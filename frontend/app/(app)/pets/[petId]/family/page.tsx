"use client";
import { FamilyScreen } from "@/features/family/components/FamilyScreen";
import { usePet } from "@/features/pet/hooks/PetContext";
export default function Page() {
  const pet = usePet();
  return <FamilyScreen key={pet.id + pet.mode} pet={pet} />;
}
