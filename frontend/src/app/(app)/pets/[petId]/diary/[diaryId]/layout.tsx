import { JournalBoundary } from "@/workspace/adapters/inbound/JournalContext";
export default function JournalLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return <JournalBoundary>{children}</JournalBoundary>;
}
