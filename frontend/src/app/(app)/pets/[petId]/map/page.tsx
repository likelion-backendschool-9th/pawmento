"use client";
import { useApp } from "@/app/AppProvider";
import { MapScreen } from "@/map/adapters/inbound/MapScreen";
import { isOpenableDiary } from "@/map/adapters/inbound/pinDetail";
import { usePet } from "@/workspace/adapters/inbound/PetContext";
export default function Page() {
  const pet = usePet();
  const { state } = useApp();
  return (
    <MapScreen
      key={pet.id + pet.mode}
      pet={pet}
      canOpenDiary={(diaryId) => isOpenableDiary(state.diaries, pet.id, diaryId)}
    />
  );
}
