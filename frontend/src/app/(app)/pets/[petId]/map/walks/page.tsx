"use client";
import { useApp } from "@/app/AppProvider";
import { MapScreen } from "@/map/adapters/inbound/MapScreen";
import { isOpenableDiary } from "@/map/adapters/inbound/pinDetail";
import { usePet } from "@/workspace/adapters/inbound/PetContext";
// 산책이 끝난 뒤 바로 "산책 길" 보기로 들어오는 주소
export default function Page() {
  const pet = usePet();
  const { state } = useApp();
  return (
    <MapScreen
      key={pet.id + pet.mode}
      pet={pet}
      initialView="walks"
      canOpenDiary={(diaryId) => isOpenableDiary(state.diaries, pet.id, diaryId)}
    />
  );
}
