import { PetBoundary } from "@/features/pet/hooks/PetContext";
export default function PetLayout({ children }: { children: React.ReactNode }) {
  return <PetBoundary>{children}</PetBoundary>;
}
