"use client";
import Link from "next/link";
import { Icon } from "@/components/Icon";
import styles from "./Life.module.css";
export { styles as lifeStyles };
export function LifeHeading({
  label,
  title,
  body,
  children,
  subject,
}: {
  subject?: string;
  label: string;
  title: string;
  body: React.ReactNode;
  children?: React.ReactNode;
}) {
  return (
    <header className={styles.heading}>
      <div>
        <p>{label}</p>
        {subject && <strong className={styles.headingSubject}>{subject}</strong>}
        <h1>{title}</h1>
        <span>{body}</span>
      </div>
      {children}
    </header>
  );
}
export function LifeStatus({
  error,
  loading,
}: {
  error?: string;
  loading?: boolean;
}) {
  return error ? (
    <p role="alert" className="error">
      {error}
    </p>
  ) : loading ? (
    <p role="status" className={styles.loading}>
      불러오는 중…
    </p>
  ) : null;
}
export function LifeEmpty({
  title,
  body,
  href,
  action,
}: {
  title: string;
  body: string;
  href?: string;
  action?: string;
}) {
  return (
    <div className={styles.empty}>
      <Icon name="leaf" size={32} />
      <h2>{title}</h2>
      <p>{body}</p>
      {href && (
        <Link className="button primary" href={href}>
          {action}
        </Link>
      )}
    </div>
  );
}
export function DemoNote({ children }: { children: React.ReactNode }) {
  return (
    <div className={styles.demoNote}>
      <Icon name="leaf" size={17} />
      <p>{children}</p>
    </div>
  );
}
