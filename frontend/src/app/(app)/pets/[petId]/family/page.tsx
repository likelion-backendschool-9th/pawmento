"use client";
import { FamilyScreen } from "@/life/adapters/inbound/FamilyScreen";
import { usePet } from "@/workspace/adapters/inbound/PetContext";
export default function Page() {
  const pet = usePet();
  return <FamilyScreen key={pet.id + pet.mode} pet={pet} />;
}
