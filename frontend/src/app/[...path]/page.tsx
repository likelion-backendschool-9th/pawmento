import { Suspense } from "react";
import { AppScreen } from "@/workspace/adapters/inbound/AppScreen";
async function Route({ params }: { params: Promise<{ path: string[] }> }) {
  const { path } = await params;
  return <AppScreen path={path} />;
}
export default function Page({
  params,
}: {
  params: Promise<{ path: string[] }>;
}) {
  return (
    <Suspense
      fallback={
        <main className="route-loading" role="status">
          우리의 이야기를 펼치는 중…
        </main>
      }
    >
      <Route params={params} />
    </Suspense>
  );
}
