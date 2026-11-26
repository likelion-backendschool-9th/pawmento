import Link from "next/link";
import { Icon } from "@/components/Icon";
import { Photo } from "@/components/Photo";
import type { DiaryEntry } from "../types/DiaryEntry";
import s from "./PolaroidDiaryList.module.css";

export function PolaroidDiaryList({ records }: { records: DiaryEntry[] }) {
  return (
    <ul className={s.gallery} aria-label="일기 사진 모음">
      {records.map(record => {
        const title = record.title || "제목 없는 일기";
        const photo = record.photoUrl || record.attachments?.find(file => file.type === "PHOTO")?.url;
        return (
          <li key={record.id} className={s.item}>
            <Link href={`/pets/${record.petId}/diary/${record.id}`} className={s.link} aria-label={`${title} 일기 열기`}>
              <figure className={s.polaroid}>
                <span className={s.peg} aria-hidden="true" />
                {photo ? <Photo src={photo} alt={`${title} 사진`} className={s.photo} /> : (
                  <span className={s.note} aria-hidden="true"><Icon name={record.type === "VOICE" ? "mic" : "edit"} size={28} /></span>
                )}
                <figcaption className={s.caption}><h3 title={title}>{title}</h3></figcaption>
              </figure>
            </Link>
          </li>
        );
      })}
    </ul>
  );
}
