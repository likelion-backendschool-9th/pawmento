"use client";
import Image from "next/image";
import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { useApp } from "@/app/AppProvider";
import { Icon } from "@/components/Icon";
import { Photo } from "@/components/Photo";
import { MediaPlayer } from "@/components/MediaAttachments";
import { journalItems } from "@/features/diary/utils/journalItems";
import { formatDate } from "@/lib/date";
import type { Pet } from "../types/Pet";
import { anotherMemory, gardenMemories } from "../utils/gardenMemories";
import s from "./GardenSky.module.css";

// Normalized centers of the six painted butterflies in puppy-retouched-v2.png.
const lights = [
  { x: 32.2, y: 29.6 },
  { x: 68.9, y: 36.4 },
  { x: 46.5, y: 43.7 },
  { x: 24.9, y: 51.7 },
  { x: 72, y: 55.6 },
  { x: 42.5, y: 57.5 },
];
export function GardenSky({ pet }: { pet: Pet }) {
  const app = useApp();
  const memories = gardenMemories(
    app.sessionSource,
    pet.id,
    journalItems(app.state),
    app.account?.id,
  );
  const [open, setOpen] = useState(false);
  const [selected, setSelected] = useState<string | null>(null);
  const previous = useRef<string | null>(null);
  const dialog = useRef<HTMLDialogElement>(null);
  const memory = memories.find((item) => item.key === selected);
  useEffect(() => {
    const node = dialog.current;
    if (open && node && !node.open) node.showModal();
    if (!open && node?.open) node.close();
  }, [open]);
  function reveal() {
    const next = anotherMemory(memories, previous.current);
    previous.current = next?.key ?? null;
    setSelected(next?.key ?? null);
    setOpen(true);
  }
  return (
    <>
      <section
        className={s.sky}
        aria-label={`${pet.name}의 기억이 빛나는 정원`}
      >
        <div className={s.scene} aria-hidden="true">
          <Image
            src="/images/puppy-retouched-v2.png"
            alt=""
            fill
            unoptimized
            sizes="480px"
            loading="eager"
            className={s.art}
          />
        </div>
        <div className={s.intro}>
          <span>GARDEN OF MEMORIES</span>
          <h1>{pet.name}의 작은 우주</h1>
          <p>나비를 눌러 기억 한 조각을 만나보세요.</p>
        </div>
        <div className={s.butterflies}>
          {lights.map((point, i) => (
            <button
              key={i}
              type="button"
              className={s.butterfly}
              style={{
                left: `${point.x}%`,
                top: `${point.y}%`,
                animationDelay: `${i * -0.8}s`,
              }}
              onClick={reveal}
              aria-label={`${i + 1}번째 나비, 기억 한 조각 열기`}
            >
              <span className={s.butterflyHalo} aria-hidden="true" />
            </button>
          ))}
        </div>
      </section>
      <dialog
        ref={dialog}
        className={s.sheet}
        onClose={() => setOpen(false)}
        onClick={(e) => {
          if (e.target === e.currentTarget) setOpen(false);
        }}
        aria-labelledby="garden-memory-title"
      >
        <div className={s.grip} />
        <div className={s.sheetTop}>
          <span>나비가 데려온 기억</span>
          <button
            className="icon-button"
            type="button"
            onClick={() => setOpen(false)}
            aria-label="기억 닫기"
          >
            <Icon name="close" />
          </button>
        </div>
        {memory ? (
          <>
            <span className={s.kind}>
              {memory.item.kind === "DIARY" ? "다이어리" : "오늘의 질문"}
            </span>
            <h2 id="garden-memory-title">
              {memory.item.kind === "QUESTION_ANSWER"
                ? memory.item.question
                : memory.item.title || "제목 없는 일기"}
            </h2>
            <p className={s.date}>
              {formatDate(memory.item.recordedDate)}
              {memory.item.authorName ? ` · ${memory.item.authorName}` : ""}
            </p>
            {memory.item.photoUrl && (
              <div className={s.photo}>
                <Photo src={memory.item.photoUrl} alt="그날의 기억" />
              </div>
            )}
            <p className={s.body}>{memory.item.body}</p>
            {memory.item.attachments?.slice(0, 5).map((file) => (
              <MediaPlayer key={file.id} file={file} />
            ))}
            <div className={s.actions}>
              <Link
                className="button primary"
                href={memory.href}
                onClick={() => setOpen(false)}
              >
                원문 보기
                <Icon name="arrow" size={16} />
              </Link>
              <button
                type="button"
                className="button secondary"
                onClick={reveal}
              >
                다른 기억
              </button>
            </div>
          </>
        ) : (
          <div className={s.empty}>
            <Icon name="book" size={32} />
            <h2 id="garden-memory-title">아직 나비가 품은 기억이 없어요</h2>
            <p>
              {app.sessionSource === "demo"
                ? "남겨 둔 일기와 질문의 답변이 이곳에서 반짝일 거예요."
                : "기억 프리뷰는 데모에서 만나볼 수 있어요. 서버 기록 연결은 준비 중이에요."}
            </p>
            {app.sessionSource === "demo" && (
              <Link
                className="button primary"
                href={`/pets/${pet.id}/diary/new`}
              >
                첫 기억 남기기
              </Link>
            )}
          </div>
        )}
      </dialog>
    </>
  );
}
