"use client";
import { HomeScreen } from "@/features/workspace/adapters/inbound/HomeScreen";
import { useCurrentPet } from "@/features/workspace/adapters/inbound/PetContext";
import { EmptyState } from "@/features/workspace/adapters/inbound/WorkspaceUi";
export default function Page() {
  const pet = useCurrentPet();
  return pet ? (
    <HomeScreen key={pet.id} pet={pet} />
  ) : (
    <EmptyState
      title="우리의 첫 이야기를 시작해요"
      body="반려동물을 등록하거나 가족 초대에 참여해 주세요."
      href="/pets/new"
      action="반려동물 등록하기"
    />
  );
}
