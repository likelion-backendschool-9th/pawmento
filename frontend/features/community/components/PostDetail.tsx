"use client";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useRef, useState } from "react";
import { Photo } from "@/components/Photo";
import { Icon } from "@/components/Icon";
import { useApp } from "@/app/AppProvider";
import { categories } from "@/features/community/utils/categories";
import type { Post } from "@/features/community/types/community";
import type { PostApi } from "@/features/community/api/postApi";
import {
  isLoginRequired,
  useLoginRedirect,
  usePost,
  usePostActions,
} from "@/features/community/hooks/usePosts";
import { useLife } from "@/features/shell/hooks/useLife";
import {
  LifeEmpty,
  LifeStatus,
  lifeStyles as s,
} from "@/features/shell/components/LifeUi";
export function PostDetail({ post }: { post: Post }) {
  const life = useLife();
  const router = useRouter();
  const dialog = useRef<HTMLDialogElement>(null);
  const [comment, setComment] = useState("");
  const [editing, setEditing] = useState<string>();
  const [editBody, setEditBody] = useState("");
  const current = life.state?.posts.find((item) => item.id === post.id) ?? post;
  const comments =
    life.state?.comments.filter((item) => item.postId === post.id) ?? [];
  async function remove() {
    if (!confirm("이 게시글과 댓글을 삭제할까요?")) return;
    const result = await life.run(async () => {
      await life.app.dependencies.life.deletePost(life.actor, post.id);
      return true;
    }, "게시글을 삭제했어요.");
    if (result) router.push(`/community?category=${current.category}`);
  }
  return (
    <div className={s.reading}>
      <Link
        href={`/community?category=${current.category}`}
        className="text-link"
      >
        ← {categories[current.category]}
      </Link>
      <article className={s.article}>
        <span className="badge">{categories[current.category]}</span>
        <h1>{current.title}</h1>
        <div className={s.articleMeta}>
          <span>
            {current.author} ·{" "}
            {new Date(current.createdAt).toLocaleString("ko-KR")}
          </span>
          {current.authorId === life.actor.id && (
            <span>
              <Link href={`/community/${current.id}/edit`}>수정</Link>
              <button onClick={() => void remove()}>삭제</button>
            </span>
          )}
        </div>
        {current.photoUrl && (
          <Photo
            src={current.photoUrl}
            alt={current.title}
            className={s.articlePhoto}
          />
        )}
        <p className={s.articleBody}>{current.body}</p>
        <div className={s.articleActions}>
          <button
            aria-pressed={current.likes.includes(life.actor.id)}
            disabled={life.busy}
            onClick={() =>
              void life.run(() =>
                life.app.dependencies.life.togglePost(
                  life.actor,
                  post.id,
                  "likes",
                ),
              )
            }
          >
            <Icon name="heart" />
            좋아요 {current.likes.length}
          </button>
          <button
            aria-pressed={current.bookmarks.includes(life.actor.id)}
            disabled={life.busy}
            onClick={() =>
              void life.run(() =>
                life.app.dependencies.life.togglePost(
                  life.actor,
                  post.id,
                  "bookmarks",
                ),
              )
            }
          >
            <Icon name="bookmark" />
            {current.bookmarks.includes(life.actor.id) ? "북마크됨" : "북마크"}
          </button>
          <button
            className="text-link"
            onClick={() => dialog.current?.showModal()}
          >
            신고하기
          </button>
        </div>
      </article>
      <LifeStatus error={life.error} />
      <section className={s.panel}>
        <h2>
          함께 나눈 말 <span>{comments.length}</span>
        </h2>
        <form
          onSubmit={async (event) => {
            event.preventDefault();
            const result = await life.run(
              () =>
                life.app.dependencies.life.saveComment(
                  life.actor,
                  post.id,
                  comment,
                ),
              "댓글을 남겼어요.",
            );
            if (result) setComment("");
          }}
          className={s.commentForm}
        >
          <textarea
            required
            value={comment}
            onChange={(event) => setComment(event.target.value)}
            rows={3}
            maxLength={1000}
            placeholder="따뜻한 한마디를 남겨주세요."
            aria-label="댓글 내용"
          />
          <button className="button primary" disabled={life.busy}>
            댓글 남기기
          </button>
        </form>
        <div>
          {comments.map((item) => (
            <div key={item.id} className={s.comment}>
              <header>
                <strong>{item.author}</strong>
                <small>
                  {new Date(item.createdAt).toLocaleDateString("ko-KR")}
                </small>
                {item.authorId === life.actor.id && (
                  <span>
                    <button
                      onClick={() => {
                        setEditing(item.id);
                        setEditBody(item.body);
                      }}
                    >
                      수정
                    </button>
                    <button
                      onClick={() => {
                        if (confirm("댓글을 삭제할까요?"))
                          void life.run(() =>
                            life.app.dependencies.life.deleteComment(
                              life.actor,
                              item.id,
                            ),
                          );
                      }}
                    >
                      삭제
                    </button>
                  </span>
                )}
              </header>
              {editing === item.id ? (
                <form
                  onSubmit={async (event) => {
                    event.preventDefault();
                    const result = await life.run(() =>
                      life.app.dependencies.life.saveComment(
                        life.actor,
                        post.id,
                        editBody,
                        item.id,
                      ),
                    );
                    if (result) setEditing(undefined);
                  }}
                >
                  <textarea
                    value={editBody}
                    onChange={(event) => setEditBody(event.target.value)}
                    required
                    maxLength={1000}
                    aria-label="댓글 수정"
                  />
                  <button className="button secondary" disabled={life.busy}>
                    수정 저장
                  </button>
                  <button
                    type="button"
                    className="text-link"
                    onClick={() => setEditing(undefined)}
                  >
                    취소
                  </button>
                </form>
              ) : (
                <p>{item.body}</p>
              )}
            </div>
          ))}
        </div>
      </section>
      <dialog ref={dialog} className="dialog">
        <h2>게시글 신고</h2>
        <p>브라우저 데모의 신고 처리 목록에 접수됩니다.</p>
        <form
          onSubmit={async (event) => {
            event.preventDefault();
            const reason = String(
              new FormData(event.currentTarget).get("reason"),
            );
            const result = await life.run(async () => {
              await life.app.dependencies.life.reportPost(
                life.actor,
                post.id,
                reason,
              );
              return true;
            }, "신고를 접수했어요.");
            if (result) dialog.current?.close();
          }}
          className="form-stack"
        >
          <label>
            신고 사유
            <textarea name="reason" required maxLength={500} rows={4} />
          </label>
          <LifeStatus error={life.error} />
          <div className="dialog-actions">
            <button
              type="button"
              className="button secondary"
              onClick={() => dialog.current?.close()}
            >
              취소
            </button>
            <button className="button primary" disabled={life.busy}>
              신고 접수
            </button>
          </div>
        </form>
      </dialog>
    </div>
  );
}

