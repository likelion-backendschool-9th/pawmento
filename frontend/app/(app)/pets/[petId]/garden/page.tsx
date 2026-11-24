"use client";
import { HomeScreen } from "@/features/pet/components/HomeScreen";
import { usePet } from "@/features/pet/hooks/PetContext";
export default function Page() {
  const pet = usePet();
  return <HomeScreen key={pet.id + pet.mode} pet={pet} />;
}
