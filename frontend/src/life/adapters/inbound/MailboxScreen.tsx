"use client";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { Icon } from "@/shared/ui/Icon";
import { Photo } from "@/shared/ui/Photo";
import type { Pet } from "@/workspace/domain/models";
import type { Postcard } from "../../domain/models";
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
  if (pet.mode !== "GARDEN")
    return (
      <LifeEmpty
        title="우체통은 정원에 있어요"
        body="기억의 정원 모드로 직접 전환한 뒤 이용할 수 있어요."
        href={`/pets/${pet.id}/settings/mode`}
        action="모드 설정"
      />
    );
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
        body="기록을 바탕으로 엮은 작은 엽서를, 내가 원할 때 천천히 펼쳐봐요."
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
        실제 AI가 생성하거나 반려동물이 보낸 편지가 아닙니다. 선택한 기록으로
        구성하는 프론트엔드 미리보기예요. 자동 생성·푸시·외부 전송은 없습니다.
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
  const [selected, setSelected] = useState<string[]>([]);
  const [consent, setConsent] = useState(false);
  const records = life.app.state.records.filter(
    (record) => record.petId === pet.id,
  );
  async function generate(event: React.FormEvent) {
    event.preventDefault();
    const task = life.app.dependencies.life.requestPostcard(
      life.actor,
      pet.id,
      pet.name,
      records.filter((record) => selected.includes(record.id)),
      consent,
    );
    const result = await life.run(() => task);
    if (result) {
      setSelected([]);
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
        label="MAKE A POSTCARD"
        title="작은 기억을 엽서로"
        body="소중한 기록을 선택해 데모 엽서를 미리 만들어보세요."
      />
      <form onSubmit={generate}>
        <div className={s.twoGrid}>
          <section className={s.panel}>
            <h2>1. 함께 담을 기록</h2>
            {records.length ? (
              records.map((record) => (
                <label className={s.recordChoice} key={record.id}>
                  <input
                    type="checkbox"
                    checked={selected.includes(record.id)}
                    onChange={(event) =>
                      setSelected((items) =>
                        event.target.checked
                          ? [...items, record.id]
                          : items.filter((id) => id !== record.id),
                      )
                    }
                    disabled={life.busy}
                  />
                  <div>
                    <strong>
                      {record.recordedDate}
                      {record.type === "DIARY" ? " · 질문 다이어리" : ""}
                    </strong>
                    <p>
                      {record.question || record.body || "사진으로 남긴 기억"}
                    </p>
                  </div>
                </label>
              ))
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
            <h2>2. 생성 안내 확인</h2>
            <p>
              선택한 질문과 답변·사진을 엽서로 엮습니다. 원본 기록은 그대로
              유지돼요.
            </p>
            <DemoNote>
              실제 AI 호출 없이 로컬 템플릿으로 만드는 데모입니다. 실제 AI 생성
              이미지나 반려동물의 말로 표시하지 않습니다.
            </DemoNote>
            <label className={s.checkLabel}>
              <input
                type="checkbox"
                checked={consent}
                onChange={(event) => setConsent(event.target.checked)}
                required
              />
              <span>데모 미리보기이며 실제 AI 생성이 아님을 확인했어요.</span>
            </label>
            <p className={s.meta}>
              선택한 기록 {selected.length}개 · 하루 최대 5장
            </p>
            <LifeStatus error={life.error} />
            <button
              className="button primary full"
              disabled={life.busy || !selected.length || !consent}
            >
              {life.busy ? "기록을 엽서로 엮는 중…" : "데모 엽서 만들기"}
            </button>
            {life.busy && (
              <div role="status" className={s.progress}>
                <progress />
                원본을 보존하며 미리보기를 준비하고 있어요.
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
                ),
              )
            }
          >
            다시 시도
          </button>
        </div>
      ) : (
        <>
          <article className={s.postcardDetail}>
            {current.imageUrl && (
              <Photo
                src={current.imageUrl}
                alt="선택한 원본 기록 사진"
                className={s.postcardImage}
              />
            )}
            <div>
              <span className="badge">데모 엽서 · 실제 AI 생성 아님</span>
              <h2>{current.title}</h2>
              <p>{current.text}</p>
              <small>{current.createdAt.slice(0, 10)} · PawMento</small>
            </div>
          </article>
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
                  () => downloadPostcard(current),
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
