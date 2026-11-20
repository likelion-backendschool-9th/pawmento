"use client";
import { WalkScreen } from "@/map/adapters/inbound/WalkScreen";
import { usePet } from "@/workspace/adapters/inbound/PetContext";
export default function Page() {
  const pet = usePet();
  return <WalkScreen key={pet.id} pet={pet} />;
}
