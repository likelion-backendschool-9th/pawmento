"use client";
import { ArchiveScreen } from "@/features/workspace/adapters/inbound/ArchiveScreen";
import { usePet } from "@/features/workspace/adapters/inbound/PetContext";
export default function Page() {
  const pet = usePet();
  return <ArchiveScreen key={pet.id + pet.mode} pet={pet} calendar />;
}
