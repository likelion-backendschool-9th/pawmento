"use client";
import { HomeScreen } from "@/workspace/adapters/inbound/HomeScreen";
import { usePet } from "@/workspace/adapters/inbound/PetContext";
export default function Page() {
  const pet = usePet();
  return <HomeScreen key={pet.id + pet.mode} pet={pet} />;
}
