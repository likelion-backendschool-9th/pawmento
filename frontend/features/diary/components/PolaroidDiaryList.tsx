import Link from "next/link";
import { Icon } from "@/components/Icon";
import { Photo } from "@/components/Photo";
import type { DiaryEntry } from "../types/DiaryEntry";
import s from "./PolaroidDiaryList.module.css";

export function PolaroidDiaryList({ records, currentAuthor }: {
  records: DiaryEntry[];
  currentAuthor?: { id: string; nickname: string };
}) {
  return (
    <ul className={s.gallery} aria-label="일기 사진 모음">
      {records.map((record, index) => {
        const title = record.title || "제목 없는 일기";
        const author = record.authorName?.trim() ||
          (record.authorId && record.authorId === currentAuthor?.id ? currentAuthor.nickname : "작성자 미상");
        const photo = record.photoUrl || record.attachments?.find(file => file.type === "PHOTO")?.url;
        return (
          <li key={record.id} className={s.item}>
            {index % 2 === 0 && <svg className={s.rope} viewBox="0 0 400 48" preserveAspectRatio="none" aria-hidden="true">
              <path d="M0 5 Q200 43 400 5" fill="none" vectorEffect="non-scaling-stroke" />
            </svg>}
            <Link href={`/pets/${record.petId}/diary/${record.id}`} className={s.link} aria-label={`${title} 일기 열기`}>
              <figure className={s.polaroid}>
                <span className={s.peg} aria-hidden="true" />
                {photo ? <Photo src={photo} alt={`${title} 사진`} className={s.photo} /> : (
                  <span className={s.note} aria-hidden="true"><Icon name={record.type === "VOICE" ? "mic" : "edit"} size={28} /></span>
                )}
                <figcaption className={s.caption}>
                  <h3 title={title}>{title}</h3>
                  <div className={s.meta}>
                    <time dateTime={record.recordedDate}>{record.recordedDate.replaceAll("-", ".")}</time>
                    <span className={s.author} title={author}>{author}</span>
                  </div>
                </figcaption>
              </figure>
            </Link>
          </li>
        );
      })}
    </ul>
  );
}
