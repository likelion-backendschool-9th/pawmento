"use client";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { Icon } from "@/shared/ui/Icon";
import { Photo } from "@/shared/ui/Photo";
import { QuestionAnswer } from "@/shared/ui/QuestionAnswer";
import { drawMemory, postcardScenes } from "../../application/postcardScenes";
import type { Pet } from "@/workspace/domain/models";
import type { Postcard } from "../../domain/models";
import { PostcardPaper } from "./PostcardPaper";
import { downloadPostcard } from "../outbound/downloadPostcard";
import { useLife } from "./useLife";
import {
  LifeHeading,
  LifeStatus,
  LifeEmpty,
  DemoNote,
  lifeStyles as s,
} from "./LifeUi";
export function MailboxScreen({
  pet,
  segments = [],
}: {
  pet: Pet;
  segments?: string[];
}) {
  const life = useLife();
  const [tab, setTab] = useState("all");
  if (!life.state) return <LifeStatus loading error={life.error} />;
  if (segments[0] === "new") return <PostcardComposer pet={pet} />;
  if (segments[0]) {
    const card = life.state.postcards.find(
      (item) =>
        item.id === segments[0] &&
        item.petId === pet.id &&
        item.ownerId === life.actor.id,
    );
    return card ? (
      <PostcardDetail pet={pet} card={card} />
    ) : (
      <LifeEmpty
        title="엽서를 찾을 수 없어요"
        body="내가 만든 엽서만 열어볼 수 있어요."
        href={`/pets/${pet.id}/garden/mailbox`}
        action="우체통으로"
      />
    );
  }
  const cards = life.state.postcards.filter(
    (card) =>
      card.petId === pet.id &&
      card.ownerId === life.actor.id &&
      (tab !== "saved" || card.saved),
  );
  return (
    <>
      <LifeHeading
        label="MEMORY MAILBOX"
        title="기억의 우체통"
        body="우연히 펼친 기억 하나가, 새로운 수채화 장면이 되어 도착해요."
      >
        <Link
          className="button primary"
          href={`/pets/${pet.id}/garden/mailbox/new`}
        >
          <Icon name="plus" size={16} />
          데모 엽서 만들기
        </Link>
      </LifeHeading>
      <DemoNote>
        기록을 랜덤으로 뽑아 새 엽서 그림을 만드는 흐름의 데모예요. 실제 AI 호출
        없이 공원·바닷가·낮잠 샘플 그림을 보여주며, 반려견의 외형을 실시간으로
        반영하지는 않습니다.
      </DemoNote>
      <div className={s.tabs}>
        <button
          className={tab === "all" ? s.selected : ""}
          onClick={() => setTab("all")}
        >
          내가 만든 엽서
        </button>
        <button
          className={tab === "saved" ? s.selected : ""}
          onClick={() => setTab("saved")}
        >
          보관함
        </button>
        <span>{cards.length}장의 엽서</span>
      </div>
      {cards.length ? (
        <div className={s.threeGrid}>
          {cards.map((card) => (
            <Link
              className={s.postcardCard}
              key={card.id}
              href={`/pets/${pet.id}/garden/mailbox/${card.id}`}
            >
              <div>
                {card.imageUrl ? (
                  <Photo src={card.imageUrl} alt={card.title} />
                ) : (
                  <span className={s.postcardPlaceholder}>
                    <Icon name="mail" size={42} />
                  </span>
                )}
              </div>
              <section>
                <span className="badge">
                  {card.status === "PROCESSING"
                    ? "만드는 중"
                    : card.status === "FAILED"
                      ? "다시 시도 필요"
                      : "데모 엽서"}
                </span>
                <h2>{card.title}</h2>
                <p>{card.text || "선택한 기록을 엽서로 엮고 있어요."}</p>
                <small>
                  {card.createdAt.slice(0, 10)}
                  {card.saved ? " · 보관 중" : ""}
                </small>
              </section>
            </Link>
          ))}
        </div>
      ) : (
        <LifeEmpty
          title="조용히 기다리는 우체통"
          body="기억하고 싶은 기록을 골라 첫 엽서를 만들어보세요."
          href={`/pets/${pet.id}/garden/mailbox/new`}
          action="데모 엽서 만들기"
        />
      )}
    </>
  );
}
function PostcardComposer({ pet }: { pet: Pet }) {
  const life = useLife();
  const router = useRouter();
  const [selectedId, setSelectedId] = useState("");
  const [consent, setConsent] = useState(false);
  const records = life.app.state.records.filter(
    (record) => record.petId === pet.id,
  );
  const selected = records.find((record) => record.id === selectedId);
  const referencePhoto = pet.photoUrl || selected?.photoUrl;
  function draw() {
    const pool =
      records.length > 1
        ? records.filter((record) => record.id !== selectedId)
        : records;
    setSelectedId(drawMemory(pool)?.id ?? "");
  }
  async function generate(event: React.FormEvent) {
    event.preventDefault();
    if (!selected) return;
    const result = await life.run(() =>
      life.app.dependencies.life.requestPostcard(
        life.actor,
        pet.id,
        pet.name,
        [selected],
        consent,
        undefined,
        pet.photoUrl,
      ),
    );
    if (result) {
      setSelectedId("");
      setConsent(false);
      router.push(`/pets/${pet.id}/garden/mailbox/${result.id}`);
    }
  }
  return (
    <>
      <Link className="text-link" href={`/pets/${pet.id}/garden/mailbox`}>
        ← 기억의 우체통
      </Link>
      <LifeHeading
        label="A MEMORY BECOMES A SCENE"
        title="우연히 펼친 기억 한 장"
        body="기록 하나를 랜덤으로 뽑고, 그날의 우리 아이를 새로운 그림 속에서 만나요."
      />
      <form onSubmit={generate}>
        <div className={s.twoGrid}>
          <section className={s.panel}>
            <h2>1. 어떤 기억이 찾아올까요?</h2>
            <p className={s.meta}>
              {pet.name}의 일기와 기록 {records.length}개 중 한 장을 뽑아요.
            </p>
            <button
              type="button"
              className="button secondary full"
              disabled={life.busy || !records.length}
              onClick={draw}
            >
              <Icon name="shuffle" size={18} />
              {selected ? "다른 기억 랜덤으로 뽑기" : "랜덤으로 기억 뽑기"}
            </button>
            {selected ? (
              <div className={s.drawnMemory} aria-live="polite">
                <span className="badge">
                  이번에 찾아온 기억 · {selected.recordedDate}
                </span>
                {selected.type === "DIARY" ? (
                  <QuestionAnswer
                    question={selected.question}
                    answer={selected.body}
                  />
                ) : (
                  <p>{selected.body || "사진으로 남긴 소중한 순간"}</p>
                )}
                {selected.photoUrl && (
                  <div className={s.sourcePhoto}>
                    <Photo
                      src={selected.photoUrl}
                      alt="뽑힌 기록의 원본 사진"
                    />
                  </div>
                )}
                <small>이 기록의 상황을 바탕으로 장면을 구성해요.</small>
              </div>
            ) : records.length ? (
              <div className={s.drawEmpty}>
                <Icon name="mail" size={42} />
                <p>
                  어느 날의 기억이
                  <br />
                  작은 엽서가 되어 올까요?
                </p>
              </div>
            ) : (
              <LifeEmpty
                title="원본 기록이 필요해요"
                body="기억을 먼저 남긴 뒤 엽서를 만들어보세요."
                href={`/pets/${pet.id}/records/new`}
                action="기록 남기기"
              />
            )}
          </section>
          <section className={s.panel}>
            <h2>2. 그날의 장면을 수채화로</h2>
            <div className={s.petReference}>
              <div>
                <Photo
                  src={referencePhoto}
                  alt={`${pet.name}의 외형 참고 사진`}
                />
              </div>
              <section>
                <strong>{pet.name}</strong>
                <p>반려견 외형 참고 사진</p>
                <small>
                  {pet.photoUrl
                    ? "프로필 사진"
                    : selected?.photoUrl
                      ? "기록에 담긴 사진"
                      : "등록된 사진이 없어요"}
                </small>
              </section>
            </div>
            <ol className={s.creationSteps}>
              <li>랜덤으로 뽑은 기억의 상황 읽기</li>
              <li>반려견 사진과 상황을 함께 참조하기</li>
              <li>그 장면 속 반려견을 새 엽서 그림으로 표현하기</li>
            </ol>
            <DemoNote>
              지금은 프론트엔드 데모예요. 기록 내용에 따라 미리 준비한 코기
              수채화 3종 중 하나를 보여줍니다. 사진을 분석하거나 새로운 이미지를
              실시간 생성하지 않습니다. 다른 반려동물을 등록해도 코기 샘플이
              표시돼요.
            </DemoNote>
            <label className={s.checkLabel}>
              <input
                type="checkbox"
                checked={consent}
                onChange={(event) => setConsent(event.target.checked)}
                required
                disabled={life.busy}
              />
              <span>샘플 그림으로 보여주는 데모임을 확인했어요.</span>
            </label>
            <p className={s.meta}>
              기억 한 장 · 수채화 엽서 한 장 · 하루 최대 5장
            </p>
            <LifeStatus error={life.error} />
            <button
              className="button primary full"
              disabled={life.busy || !selected || !consent}
            >
              <Icon name="mail" size={17} />
              {life.busy
                ? "기억을 그림으로 옮기는 중…"
                : "이 기억으로 데모 엽서 만들기"}
            </button>
            {life.busy && (
              <div role="status" className={s.progress}>
                <progress />
                뽑은 기억과 참고 사진으로 엽서 미리보기를 준비해요.
              </div>
            )}
          </section>
        </div>
      </form>
    </>
  );
}
function PostcardDetail({ pet, card }: { pet: Pet; card: Postcard }) {
  const life = useLife();
  const router = useRouter();
  const current =
    life.state?.postcards.find((item) => item.id === card.id) ?? card;
  const sources = life.app.state.records.filter((record) =>
    current.sourceRecordIds.includes(record.id),
  );
  return (
    <div className={s.reading}>
      <Link className="text-link" href={`/pets/${pet.id}/garden/mailbox`}>
        ← 기억의 우체통
      </Link>
      <LifeHeading
        label="A LITTLE POSTCARD"
        title={current.title}
        body="우리의 기록에서 꺼낸 작은 이야기."
      />
      {current.status === "PROCESSING" ? (
        <div className={s.empty} role="status">
          <progress />
          <h2>엽서를 준비하고 있어요</h2>
          <p>잠시 뒤 이 화면이 자동으로 갱신돼요.</p>
          <button
            className="button secondary"
            disabled={life.busy}
            onClick={() =>
              void life.run(() =>
                life.app.dependencies.life.requestPostcard(
                  life.actor,
                  pet.id,
                  pet.name,
                  sources,
                  true,
                  current.id,
                  current.demoArtwork?.referencePhotoUrl || pet.photoUrl,
                ),
              )
            }
          >
            중단된 데모 생성 다시 시도
          </button>
        </div>
      ) : current.status === "FAILED" ? (
        <div className={s.panel}>
          <p className="error" role="alert">
            {current.error}
          </p>
          <button
            className="button primary"
            disabled={life.busy}
            onClick={() =>
              void life.run(() =>
                life.app.dependencies.life.requestPostcard(
                  life.actor,
                  pet.id,
                  pet.name,
                  sources,
                  true,
                  current.id,
                  current.demoArtwork?.referencePhotoUrl || pet.photoUrl,
                ),
              )
            }
          >
            다시 시도
          </button>
        </div>
      ) : (
        <>
          <PostcardPaper card={current} petName={pet.name} />
          {current.demoArtwork && (
            <section className={`${s.panel} ${s.artworkProvenance}`}>
              <span className="badge">
                수채화 샘플 · {postcardScenes[current.demoArtwork.scene].title}
              </span>
              <h2>이 기억이 한 장의 그림으로</h2>
              <p>
                랜덤으로 뽑힌 기록 1개와 {pet.name}의 참고 사진을 연결한
                데모입니다. 표시된 그림은 미리 준비한 코기 샘플이에요.
              </p>
              {current.demoArtwork.referencePhotoUrl && (
                <div className={s.petReference}>
                  <div>
                    <Photo
                      src={current.demoArtwork.referencePhotoUrl}
                      alt="엽서에 연결한 반려견 참고 사진"
                    />
                  </div>
                  <section>
                    <strong>{pet.name}의 참고 사진</strong>
                    <small>새 장면 속 외형을 참고할 사진</small>
                  </section>
                </div>
              )}
              <details>
                <summary>이 기록으로 구성한 장면 설명</summary>
                <p>{current.demoArtwork.prompt}</p>
              </details>
            </section>
          )}
          <div className={s.inlineActions}>
            <button
              className="button secondary"
              onClick={() =>
                void life.run(
                  () =>
                    life.app.dependencies.life.togglePostcard(
                      life.actor,
                      current.id,
                    ),
                  current.saved ? "보관을 해제했어요." : "보관함에 담았어요.",
                )
              }
            >
              <Icon name="bookmark" size={16} />
              {current.saved ? "보관 해제" : "보관함에 담기"}
            </button>
            <button
              className="button primary"
              onClick={() =>
                void life.run(
                  () => downloadPostcard(current, pet.name),
                  "엽서 파일을 준비했어요.",
                )
              }
            >
              <Icon name="download" size={16} />
              엽서 다운로드
            </button>
            <button
              className="text-link danger"
              onClick={() => {
                if (confirm("데모 엽서를 삭제할까요? 원본 기록은 유지돼요."))
                  void life.run(async () => {
                    await life.app.dependencies.life.deletePostcard(
                      life.actor,
                      current.id,
                    );
                    router.push(`/pets/${pet.id}/garden/mailbox`);
                  });
              }}
            >
              엽서 삭제
            </button>
          </div>
        </>
      )}
      <LifeStatus error={life.error} />
      <section className={s.panel}>
        <h2>함께 담은 원본 기록</h2>
        {sources.length ? (
          sources.map((record) => (
            <Link
              className={s.sourceLink}
              key={record.id}
              href={`/pets/${pet.id}/${record.type === "DIARY" ? "diary" : "records"}/${record.id}`}
            >
              <span>{record.recordedDate}</span>
              <strong>
                {record.question || record.body.slice(0, 60) || "사진 기록"}
              </strong>
              <Icon name="arrow" size={15} />
            </Link>
          ))
        ) : (
          <p className="muted">원본 기록이 삭제되어 링크를 표시할 수 없어요.</p>
        )}
      </section>
    </div>
  );
}
