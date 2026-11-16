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
    <nav className={s.tabs} aria-label="기록 보기 방식">
      {(
        [
          ["timeline", "전체 기록", "book"],
          ["calendar", "달력", "calendar"],
          ["media", "사진·영상·목소리", "camera"],
        ] as const
      ).map(([page, label, icon]) => (
        <Link
          key={page}
          href={`/pets/${petId}/${page}`}
          aria-current={active === page ? "page" : undefined}
        >
          <Icon name={icon} size={16} />
          {label}
        </Link>
      ))}
    </nav>
  );
}
