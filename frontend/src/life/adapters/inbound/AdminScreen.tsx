"use client";
import Link from "next/link";
import { useState } from "react";
import { useLife } from "./useLife";
import {
  LifeHeading,
  LifeStatus,
  LifeEmpty,
  DemoNote,
  lifeStyles as s,
} from "./LifeUi";
export function AdminScreen({ section = "reports" }: { section?: string }) {
  const life = useLife();
  const [editingQuestion, setEditingQuestion] = useState<string>();
  const [editingNotice, setEditingNotice] = useState<string>();
  if (!life.actor.isAdmin)
    return (
      <LifeEmpty
        title="관리자 데모 권한이 필요해요"
        body="가입 없는 데모 계정에서 운영 화면을 둘러볼 수 있어요."
        href="/"
        action="데모 시작 화면"
      />
    );
  if (!life.state) return <LifeStatus loading error={life.error} />;
  const state = life.state;
  const question = state.questions.find((item) => item.id === editingQuestion);
  const notice = state.notices.find((item) => item.id === editingNotice);
  return (
    <>
      <LifeHeading
        label="ADMIN DEMO"
        title="운영 관리"
        body="신고·질문·공지·회원 상태를 관리하는 브라우저 데모입니다."
      />
      <DemoNote>
        실제 서비스 운영 권한이 아닙니다. 이 브라우저의 데모 데이터에만
        반영됩니다.
      </DemoNote>
      <nav className={s.tabs} aria-label="관리자 메뉴">
        {[
          ["reports", "신고 처리"],
          ["questions", "질문 풀"],
          ["notices", "공지 관리"],
          ["members", "회원 관리"],
        ].map(([key, label]) => (
          <Link
            className={section === key ? s.selected : ""}
            href={`/admin/${key}`}
            key={key}
          >
            {label}
          </Link>
        ))}
      </nav>
      <LifeStatus error={life.error} />
      {section === "reports" ? (
        <section className={s.panel}>
          <h2>접수된 신고</h2>
          {state.reports.length ? (
            <div className={s.tableWrap}>
              <table>
                <thead>
                  <tr>
                    <th>게시글</th>
                    <th>사유</th>
                    <th>상태</th>
                    <th>처리</th>
                  </tr>
                </thead>
                <tbody>
                  {state.reports.map((report) => (
                    <tr key={report.id}>
                      <td>
                        <Link href={`/community/${report.postId}`}>
                          {state.posts.find((post) => post.id === report.postId)
                            ?.title ?? "삭제된 게시글"}
                        </Link>
                      </td>
                      <td>{report.reason}</td>
                      <td>
                        {report.status === "PENDING"
                          ? "대기"
                          : report.status === "RESOLVED"
                            ? "숨김 처리"
                            : "기각"}
                      </td>
                      <td>
                        {report.status === "PENDING" && (
                          <>
                            <button
                              className="text-link"
                              disabled={life.busy}
                              onClick={() =>
                                void life.run(
                                  () =>
                                    life.app.dependencies.life.resolveReport(
                                      life.actor,
                                      report.id,
                                      "RESOLVED",
                                    ),
                                  "게시글을 숨겼어요.",
                                )
                              }
                            >
                              숨김 처리
                            </button>
                            <button
                              className="text-link"
                              disabled={life.busy}
                              onClick={() =>
                                void life.run(
                                  () =>
                                    life.app.dependencies.life.resolveReport(
                                      life.actor,
                                      report.id,
                                      "DISMISSED",
                                    ),
                                  "신고를 기각했어요.",
                                )
                              }
                            >
                              기각
                            </button>
                          </>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <p className="muted">
              아직 접수된 신고가 없어요. 커뮤니티 게시글에서 신고 흐름을 확인할
              수 있습니다.
            </p>
          )}
        </section>
      ) : section === "questions" ? (
        <div className={s.twoGrid}>
          <section className={s.panel}>
            <h2>오늘의 질문 풀</h2>
            <p className={s.meta}>
              질문을 수정해도 이미 저장한 일기의 질문은 바뀌지 않아요.
            </p>
            {state.questions.map((item) => (
              <div className={s.adminRow} key={item.id}>
                <div>
                  <strong>{item.text}</strong>
                  <small>{item.enabled ? "사용 중" : "비활성"}</small>
                </div>
                <button
                  className="text-link"
                  onClick={() => setEditingQuestion(item.id)}
                >
                  수정
                </button>
                <button
                  className="text-link"
                  disabled={life.busy}
                  onClick={() =>
                    void life.run(() =>
                      life.app.dependencies.life.toggleQuestion(
                        life.actor,
                        item.id,
                      ),
                    )
                  }
                >
                  {item.enabled ? "비활성화" : "활성화"}
                </button>
              </div>
            ))}
          </section>
          <form
            key={editingQuestion ?? "new"}
            className={`${s.panel} form-stack`}
            onSubmit={async (event) => {
              event.preventDefault();
              const result = await life.run(async () => {
                await life.app.dependencies.life.saveQuestion(
                  life.actor,
                  String(new FormData(event.currentTarget).get("text")),
                  editingQuestion,
                );
                return true;
              }, "질문을 저장했어요.");
              if (result) setEditingQuestion(undefined);
            }}
          >
            <h2>{question ? "질문 수정" : "질문 추가"}</h2>
            <label>
              질문 문구
              <textarea
                name="text"
                defaultValue={question?.text}
                required
                maxLength={300}
                rows={5}
                placeholder="오늘 {name}에게 해주고 싶은 말이 있나요?"
              />
            </label>
            <small className="muted">
              {"{name}"}은 친구의 이름으로 표시됩니다.
            </small>
            <button className="button primary" disabled={life.busy}>
              질문 저장
            </button>
            {editingQuestion && (
              <button
                className="button secondary"
                type="button"
                onClick={() => setEditingQuestion(undefined)}
              >
                새 질문 추가로
              </button>
            )}
          </form>
        </div>
      ) : section === "notices" ? (
        <div className={s.twoGrid}>
          <section className={s.panel}>
            <h2>공지 목록</h2>
            {state.notices.map((item) => (
              <div className={s.adminRow} key={item.id}>
                <div>
                  <strong>{item.title}</strong>
                  <small>{item.published ? "게시 중" : "비공개"}</small>
                </div>
                <button
                  className="text-link"
                  onClick={() => setEditingNotice(item.id)}
                >
                  수정
                </button>
                <button
                  className="text-link"
                  onClick={() =>
                    void life.run(() =>
                      life.app.dependencies.life.toggleNotice(
                        life.actor,
                        item.id,
                      ),
                    )
                  }
                >
                  {item.published ? "게시 중단" : "게시하기"}
                </button>
              </div>
            ))}
          </section>
          <form
            key={editingNotice ?? "new-notice"}
            className={`${s.panel} form-stack`}
            onSubmit={async (event) => {
              event.preventDefault();
              const data = new FormData(event.currentTarget);
              const result = await life.run(async () => {
                await life.app.dependencies.life.saveNotice(
                  life.actor,
                  String(data.get("title")),
                  String(data.get("body")),
                  editingNotice,
                );
                return true;
              }, "공지를 저장했어요.");
              if (result) setEditingNotice(undefined);
            }}
          >
            <h2>{notice ? "공지 수정" : "새 공지"}</h2>
            <label>
              제목
              <input
                name="title"
                defaultValue={notice?.title}
                required
                maxLength={100}
              />
            </label>
            <label>
              내용
              <textarea
                name="body"
                defaultValue={notice?.body}
                required
                maxLength={3000}
                rows={7}
              />
            </label>
            <button className="button primary" disabled={life.busy}>
              공지 저장
            </button>
            {editingNotice && (
              <button
                className="button secondary"
                type="button"
                onClick={() => setEditingNotice(undefined)}
              >
                새 공지 작성
              </button>
            )}
          </form>
        </div>
      ) : section === "members" ? (
        <section className={s.panel}>
          <h2>데모 회원</h2>
          <div className={s.tableWrap}>
            <table>
              <thead>
                <tr>
                  <th>이름</th>
                  <th>상태</th>
                  <th>관리</th>
                </tr>
              </thead>
              <tbody>
                {state.members.map((member) => (
                  <tr key={member.id}>
                    <td>{member.name}</td>
                    <td>
                      {member.status === "ACTIVE" ? "정상" : "데모 활동 제한"}
                    </td>
                    <td>
                      <button
                        className="text-link"
                        disabled={life.busy || member.id === life.actor.id}
                        onClick={() =>
                          void life.run(
                            () =>
                              life.app.dependencies.life.toggleMember(
                                life.actor,
                                member.id,
                              ),
                            "데모 회원 상태를 바꿨어요.",
                          )
                        }
                      >
                        {member.status === "ACTIVE" ? "활동 제한" : "제한 해제"}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      ) : (
        <LifeEmpty
          title="관리 메뉴를 선택해 주세요"
          body="상단 메뉴에서 운영 기능을 확인할 수 있어요."
          href="/admin/reports"
          action="신고 처리"
        />
      )}
    </>
  );
}
