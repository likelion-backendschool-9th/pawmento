"use client";
import { useState } from "react";
import { Icon } from "@/components/Icon";
import { Photo } from "@/components/Photo";
import type { Postcard } from "@/features/postcard/types/Postcard";
import s from "./PostcardPaper.module.css";

export function PostcardPaper({
  card,
  petName,
}: {
  card: Postcard;
  petName: string;
}) {
  const [back, setBack] = useState(false);
  return (
    <section className={s.stationery} aria-label="기억 엽서">
      <div className={s.toolbar}>
        <span>
          POSTCARD <small>한 장에 담은 우리의 계절</small>
        </span>
        <div role="group" aria-label="엽서 면 선택">
          <button
            type="button"
            aria-pressed={!back}
            onClick={() => setBack(false)}
          >
            그림 앞면
          </button>
          <button
            type="button"
            aria-pressed={back}
            onClick={() => setBack(true)}
          >
            편지 뒷면
          </button>
        </div>
      </div>
      <div className={s.stage}>
        <article
          className={`${s.paper} ${back ? s.back : s.front}`}
          aria-label={back ? "엽서 뒷면" : "엽서 앞면"}
        >
          {back ? (
            <>
              <div className={s.letter}>
                <span className={s.kicker}>DEAR, OUR PRECIOUS DAYS</span>
                <h2>{card.title}</h2>
                <p>{card.text}</p>
                <small>
                  {card.createdAt.slice(0, 10).replaceAll("-", ". ")} · 함께
                  남긴 기억에서
                </small>
              </div>
              <div className={s.address}>
                <div className={s.postage} aria-label="발자국 기념 우표와 소인">
                  <span className={s.postmark}>
                    PAWMENTO
                    <br />
                    <small>MEMORY POST</small>
                    <br />
                    {card.createdAt.slice(0, 10)}
                  </span>
                  <span className={s.stamp}>
                    <Icon name="paw" size={31} />
                    <small>기억 우편</small>
                  </span>
                </div>
                <div className={s.recipient}>
                  <small>TO.</small>
                  <strong>{petName}와 함께한 소중한 날들에게</strong>
                  <span />
                  <span />
                  <small>FROM.</small>
                  <strong>우리의 기억을 간직하는 나</strong>
                </div>
                <span className={s.postcode} aria-label="장식용 우편번호 칸">
                  □ □ □ □ □
                </span>
              </div>
            </>
          ) : (
            <>
              <div
                className={`${s.frontArt} ${card.demoArtwork ? s.illustrated : ""}`}
              >
                {card.imageUrl ? (
                  <div
                    className={
                      card.demoArtwork ? s.scenePainting : s.photoMount
                    }
                  >
                    <Photo
                      src={card.imageUrl}
                      alt={
                        card.demoArtwork
                          ? "기록의 상황을 표현한 수채화 엽서 샘플"
                          : "선택한 기록의 원본 사진"
                      }
                    />
                  </div>
                ) : (
                  <Icon name="leaf" size={48} />
                )}
                <span className={s.frontNote}>a little memory, forever</span>
              </div>
              <div className={s.caption}>
                <div>
                  <span>OUR LITTLE POSTCARD</span>
                  <h2>{card.title}</h2>
                </div>
                <small>
                  {card.createdAt.slice(0, 10).replaceAll("-", ". ")}
                  <br />
                  PawMento
                </small>
              </div>
            </>
          )}
        </article>
      </div>
      <div className={s.under}>
        <span>{back ? "02 / 02 · 편지와 주소" : "01 / 02 · 추억의 그림"}</span>
        <button type="button" onClick={() => setBack(!back)}>
          <Icon name="shuffle" size={16} />
          엽서 뒤집기
        </button>
      </div>
      <p className={s.note}>
        {card.demoArtwork
          ? "기록의 상황을 표현한 코기 수채화 샘플입니다. 실시간 AI 생성이나 외형 분석은 하지 않습니다."
          : "이전 방식으로 만든 사진 엽서입니다. 새 엽서는 랜덤 기록과 수채화 샘플로 구성됩니다."}
      </p>
    </section>
  );
}
