"use client";
import { redirect } from "next/navigation";
import { usePet } from "@/features/workspace/adapters/inbound/PetContext";
export default function Page() {
  const pet = usePet();
  redirect(`/pets/${pet.id}/diary`);
}
