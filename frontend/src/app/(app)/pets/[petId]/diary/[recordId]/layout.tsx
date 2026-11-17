import { RecordBoundary } from "@/workspace/adapters/inbound/RecordContext";
export default function RecordLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return <RecordBoundary>{children}</RecordBoundary>;
}
