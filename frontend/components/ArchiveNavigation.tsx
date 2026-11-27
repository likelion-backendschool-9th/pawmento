import Link from "next/link";
import { Icon } from "./Icon";
import s from "./ArchiveNavigation.module.css";

export function ArchiveNavigation({
  petId,
  active,
}: {
  petId: string;
  active: "diary" | "calendar" | "map" | "questions" | "media";
}) {
  return (
    <nav className={s.tabs} aria-label="다이어리 보기 방식">
      {(
        [
          ["diary", "일기", "book"],
          ["calendar", "달력", "calendar"],
          ["map", "지도", "pin"],
          ["questions", "오늘의 질문", "question"],
        ] as const
      ).map(([page, label, icon]) => (
        <Link
          key={page}
          href={`/pets/${petId}/${page}`}
          aria-current={
            active === page || (active === "media" && page === "diary")
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
