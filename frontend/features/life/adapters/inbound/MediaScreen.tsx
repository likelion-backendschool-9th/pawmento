"use client";
import { journalItems } from "@/features/workspace/domain/models";

import Link from "next/link";
import { ArchiveNavigation } from "@/components/ArchiveNavigation";
import { useState } from "react";
import { useApp } from "@/app/AppProvider";
import { Photo } from "@/components/Photo";
import { MediaPlayer } from "@/components/MediaAttachments";
import { Icon } from "@/components/Icon";
import type { Pet, MediaAttachment } from "@/features/workspace/domain/models";
import { LifeHeading, LifeEmpty, lifeStyles as s } from "./LifeUi";
export function MediaScreen({ pet }: { pet: Pet }) {
  const app = useApp();
  const [filter, setFilter] = useState("ALL");
  const records = journalItems(app.state).filter(
    (record) => record.petId === pet.id && record.type !== "QUESTION_ANSWER",
  );
  const files = records
    .flatMap((record) =>
      [
        ...(record.photoUrl
          ? [
              {
                id: record.id,
                type: "PHOTO" as const,
                url: record.photoUrl,
                name: record.body.slice(0, 30) || "사진 기록",
                mimeType: "image/jpeg",
              },
            ]
          : []),
        ...(record.attachments ?? []),
      ].map((file) => ({ file, record })),
    )
    .filter((item) => filter === "ALL" || item.file.type === filter);
  return (
    <>
      <LifeHeading
        label="MEDIA ARCHIVE"
        title="다이어리"
        body={`${pet.name}의 사진과 영상, 목소리를 한곳에서 만나보세요.`}
      >
        {pet.accessRole !== "VIEWER" && (
          <Link className="button primary" href={`/pets/${pet.id}/diary/new`}>
            <Icon name="plus" size={16} />
            미디어 남기기
          </Link>
        )}
      </LifeHeading>
      <ArchiveNavigation petId={pet.id} active="media" />
      <div className={s.tabs}>
        {[
          ["ALL", "전체"],
          ["PHOTO", "사진"],
          ["VIDEO", "영상"],
          ["VOICE", "목소리"],
        ].map(([key, label]) => (
          <button
            key={key}
            className={filter === key ? s.selected : ""}
            onClick={() => setFilter(key)}
          >
            {label}
          </button>
        ))}
        <span>{files.length}개의 기억</span>
      </div>
      {files.length ? (
        <div className={s.mediaGrid}>
          {files.map(({ file, record }) => (
            <MediaTile
              key={record.id + file.id}
              file={file}
              href={`/pets/${pet.id}/${record.type === "QUESTION_ANSWER" ? "questions/answers" : "diary"}/${record.id}`}
              date={record.recordedDate}
            />
          ))}
        </div>
      ) : (
        <LifeEmpty
          title="아직 담긴 미디어가 없어요"
          body="사진, 영상 또는 목소리를 첨부해 첫 기억을 남겨보세요."
          href={`/pets/${pet.id}/diary/new`}
          action="기록 남기기"
        />
      )}
    </>
  );
}
function MediaTile({
  file,
  href,
  date,
}: {
  file: MediaAttachment;
  href: string;
  date: string;
}) {
  return (
    <article className={s.mediaTile}>
      {file.type === "PHOTO" ? (
        <Link href={href}>
          <Photo src={file.url} alt={file.name} className={s.galleryPhoto} />
        </Link>
      ) : (
        <MediaPlayer file={file} />
      )}
      <div>
        <span className="badge">
          {file.type === "PHOTO"
            ? "사진"
            : file.type === "VIDEO"
              ? "영상"
              : "목소리"}
        </span>
        <time>{date}</time>
        <Link className="text-link" href={href}>
          원본 기록 보기
          <Icon name="arrow" size={14} />
        </Link>
      </div>
    </article>
  );
}
