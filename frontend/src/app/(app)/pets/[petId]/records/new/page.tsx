"use client";
import { useSearchParams } from "next/navigation";
import { today } from "@/shared/date";
import {
  usePet,
  PetWriteAccess,
} from "@/workspace/adapters/inbound/PetContext";
import { RecordForm } from "@/workspace/adapters/inbound/RecordScreens";
export default function Page() {
  const pet = usePet();
  const search = useSearchParams();
  return (
    <PetWriteAccess>
      <RecordForm
        key={pet.id + (search.get("date") ?? today())}
        pet={pet}
        diary={false}
      />
    </PetWriteAccess>
  );
}
