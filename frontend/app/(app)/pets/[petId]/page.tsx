"use client";
import { PetForm } from "@/features/workspace/adapters/inbound/PetScreens";
import {
  usePet,
  PetWriteAccess,
} from "@/features/workspace/adapters/inbound/PetContext";
export default function Page() {
  const pet = usePet();
  return (
    <PetWriteAccess ownerOnly>
      <PetForm key={pet.id + pet.mode} pet={pet} />
    </PetWriteAccess>
  );
}
