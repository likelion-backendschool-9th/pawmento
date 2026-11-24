"use client";
import { createContext, useContext } from "react";
import { notFound, useParams } from "next/navigation";
import { useApp } from "@/app/AppProvider";
import type { Pet } from "../types/Pet";
import { EmptyState } from "../../shell/components/WorkspaceUi";
const Context = createContext<Pet | null>(null);
export function useCurrentPet() {
  const app = useApp();
  const { petId } = useParams<{ petId?: string }>();
  return app.state.pets.find(
    (pet) => pet.id === (petId ?? app.state.selectedPetId),
  );
}
export function PetBoundary({ children }: { children: React.ReactNode }) {
  const pet = useCurrentPet();
  if (!pet) notFound();
  return <Context.Provider value={pet}>{children}</Context.Provider>;
}
export function usePet() {
  const pet = useContext(Context);
  if (!pet) throw Error("PetBoundary is required");
  return pet;
}
export function PetWriteAccess({
  children,
  ownerOnly = false,
}: {
  children: React.ReactNode;
  ownerOnly?: boolean;
}) {
  const pet = usePet();
  if (
    pet.accessRole === "VIEWER" ||
    (ownerOnly && pet.accessRole && pet.accessRole !== "OWNER")
  )
    return (
      <EmptyState
        title="소유자 또는 편집 권한이 필요해요"
        body={
          ownerOnly
            ? "프로필과 모드는 소유자가 관리합니다."
            : "기록을 남기려면 소유자에게 편집자 역할을 요청해 주세요."
        }
        href={`/pets/${pet.id}/diary`}
        action="다이어리 보기"
      />
    );
  return children;
}
