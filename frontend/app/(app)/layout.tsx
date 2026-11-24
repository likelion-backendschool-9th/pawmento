import { Suspense } from "react";
import { AuthenticatedLayout } from "@/features/shell/components/AuthenticatedLayout";
export default function AppLayout({ children }: { children: React.ReactNode }) {
  return (
    <Suspense
      fallback={
        <main className="route-loading" role="status">
          우리의 이야기를 펼치는 중…
        </main>
      }
    >
      <AuthenticatedLayout>{children}</AuthenticatedLayout>
    </Suspense>
  );
}