export function PostNotFound() {
  return (
    <LifeEmpty
      title="게시글을 찾을 수 없어요"
      body="삭제되었거나 운영 처리된 글이에요."
      href="/community"
      action="목록으로"
    />
  );
}

// 가입·로그인한 회원의 상세(#104-S4). 수정·삭제는 작성자에게만 보인다.
// 좋아요·북마크·신고·댓글·사진은 서버 API가 없어 그리지 않는다(#104-S10).
export function ServerPostDetail({
  posts: api,
  postId,
}: {
  posts: PostApi;
  postId: string;
}) {
  const app = useApp();
  const router = useRouter();
  const toLogin = useLoginRedirect();
  const loaded = usePost(api, postId);
  const actions = usePostActions(api);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  if (loaded.status === "missing") return <PostNotFound />;
  if (loaded.status === "error")
    return (
      <div className="form-stack">
        <LifeStatus error={loaded.error} />
        <button className="button secondary" onClick={loaded.retry}>
          다시 시도
        </button>
      </div>
    );
  const post = loaded.post;
  if (!post) return <LifeStatus loading />;
  const mine = post.authorId === app.account?.id;
  const listHref = `/community?category=${encodeURIComponent(post.category)}`;
  async function remove(postId: string) {
    if (!confirm("이 게시글을 삭제할까요?")) return;
    setBusy(true);
    setError("");
    try {
      await actions.remove(postId);
      app.notify("게시글을 삭제했어요.");
      router.push(listHref);
    } catch (cause) {
      if (isLoginRequired(cause)) return toLogin();
      setError(cause instanceof Error ? cause.message : "삭제하지 못했어요.");
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className={s.reading}>
      <Link href={listHref} className="text-link">
        ← {post.categoryName}
      </Link>
      <article className={s.article}>
        <span className="badge">{post.categoryName}</span>
        <h1>{post.title}</h1>
        <div className={s.articleMeta}>
          <span>
            {mine ? "나" : "보호자"} ·{" "}
            {new Date(post.createdAt).toLocaleString("ko-KR")}
          </span>
          {mine && (
            <span>
              <Link href={`/community/${post.id}/edit`}>수정</Link>
              <button disabled={busy} onClick={() => void remove(post.id)}>
                삭제
              </button>
            </span>
          )}
        </div>
        <p className={s.articleBody}>{post.body}</p>
      </article>
      <LifeStatus error={error} />
    </div>
  );
}
