"use client";
import { useSearchParams } from "next/navigation";
import { today } from "@/lib/date";
import {
  usePet,
  PetWriteAccess,
} from "@/features/workspace/adapters/inbound/PetContext";
import { JournalForm } from "@/features/workspace/adapters/inbound/JournalScreens";
export default function Page() {
  const pet = usePet();
  const search = useSearchParams();
  return (
    <PetWriteAccess>
      <JournalForm
        key={pet.id + (search.get("date") ?? today())}
        pet={pet}
        questionAnswer={true}
      />
    </PetWriteAccess>
  );
}
