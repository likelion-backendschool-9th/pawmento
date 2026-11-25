"use client";
import { useRef, useState } from "react";
import { Icon } from "./Icon";
import { Photo } from "./Photo";
import s from "./PhotoAttachmentCarousel.module.css";

export type PhotoSlide = { id: string; src: string; label: string; remove: () => void };

export function PhotoAttachmentCarousel({ photos, disabled }: { photos: PhotoSlide[]; disabled: boolean }) {
  const track = useRef<HTMLDivElement>(null);
  const [position, setPosition] = useState(0);
  const current = Math.min(position, photos.length - 1);
  function go(index: number) {
    const node = track.current;
    const slide = node?.children[index] as HTMLElement | undefined;
    if (node && slide) node.scrollTo({ left: slide.offsetLeft });
  }
  return (
    <section className={s.carousel} aria-label="첨부한 사진" aria-roledescription="캐러셀">
      <div className={s.heading}>
        <strong>사진 <span>{photos.length}장</span></strong>
        <div className={s.controls}>
          <span className={s.count} aria-live="polite">{current + 1} / {photos.length}</span>
          {photos.length > 1 && <>
            <button type="button" aria-label="이전 사진" disabled={current === 0} onClick={() => go(current - 1)}><Icon name="chevron" size={17} /></button>
            <button type="button" aria-label="다음 사진" disabled={current === photos.length - 1} onClick={() => go(current + 1)}><Icon name="chevron" size={17} /></button>
          </>}
        </div>
      </div>
      <div ref={track} className={s.track} tabIndex={0} role="group" aria-label="사진 목록 · 좌우로 넘겨보세요"
        onKeyDown={event => {
          if (event.target !== event.currentTarget) return;
          if (event.key === "ArrowRight" || event.key === "ArrowLeft") {
            event.preventDefault();
            go(Math.max(0, Math.min(photos.length - 1, current + (event.key === "ArrowRight" ? 1 : -1))));
          }
        }}
        onScroll={event => {
          const node = event.currentTarget;
          const slides = Array.from(node.children) as HTMLElement[];
          const nearest = slides.reduce((best, slide, index) => Math.abs(slide.offsetLeft - node.scrollLeft) < Math.abs(slides[best].offsetLeft - node.scrollLeft) ? index : best, 0);
          setPosition(nearest);
        }}>
        {photos.map((photo, index) => (
          <div key={photo.id} className={s.slide} role="group" aria-roledescription="슬라이드" aria-label={`${index + 1} / ${photos.length}`}>
            <Photo src={photo.src} alt={photo.label} className={s.photo} />
            <button type="button" className={s.remove} aria-label={`${index + 1}번째 사진 삭제`} disabled={disabled} onClick={photo.remove}><Icon name="close" size={17} /></button>
            <span className={s.caption} title={photo.label}>{photo.label}</span>
          </div>
        ))}
      </div>
      {photos.length > 1 && <div className={s.pagination} aria-label="사진 선택">
        {photos.map((photo, index) => <button key={photo.id} type="button" aria-label={`${index + 1}번째 사진 보기`} aria-current={index === current ? "true" : undefined} onClick={() => go(index)}><span /></button>)}
      </div>}
    </section>
  );
}
