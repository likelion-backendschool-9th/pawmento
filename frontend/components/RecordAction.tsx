import Link from "next/link";
import type { ButtonHTMLAttributes, ComponentProps, ReactNode } from "react";
import { Icon } from "./Icon";
import s from "./RecordAction.module.css";

type Appearance = { icon?: string; full?: boolean; compact?: boolean; children: ReactNode };
function classes(className = "", full = false, compact = false) {
  return `${s.action} ${full ? s.full : ""} ${compact ? s.compact : ""} ${className}`;
}
function Content({ icon = "edit", children }: Pick<Appearance, "icon" | "children">) {
  return <><span className={s.icon} aria-hidden="true"><Icon name={icon} size={15} /></span>{children}</>;
}
export function RecordActionLink({ icon, full, compact, className, children, ...props }: ComponentProps<typeof Link> & Appearance) {
  return <Link {...props} className={classes(className, full, compact)}><Content icon={icon}>{children}</Content></Link>;
}
export function RecordActionButton({ icon, full, compact, danger = false, className, children, type = "button", ...props }: ButtonHTMLAttributes<HTMLButtonElement> & Appearance & { danger?: boolean }) {
  return <button {...props} type={type} className={`${classes(className, full, compact)} ${danger ? s.danger : ""}`}><Content icon={icon}>{children}</Content></button>;
}
