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
import { gardenGuide } from "../hooks/gardenGuidePreference";

// Normalized centers of the ten painted butterflies in puppy-retouched-v4.png.
const lights = [
  { x: 29.8, y: 26.1 },
  { x: 61.1, y: 29.9 },
  { x: 44.3, y: 35.9 },
  { x: 73, y: 40 },
  { x: 26.2, y: 45.3 },
  { x: 55.5, y: 48 },
  { x: 38.3, y: 55.5 },
  { x: 71.8, y: 58 },
  { x: 48.8, y: 64.1 },
  { x: 28.2, y: 66.1 },
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
  const guide = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    if (gardenGuide.shouldShow() && !guide.current?.open)
      guide.current?.showModal();
  }, []);
  const memory = memories.find((item) => item.key === selected);
  useEffect(() => {
    const node = dialog.current;
    if (open && node && !node.open) node.showModal();
    if (!open && node?.open) node.close();
  }, [open]);
  function closeGuide() {
    gardenGuide.finish();
    guide.current?.close();
  }
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
            src="/images/puppy-retouched-v4.png"
            alt=""
            fill
            unoptimized
            sizes="480px"
            loading="eager"
            className={s.art}
          />
        </div>
        <div className={s.intro}>
          <div className={s.petIdentity}>
            <span className={s.petPortrait}>
              <Photo src={pet.photoUrl} alt={`${pet.name}의 프로필 사진`} />
            </span>
            <div>
              <h1>{pet.name}의 기억 정원</h1>
              <p className={s.welcome}>함께한 순간은, 언제나 여기.</p>
            </div>
          </div>
          <button
            type="button"
            className={s.guideButton}
            aria-label="정원 이용 안내 다시 보기"
            onClick={() => guide.current?.showModal()}
          >
            <Icon name="question" size={18} />
          </button>
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
            />
          ))}
        </div>
        <div className={s.homeAction}>
          <button type="button" onClick={reveal}>
            <span className={s.actionSpark} aria-hidden="true">✧</span>
            기억 한 조각 만나기
            <span aria-hidden="true">↗</span>
          </button>
        </div>
      </section>
      <dialog
        ref={guide}
        className={`${s.memoryDialog} ${s.guide}`}
        aria-labelledby="garden-guide-title"
        onClose={() => gardenGuide.finish()}
        onCancel={() => gardenGuide.finish()}
        onClick={(event) => {
          if (event.target === event.currentTarget) closeGuide();
        }}
      >
        <button
          type="button"
          className={`icon-button ${s.guideClose}`}
          aria-label="정원 안내 닫기"
          onClick={closeGuide}
        >
          <Icon name="close" />
        </button>
        <span className={s.guideSymbol} aria-hidden="true">
          <svg viewBox="0 0 40 40">
            <path
              d="M20 22C8 0 1 9 7 22c2 5 7 6 11 3-9 15 0 14 3 3 6 13 12 7 4-4 15 5 17-11 8-9-4 1-8 4-13 7Z"
              fill="currentColor"
            />
          </svg>
        </span>
        <h2 id="garden-guide-title">기억 정원을 만나보세요</h2>
        <p>
          빛나는 나비마다
          <br />
          함께 남긴 기억이 머물러 있어요.
        </p>
        <div className={s.guideSteps}>
          <p>
            <Icon name="question" size={18} />
            <span>나비를 누르면 일기나 질문의 답변이 열려요.</span>
          </p>
          <p>
            <Icon name="book" size={18} />
            <span>다른 기억을 만나거나 원문을 펼쳐 보세요.</span>
          </p>
        </div>
        <button
          type="button"
          className="button primary full"
          onClick={closeGuide}
        >
          정원 둘러보기
        </button>
      </dialog>
      <dialog
        ref={dialog}
        className={s.memoryDialog}
        onClose={() => setOpen(false)}
        onClick={(e) => {
          if (e.target === e.currentTarget) setOpen(false);
        }}
        aria-labelledby="garden-memory-title"
      >
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
