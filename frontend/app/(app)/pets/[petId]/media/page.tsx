"use client";
import { MediaScreen } from "@/features/life/adapters/inbound/MediaScreen";
import { usePet } from "@/features/pet/hooks/PetContext";
export default function Page() {
  const pet = usePet();
  return <MediaScreen key={pet.id + pet.mode} pet={pet} />;
}
