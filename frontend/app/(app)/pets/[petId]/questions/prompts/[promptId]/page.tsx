"use client";
import { useParams } from "next/navigation";
import { usePet } from "@/features/pet/hooks/PetContext";
import { QuestionPromptDetail } from "@/features/question/components/QuestionPromptDetail";
export default function Page() {
  const pet = usePet();
  const { promptId } = useParams<{ promptId: string }>();
  return <QuestionPromptDetail pet={pet} promptId={promptId} />;
}
