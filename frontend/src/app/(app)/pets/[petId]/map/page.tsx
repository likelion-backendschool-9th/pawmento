"use client";
import { MapScreen } from "@/map/adapters/inbound/MapScreen";
import { usePet } from "@/workspace/adapters/inbound/PetContext";
export default function Page() {
  const pet = usePet();
  return <MapScreen key={pet.id + pet.mode} pet={pet} />;
}
