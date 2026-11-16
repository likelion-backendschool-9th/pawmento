"use client";
import { usePathname, useSearchParams } from "next/navigation";
import { useApp } from "@/app/AppProvider";
import { AuthScreen } from "@/auth/adapters/inbound/AuthScreen";
import { Shell } from "./AppShell";
import { useCurrentPet } from "./PetContext";

export function AuthenticatedLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const app = useApp();
  const pathname = usePathname();
  const search = useSearchParams();
  const pet = useCurrentPet();
  const query = search.toString();
  if (!app.account)
    return <AuthScreen returnTo={pathname + (query ? `?${query}` : "")} />;
  return (
    <Shell key={app.account.id} pet={pet} route={pathname}>
      {app.loading && (
        <div className="loading-bar" role="status">
          기록을 불러오고 있어요…
        </div>
      )}
      {app.error && (
        <div className="error" role="alert">
          {app.error}
          <button
            className="text-link"
            onClick={() => void app.refresh().catch(() => undefined)}
          >
            다시 시도
          </button>
        </div>
      )}
      {app.loading && !app.state.selectedPetId
        ? null
        : app.error && !app.state.selectedPetId
          ? null
          : children}
    </Shell>
  );
}
