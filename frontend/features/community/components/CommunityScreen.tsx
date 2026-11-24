"use client";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useRef, useState } from "react";
import { Photo } from "@/components/Photo";
import { PhotoPicker } from "@/components/PhotoPicker";
import { Icon } from "@/components/Icon";
import { categories } from "@/features/community/utils/categories";
import type { Post, Category } from "@/features/community/types/community";
import { useLife } from "@/features/shell/hooks/useLife";
import {
  LifeHeading,
  LifeStatus,
  LifeEmpty,
  DemoNote,
  lifeStyles as s,
} from "@/features/shell/components/LifeUi";
export function CommunityScreen({
  segments = [],
  activity = false,
}: {
  segments?: string[];
  activity?: boolean;
}) {
  const life = useLife();
  const router = useRouter();
  const search = useSearchParams();
  const [activityTab, setActivityTab] = useState("posts");
  if (!life.state) return <LifeStatus error={life.error} loading />;
  if (segments[0] === "new") return <PostEditor />;
  if (segments[0]) {
    const post = life.state.posts.find((item) => item.id === segments[0]);
    return !post || post.hidden ? (
      <LifeEmpty
        title="게시글을 찾을 수 없어요"
        body="삭제되었거나 운영 처리된 글이에요."
        href="/community"
        action="목록으로"
      />
    ) : segments[1] === "edit" ? (
      <PostEditor key={post.id} post={post} />
    ) : (
      <PostDetail post={post} />
    );
  }
  const requestedCategory = search.get("category");
  const category: Category =
    requestedCategory && Object.hasOwn(categories, requestedCategory)
      ? (requestedCategory as Category)
      : "LIFE";
  const query = search.get("q") ?? "";
  const sort = search.get("sort") ?? "latest";
  const update = (key: string, value: string) => {
    const params = new URLSearchParams(search.toString());
    params.set("category", category);
    if (value) params.set(key, value);
    else params.delete(key);
    router.replace(`${activity ? "/me/activity" : "/community"}?${params}`, {
      scroll: false,
    });
  };
  let posts = life.state.posts.filter(
    (item) =>
      !item.hidden &&
      item.category === category &&
      `${item.title} ${item.body}`.toLowerCase().includes(query.toLowerCase()),
  );
  if (activity)
    posts = posts.filter((item) =>
      activityTab === "bookmarks"
        ? item.bookmarks.includes(life.actor.id)
        : activityTab === "comments"
          ? life.state!.comments.some(
              (comment) =>
                comment.postId === item.id &&
                comment.authorId === life.actor.id,
            )
          : item.authorId === life.actor.id,
    );
  posts.sort((a, b) =>
    sort === "popular"
      ? b.likes.length - a.likes.length
      : b.createdAt.localeCompare(a.createdAt),
  );
  return (
    <>
      <LifeHeading
        label={activity ? "MY ACTIVITY" : "COMMUNITY"}
        title={activity ? "나의 커뮤니티 활동" : "함께 나누는 이야기"}
        body="반려생활의 발견부터 소중한 기억까지, 서로의 이야기를 천천히 나누어요."
      >
        <Link
          className="button primary"
          href={`/community/new?category=${category}`}
        >
          <Icon name="edit" size={16} />글 쓰기
        </Link>
      </LifeHeading>
      <div className={s.communityBanner}>
        <Icon name="users" size={29} />
        <div>
          <strong>함께라서 조금 더 따뜻한 일상</strong>
          <p>나누고 싶은 질문과 경험을 들려주세요.</p>
        </div>
        <Link
          href={activity ? "/community" : "/me/activity"}
          className="text-link"
        >
          {activity ? "커뮤니티로" : "내가 쓴 글 · 댓글 · 북마크"}
          <Icon name="arrow" size={16} />
        </Link>
      </div>
      {activity && (
        <div className={s.tabs}>
          {[
            ["posts", "내가 쓴 글"],
            ["comments", "내 댓글"],
            ["bookmarks", "북마크"],
          ].map(([key, label]) => (
            <button
              key={key}
              className={activityTab === key ? s.selected : ""}
              onClick={() => setActivityTab(key)}
            >
              {label}
            </button>
          ))}
        </div>
      )}
      <nav className={s.categories} aria-label="커뮤니티 카테고리">
        {Object.entries(categories).map(([key, label]) => (
          <button
            key={key}
            className={category === key ? s.selected : ""}
            aria-pressed={category === key}
            onClick={() => update("category", key)}
          >
            {label}
          </button>
        ))}
      </nav>
      <div className={s.toolbar}>
        <form
          onSubmit={(event) => {
            event.preventDefault();
            update(
              "q",
              String(new FormData(event.currentTarget).get("q") ?? ""),
            );
          }}
          className={s.search}
        >
          <Icon name="search" size={18} />
          <input
            key={query}
            name="q"
            defaultValue={query}
            placeholder="제목이나 내용을 검색해 보세요"
            aria-label="게시글 검색"
          />
          <button type="submit">검색</button>
        </form>
        <label className="visually-hidden" htmlFor="post-sort">
          정렬
        </label>
        <select
          id="post-sort"
          value={sort}
          onChange={(event) => update("sort", event.target.value)}
        >
          <option value="latest">최신순</option>
          <option value="popular">인기순</option>
        </select>
      </div>
      {["FUNERAL", "MEMORIAL"].includes(category) && (
        <DemoNote>
          개인 경험을 나누는 공간입니다. 장례·건강 관련 전문적인 판단은 해당
          전문가에게 확인해 주세요.
        </DemoNote>
      )}
      <div className={s.postList}>
        {posts.map((post) => (
          <Link
            href={`/community/${post.id}`}
            key={post.id}
            className={s.postCard}
          >
            {post.photoUrl && (
              <Photo
                src={post.photoUrl}
                alt={post.title}
                className={s.postPhoto}
              />
            )}
            <div>
              <span className="badge">{categories[post.category]}</span>
              <h2>{post.title}</h2>
              <p>{post.body}</p>
              <footer>
                <span>
                  {post.author} ·{" "}
                  {new Date(post.createdAt).toLocaleDateString("ko-KR")}
                </span>
                <span>
                  <Icon name="heart" size={16} />
                  {post.likes.length}
                  <Icon name="comment" size={16} />
                  {
                    life.state!.comments.filter(
                      (comment) => comment.postId === post.id,
                    ).length
                  }
                </span>
              </footer>
            </div>
          </Link>
        ))}
      </div>
      {!posts.length && (
        <LifeEmpty
          title="아직 이야기가 없어요"
          body="첫 번째 이야기를 나누어 주세요."
          href={`/community/new?category=${category}`}
          action="글 쓰기"
        />
      )}
      <DemoNote>
        게시글과 댓글은 이 브라우저에서만 공유되는 데모 데이터입니다. 실제
        사용자에게 공개되지 않아요.
      </DemoNote>
    </>
  );
}
function PostEditor({ post }: { post?: Post }) {
  const search = useSearchParams();
  const requestedCategory = search.get("category");
  const initialCategory =
    post?.category ??
    (requestedCategory && Object.hasOwn(categories, requestedCategory)
      ? (requestedCategory as Category)
      : "LIFE");
  const life = useLife();
  const router = useRouter();
  const [photo, setPhoto] = useState(post?.photoUrl ?? "");
  if (post && post.authorId !== life.actor.id)
    return (
      <LifeEmpty
        title="작성자만 수정할 수 있어요"
        body="다른 보호자의 이야기를 편집할 수 없습니다."
        href="/community"
        action="돌아가기"
      />
    );
  async function save(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const data = new FormData(form);
    const value = await life.run(
      () =>
        life.app.dependencies.life.savePost(
          life.actor,
          {
            title: String(data.get("title")),
            body: String(data.get("body")),
            category: String(data.get("category")) as Category,
            photoUrl: photo,
          },
          post?.id,
        ),
      "이야기를 저장했어요.",
    );
    if (value) {
      if (!post) {
        form.reset();
        setPhoto("");
      }
      router.push(`/community/${value.id}`);
    }
  }
  return (
    <>
      <Link
        className="text-link"
        href={`/community?category=${initialCategory}`}
      >
        ← 커뮤니티
      </Link>
      <LifeHeading
        label="SHARE YOUR STORY"
        title={post ? "이야기 수정하기" : "새로운 이야기"}
        body="서로를 배려하는 마음으로 경험을 나누어 주세요."
      />
      <form className={s.editor} onSubmit={save}>
        <div className="form-stack">
          <label>
            카테고리
            <select name="category" defaultValue={initialCategory}>
              {Object.entries(categories).map(([key, label]) => (
                <option key={key} value={key}>
                  {label}
                </option>
              ))}
            </select>
          </label>
          <label>
            제목
            <input
              name="title"
              required
              maxLength={100}
              defaultValue={post?.title}
              placeholder="어떤 이야기를 나누고 싶나요?"
            />
          </label>
          <label>
            내용
            <textarea
              name="body"
              required
              maxLength={5000}
              rows={10}
              defaultValue={post?.body}
              placeholder="반려동물과 함께한 경험을 들려주세요."
            />
          </label>
          <LifeStatus error={life.error} />
          <button className="button primary" disabled={life.busy}>
            {life.busy ? "저장 중…" : "이야기 저장하기"}
          </button>
        </div>
        <PhotoPicker value={photo} onChange={setPhoto} />
      </form>
    </>
  );
}
function PostDetail({ post }: { post: Post }) {
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
