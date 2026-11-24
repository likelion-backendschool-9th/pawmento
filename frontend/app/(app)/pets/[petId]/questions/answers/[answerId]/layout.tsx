import { JournalBoundary } from "@/features/diary/hooks/JournalContext";
export default function JournalLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return <JournalBoundary>{children}</JournalBoundary>;
}
