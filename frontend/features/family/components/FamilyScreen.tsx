"use client";
import Link from "next/link";
import { useState } from "react";
import { Icon } from "@/components/Icon";
import type { Pet } from "@/features/workspace/domain/models";
import type { FamilyRole } from "@/features/life/domain/models";
import { useLife } from "@/features/shell/hooks/useLife";
import {
  LifeHeading,
  LifeStatus,
  LifeEmpty,
  DemoNote,
  lifeStyles as s,
} from "@/features/shell/components/LifeUi";
export function FamilyScreen({
  pet,
  inviteId,
}: {
  pet?: Pet;
  inviteId?: string;
}) {
  const life = useLife();
  const [role, setRole] = useState<FamilyRole>("EDITOR");
  const [created, setCreated] = useState("");
  const [accepted, setAccepted] = useState(false);
  if (!life.state) return <LifeStatus loading error={life.error} />;
  if (inviteId) {
    const family = life.state.families.find((item) =>
      item.invites.some((invite) => invite.id === inviteId),
    );
    const invite = family?.invites.find((item) => item.id === inviteId);
    return (
      <>
        <LifeHeading
          label="FAMILY INVITATION"
          title="함께 기록할 가족을 초대했어요"
          body="같은 브라우저 안에서 함께 기록하는 데모 초대입니다."
        />
        {accepted ? (
          <LifeEmpty
            title="가족 참여를 확인했어요"
            body="반려동물 목록에서 공유된 친구를 선택할 수 있어요."
            href="/pets"
            action="함께하는 친구들"
          />
        ) : !invite ||
          invite.used ||
          invite.expiresAt < new Date().toISOString() ? (
          <LifeEmpty
            title="유효하지 않은 초대예요"
            body="이미 사용했거나 만료된 초대는 다시 받을 수 있어요."
            href="/home"
            action="홈으로"
          />
        ) : (
          <form
            className={`${s.panel} form-stack`}
            onSubmit={async (event) => {
              event.preventDefault();
              const result = await life.run(
                () =>
                  life.app.dependencies.life.acceptInvite(
                    life.actor,
                    inviteId,
                    String(new FormData(event.currentTarget).get("name")),
                  ),
                "가족으로 참여했어요.",
              );
              if (result) {
                await life.app.refresh();
                setAccepted(true);
              }
            }}
          >
            <h2>참여 역할: {invite.role === "EDITOR" ? "편집자" : "열람자"}</h2>
            <p>
              {invite.role === "EDITOR"
                ? "공유된 친구의 기록을 읽고 작성·수정할 수 있어요."
                : "공유된 친구의 기록을 읽을 수 있어요."}
            </p>
            <label>
              가족에게 보여줄 이름
              <input
                name="name"
                defaultValue={life.actor.name}
                required
                maxLength={20}
              />
            </label>
            <LifeStatus error={life.error} />
            <button className="button primary" disabled={life.busy}>
              초대 수락하기
            </button>
          </form>
        )}
      </>
    );
  }
  if (!pet)
    return (
      <LifeEmpty
        title="친구를 먼저 선택해 주세요"
        body="반려동물별로 가족을 초대할 수 있어요."
        href="/pets"
        action="반려동물 선택"
      />
    );
  const family = life.state.families.find((item) => item.petId === pet.id);
  const owner = !family || family.ownerId === life.actor.id;
  async function copy(id: string) {
    try {
      await navigator.clipboard.writeText(
        `${location.origin}/family/invite/${id}`,
      );
      life.app.notify("초대 링크를 복사했어요.");
    } catch {
      life.app.notify("초대 주소를 선택해 직접 복사해 주세요.");
    }
  }
  return (
    <>
      <LifeHeading
        label="FAMILY SPACE"
        title={`${pet.name}와 함께 기록하는 가족`}
        body="역할을 나누고, 같은 친구의 소중한 순간을 함께 남겨요."
      />
      <DemoNote>
        같은 브라우저의 다른 데모 계정과 기록을 공유합니다. 링크를 외부로
        보내거나 다른 기기에서 여는 실제 공유는 서버 연결 후 제공돼요.
      </DemoNote>
      <div className={s.twoGrid}>
        <section className={s.panel}>
          <div className={s.panelHeading}>
            <h2>함께하는 가족</h2>
            <span className="badge">{1 + (family?.members.length ?? 0)}명</span>
          </div>
          <div className={s.memberRow}>
            <span className={s.avatar}>
              <Icon name="user" />
            </span>
            <div>
              <strong>{owner ? life.actor.name : "소유자"}</strong>
              <small>가족과 기록을 관리해요.</small>
            </div>
            <span className="badge">소유자</span>
          </div>
          {family?.members.map((member) => (
            <div className={s.memberRow} key={member.id}>
              <span className={s.avatar}>{member.name.slice(0, 1)}</span>
              <div>
                <strong>{member.name}</strong>
                <small>
                  {member.role === "EDITOR"
                    ? "기록 작성·수정 가능"
                    : "기록 열람 가능"}
                </small>
              </div>
              {owner ? (
                <>
                  <select
                    aria-label={`${member.name} 역할`}
                    value={member.role}
                    disabled={life.busy}
                    onChange={(event) =>
                      void life.run(
                        () =>
                          life.app.dependencies.life.changeFamilyRole(
                            life.actor,
                            pet.id,
                            member.id,
                            event.target.value as FamilyRole,
                          ),
                        "역할을 변경했어요.",
                      )
                    }
                  >
                    <option value="EDITOR">편집자</option>
                    <option value="VIEWER">열람자</option>
                  </select>
                  <button
                    className="icon-button danger"
                    aria-label={`${member.name} 공유 해제`}
                    onClick={() => {
                      if (confirm("이 가족의 공유를 해제할까요?"))
                        void life.run(async () => {
                          await life.app.dependencies.life.removeFamilyMember(
                            life.actor,
                            pet.id,
                            member.id,
                          );
                          await life.app.refresh();
                        }, "공유를 해제했어요.");
                    }}
                  >
                    <Icon name="close" size={17} />
                  </button>
                </>
              ) : (
                <span className="badge">
                  {member.role === "EDITOR" ? "편집자" : "열람자"}
                </span>
              )}
            </div>
          ))}
        </section>
        <section className={s.panel}>
          <h2>가족 초대</h2>
          {owner ? (
            <form
              className="form-stack"
              onSubmit={async (event) => {
                event.preventDefault();
                const result = await life.run(
                  () =>
                    life.app.dependencies.life.inviteFamily(
                      life.actor,
                      pet.id,
                      role,
                    ),
                  "초대 링크를 만들었어요.",
                );
                if (result) setCreated(result.id);
              }}
            >
              <label>
                초대할 역할
                <select
                  value={role}
                  onChange={(event) =>
                    setRole(event.target.value as FamilyRole)
                  }
                >
                  <option value="EDITOR">편집자 — 기록 작성·수정</option>
                  <option value="VIEWER">열람자 — 기록 조회</option>
                </select>
              </label>
              <p className="muted">
                초대는 7일간 유효하며 한 번 사용할 수 있어요. 소유권과 프로필
                관리 권한은 공유되지 않습니다.
              </p>
              <button className="button primary" disabled={life.busy}>
                <Icon name="plus" size={16} />
                초대 링크 만들기
              </button>
              {created && (
                <div className={s.inviteLink}>
                  <code>
                    {typeof location !== "undefined" ? location.origin : ""}
                    /family/invite/{created}
                  </code>
                  <button
                    type="button"
                    className="button secondary"
                    onClick={() => void copy(created)}
                  >
                    링크 복사
                  </button>
                  <Link
                    href={`/family/invite/${created}`}
                    className="text-link"
                  >
                    이 브라우저에서 참여 화면 보기
                  </Link>
                </div>
              )}
            </form>
          ) : (
            <p>가족 초대와 역할 변경은 소유자만 할 수 있어요.</p>
          )}
        </section>
      </div>
      <LifeStatus error={life.error} />
      {owner && !!family?.invites.length && (
        <section className={s.panel}>
          <h2>발급한 초대</h2>
          <div className={s.tableWrap}>
            <table>
              <thead>
                <tr>
                  <th>역할</th>
                  <th>만료</th>
                  <th>상태</th>
                  <th>관리</th>
                </tr>
              </thead>
              <tbody>
                {family.invites.map((invite) => (
                  <tr key={invite.id}>
                    <td>{invite.role === "EDITOR" ? "편집자" : "열람자"}</td>
                    <td>
                      {new Date(invite.expiresAt).toLocaleDateString("ko-KR")}
                    </td>
                    <td>
                      {invite.used
                        ? "참여 완료"
                        : invite.expiresAt < new Date().toISOString()
                          ? "만료"
                          : "대기 중"}
                    </td>
                    <td>
                      <button
                        className="text-link"
                        onClick={() => void copy(invite.id)}
                      >
                        복사
                      </button>
                      <button
                        className="text-link danger"
                        disabled={life.busy}
                        onClick={() =>
                          void life.run(
                            () =>
                              life.app.dependencies.life.revokeInvite(
                                life.actor,
                                pet.id,
                                invite.id,
                              ),
                            "초대를 취소했어요.",
                          )
                        }
                      >
                        초대 취소
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}
    </>
  );
}
