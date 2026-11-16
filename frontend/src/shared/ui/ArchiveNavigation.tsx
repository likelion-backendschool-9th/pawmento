import Link from "next/link";
import { Icon } from "./Icon";
import s from "./ArchiveNavigation.module.css";

export function ArchiveNavigation({
  petId,
  active,
}: {
  petId: string;
  active: "timeline" | "calendar" | "media";
}) {
  return (
    <nav className={s.tabs} aria-label="다이어리 보기 방식">
      {(
        [
          ["timeline", "일기", "book"],
          ["calendar", "달력", "calendar"],
        ] as const
      ).map(([page, label, icon]) => (
        <Link
          key={page}
          href={`/pets/${petId}/${page}`}
          aria-current={
            active === page || (active === "media" && page === "timeline")
              ? "page"
              : undefined
          }
        >
          <Icon name={icon} size={16} />
          {label}
        </Link>
      ))}
    </nav>
  );
}
