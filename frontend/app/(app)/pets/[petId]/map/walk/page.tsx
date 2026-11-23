"use client";
import { useApp } from "@/app/AppProvider";
import { WalkScreen } from "@/features/map/adapters/inbound/WalkScreen";
import { usePet } from "@/features/workspace/adapters/inbound/PetContext";
export default function Page() {
  const pet = usePet();
  const { account } = useApp();
  return <WalkScreen key={`${account?.id ?? ""}:${pet.id}`} pet={pet} />;
}
