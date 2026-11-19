"use client";
import { useSearchParams } from "next/navigation";
import { today } from "@/shared/date";
import {
  usePet,
  PetWriteAccess,
} from "@/workspace/adapters/inbound/PetContext";
import { JournalForm } from "@/workspace/adapters/inbound/JournalScreens";
export default function Page() {
  const pet = usePet();
  const search = useSearchParams();
  return (
    <PetWriteAccess>
      <JournalForm
        key={pet.id + (search.get("date") ?? today())}
        pet={pet}
        questionAnswer={false}
      />
    </PetWriteAccess>
  );
}
