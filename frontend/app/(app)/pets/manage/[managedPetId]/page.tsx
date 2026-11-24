import { Suspense } from "react";
import { PetManagementDetail } from "@/features/pet/components/PetManagementDetail";

export default function Page() {
  return (
    <Suspense fallback={<p className="loading-bar" role="status">프로필을 불러오고 있어요…</p>}>
      <PetManagementDetail />
    </Suspense>
  );
}
