"use client";
import { ModeScreen } from "@/life/adapters/inbound/ModeScreen";
import {
  usePet,
  PetWriteAccess,
} from "@/workspace/adapters/inbound/PetContext";
export default function Page() {
  const pet = usePet();
  return (
    <PetWriteAccess ownerOnly>
      <ModeScreen key={pet.id + pet.mode} pet={pet} />
    </PetWriteAccess>
  );
}
