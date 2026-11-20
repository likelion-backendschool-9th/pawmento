"use client";
import { useApp } from "@/app/AppProvider";
import { WalkScreen } from "@/map/adapters/inbound/WalkScreen";
import { usePet } from "@/workspace/adapters/inbound/PetContext";
export default function Page() {
  const pet = usePet();
  const { account } = useApp();
  return <WalkScreen key={`${account?.id ?? ""}:${pet.id}`} pet={pet} />;
}
