"use client";
import { ArchiveScreen } from "@/features/diary/components/ArchiveScreen";
import { usePet } from "@/features/pet/hooks/PetContext";
export default function Page() {
  const pet = usePet();
  return <ArchiveScreen key={pet.id + pet.mode} pet={pet} calendar />;
}
