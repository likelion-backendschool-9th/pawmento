"use client";
import { redirect } from "next/navigation";
import { usePet } from "@/features/pet/hooks/PetContext";
export default function Page() {
  const pet = usePet();
  redirect(`/pets/${pet.id}/diary`);
}
