"use client";
import { redirect } from "next/navigation";
import { usePet } from "@/workspace/adapters/inbound/PetContext";
export default function Page() {
  const pet = usePet();
  redirect(`/pets/${pet.id}/diary`);
}
