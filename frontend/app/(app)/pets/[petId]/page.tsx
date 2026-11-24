"use client";
import { PetForm } from "@/features/pet/components/PetScreens";
import {
  usePet,
  PetWriteAccess,
} from "@/features/pet/hooks/PetContext";
export default function Page() {
  const pet = usePet();
  return (
    <PetWriteAccess ownerOnly>
      <PetForm key={pet.id + pet.mode} pet={pet} />
    </PetWriteAccess>
  );
}
