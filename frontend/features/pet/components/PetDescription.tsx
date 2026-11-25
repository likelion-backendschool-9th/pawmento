import type { ReactNode } from "react";
import s from "./PetDescription.module.css";

/** Keep the pet's name and the explanation on separate, intentional lines. */
export function PetDescription({ name, suffix, children }: {
  name: string;
  suffix: string;
  children: ReactNode;
}) {
  return (
    <span className={s.description}>
      <span className={s.identity}>
        <span className={s.name} title={name}>{name}</span>
        <span className={s.suffix}>{suffix}</span>
      </span>
      <span className={s.copy}>{children}</span>
    </span>
  );
}
