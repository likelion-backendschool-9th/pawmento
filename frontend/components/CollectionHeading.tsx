import type { ReactNode } from "react";
import s from "./CollectionHeading.module.css";

export function CollectionHeading({ title, body, children }: {
  title: string;
  body: string;
  children?: ReactNode;
}) {
  return (
    <header className={s.heading}>
      <div className={s.titleRow}>
        <h1>{title}</h1>
        {children && <div className={s.action}>{children}</div>}
      </div>
      <p>{body}</p>
    </header>
  );
}
