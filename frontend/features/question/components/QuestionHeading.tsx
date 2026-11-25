import type { ReactNode } from "react";
import s from "./TodayQuestion.module.css";

export function QuestionHeading({ title, body, children }: {
  title: string;
  body: ReactNode;
  children?: ReactNode;
}) {
  return (
    <header className={s.screenHeading}>
      <div className={s.headingRow}>
        <h1>{title}</h1>
        {children}
      </div>
      <p>{body}</p>
    </header>
  );
}
