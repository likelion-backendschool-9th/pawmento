"use client";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useState } from "react";
import { PhotoPicker } from "@/components/PhotoPicker";
import { categories } from "@/features/community/utils/categories";
import type { Post, Category } from "@/features/community/types/community";
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
