"use client";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useState } from "react";
import { PhotoPicker } from "@/components/PhotoPicker";
import { useApp } from "@/app/AppProvider";
import { categories } from "@/features/community/utils/categories";
import { pickCategory } from "@/features/community/utils/postList";
import type { Post, Category } from "@/features/community/types/community";
import type { PostApi } from "@/features/community/api/postApi";
import type { PostDetail } from "@/features/community/types/post";
import {
  isLoginRequired,
  useLoginRedirect,
  usePost,
  usePostActions,
  usePostCategories,
} from "@/features/community/hooks/usePosts";
import { PostNotFound } from "@/features/community/components/PostDetail";
import { useLife } from "@/features/shell/hooks/useLife";
import {
  LifeHeading,
  LifeStatus,
  LifeEmpty,
  lifeStyles as s,
} from "@/features/shell/components/LifeUi";
export function PostEditor({ post }: { post?: Post }) {
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

// 가입·로그인한 회원의 글쓰기·수정(#104-S6, S7, S8). postId가 있으면 그 글을 고친다.
// 사진은 서버 API가 없어 고르지 않는다(#104-S10).
export function ServerPostEditor({
  posts: api,
  postId,
}: {
  posts: PostApi;
  postId?: string;
}) {
  const app = useApp();
  const categories = usePostCategories(api);
  if (categories.error)
    return (
      <div className="form-stack">
        <LifeStatus error={categories.error} />
        <button className="button secondary" onClick={categories.retry}>
          다시 시도
        </button>
      </div>
    );
  if (!categories.categories) return <LifeStatus loading />;
  if (!postId)
    return <ServerPostForm posts={api} categories={categories.categories} />;
  return (
    <ServerPostEditForm
      posts={api}
      postId={postId}
      categories={categories.categories}
      accountId={app.account?.id}
    />
  );
}

function ServerPostEditForm({
  posts: api,
  postId,
  categories,
  accountId,
}: {
  posts: PostApi;
  postId: string;
  categories: { code: string; name: string }[];
  accountId: string | undefined;
}) {
  const loaded = usePost(api, postId);
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
  if (!loaded.post) return <LifeStatus loading />;
  if (loaded.post.authorId !== accountId)
    return (
      <LifeEmpty
        title="작성자만 수정할 수 있어요"
        body="다른 보호자의 이야기를 편집할 수 없습니다."
        href="/community"
        action="돌아가기"
      />
    );
  return (
    <ServerPostForm
      key={loaded.post.id}
      posts={api}
      categories={categories}
      post={loaded.post}
    />
  );
}

function ServerPostForm({
  posts: api,
  categories,
  post,
}: {
  posts: PostApi;
  categories: { code: string; name: string }[];
  post?: PostDetail;
}) {
  const app = useApp();
  const router = useRouter();
  const search = useSearchParams();
  const toLogin = useLoginRedirect();
  const actions = usePostActions(api);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const initialCategory =
    post?.category ?? pickCategory(categories, search.get("category"));
  // 실패하면 폼을 그대로 두어 입력한 제목·본문·카테고리를 지우지 않는다(#104-S8).
  async function save(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy) return;
    const form = event.currentTarget;
    const data = new FormData(form);
    const input = {
      category: String(data.get("category")),
      title: String(data.get("title")),
      body: String(data.get("body")),
    };
    setBusy(true);
    setError("");
    try {
      const savedId = post
        ? (await actions.update(post.id, input)).id
        : await actions.create(input);
      // 숨겨 둔 글쓰기 화면에 돌아왔을 때 지난 입력이 남지 않게 한다.
      if (!post) form.reset();
      app.notify("이야기를 저장했어요.");
      router.push(`/community/${savedId}`);
    } catch (cause) {
      if (isLoginRequired(cause)) return toLogin();
      setError(cause instanceof Error ? cause.message : "저장에 실패했어요.");
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <Link
        className="text-link"
        href={`/community?category=${encodeURIComponent(initialCategory ?? "")}`}
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
              {categories.map((item) => (
                <option key={item.code} value={item.code}>
                  {item.name}
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
          <LifeStatus error={error} />
          <button className="button primary" disabled={busy}>
            {busy ? "저장 중…" : "이야기 저장하기"}
          </button>
        </div>
      </form>
    </>
  );
}
