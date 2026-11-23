"use client";
import { FamilyScreen } from "@/features/life/adapters/inbound/FamilyScreen";
import { usePet } from "@/features/workspace/adapters/inbound/PetContext";
export default function Page() {
  const pet = usePet();
  return <FamilyScreen key={pet.id + pet.mode} pet={pet} />;
}
