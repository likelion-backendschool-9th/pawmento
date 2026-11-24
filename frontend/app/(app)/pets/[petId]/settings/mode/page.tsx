"use client";
import { ModeScreen } from "@/features/pet/components/ModeScreen";
import {
  usePet,
  PetWriteAccess,
} from "@/features/pet/hooks/PetContext";
export default function Page() {
  const pet = usePet();
  return (
    <PetWriteAccess ownerOnly>
      <ModeScreen key={pet.id + pet.mode} pet={pet} />
    </PetWriteAccess>
  );
}
