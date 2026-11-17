"use client";
import { MediaScreen } from "@/life/adapters/inbound/MediaScreen";
import { usePet } from "@/workspace/adapters/inbound/PetContext";
export default function Page() {
  const pet = usePet();
  return <MediaScreen key={pet.id + pet.mode} pet={pet} />;
}
