import type { Metadata } from "next";
import { SiteEntrySplash } from "@/features/onboarding/components/SiteEntrySplash";
import { AppProvider } from "./AppProvider";
import { UnsavedChangesProvider } from "@/components/UnsavedChangesProvider";
import "./globals.css";
export const metadata: Metadata = {
  manifest: "/manifest.webmanifest",
  title: {
    default: "PawMento · 함께한 오늘, 오래도록",
    template: "%s | PawMento",
  },
  description:
    "반려동물과 함께한 오늘을 기록하고, 소중한 순간을 차곡차곡 모으는 우리만의 정원.",
};
export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="ko" data-scroll-behavior="smooth">
      <body>
        <AppProvider>
          <SiteEntrySplash><UnsavedChangesProvider>{children}</UnsavedChangesProvider></SiteEntrySplash>
        </AppProvider>
      </body>
    </html>
  );
}
