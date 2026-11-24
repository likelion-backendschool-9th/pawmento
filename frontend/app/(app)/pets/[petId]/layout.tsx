import { PetBoundary } from "@/features/workspace/adapters/inbound/PetContext";
export default function PetLayout({ children }: { children: React.ReactNode }) {
  return <PetBoundary>{children}</PetBoundary>;
}
