import { Suspense } from "react";
export default function AuthLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <Suspense
      fallback={
        <main className="route-loading" role="status">
          화면을 준비하고 있어요…
        </main>
      }
    >
      {children}
    </Suspense>
  );
}
