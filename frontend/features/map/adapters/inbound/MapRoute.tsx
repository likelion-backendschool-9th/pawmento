"use client";
import { useApp } from "@/app/AppProvider";
import { usePet } from "@/features/workspace/adapters/inbound/PetContext";
import { MapScreen } from "./MapScreen";
import { isOpenableDiary } from "./pinDetail";

// 지도 화면으로 들어오는 모든 주소(/map, /map/walks, /map/walks/{날짜})가 같은 조립을 쓴다.
// 기록 상세를 열 수 있는 조건(canOpenDiary)을 한 곳에서 관리해 주소마다 달라지지 않게 한다.
export function MapRoute({
  initialView = "pins",
  initialDate = null,
}: {
  initialView?: "pins" | "walks";
  initialDate?: string | null;
}) {
  const pet = usePet();
  const { state } = useApp();
  return (
    <MapScreen
      key={`${pet.id}${pet.mode}${initialView}${initialDate ?? ""}`}
      pet={pet}
      initialView={initialView}
      initialDate={initialDate}
      canOpenDiary={(diaryId) => isOpenableDiary(state.diaries, pet.id, diaryId)}
    />
  );
}
