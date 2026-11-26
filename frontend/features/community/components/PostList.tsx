"use client";
import { RecordActionLink } from "@/components/RecordAction";
import Link from "next/link";
import { CollectionHeading } from "@/components/CollectionHeading";
import { useRouter, useSearchParams } from "next/navigation";
import { useState } from "react";
import { Photo } from "@/components/Photo";
import { Icon } from "@/components/Icon";
import { categories } from "@/features/community/utils/categories";
import type { Category } from "@/features/community/types/community";
import type { PostApi } from "@/features/community/api/postApi";
import { usePostList } from "@/features/community/hooks/usePosts";
import { useApp } from "@/app/AppProvider";
import type { useLife } from "@/features/shell/hooks/useLife";
import {
  LifeStatus,
  LifeEmpty,
  DemoNote,
  lifeStyles as s,
} from "@/features/shell/components/LifeUi";
export function PostList({
  life,
  activity = false,
}: {
  life: ReturnType<typeof useLife>;
  activity?: boolean;
}) {
  const router = useRouter();
  const search = useSearchParams();
  const [activityTab, setActivityTab] = useState("posts");
  if (!life.state) return <LifeStatus error={life.error} loading />;
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
      <CollectionHeading
        title={activity ? "내 커뮤니티 활동" : "커뮤니티"}
        body={<>반려생활의 발견부터 소중한 기억까지,<br />서로의 이야기를 나누어요.</>}
      >
        <RecordActionLink href={`/community/new?category=${category}`}>글 쓰기</RecordActionLink>
      </CollectionHeading>
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
          actionIcon="edit"
        />
      )}
      <DemoNote>
        게시글과 댓글은 이 브라우저에서만 공유되는 데모 데이터입니다. 실제
        사용자에게 공개되지 않아요.
      </DemoNote>
    </>
  );
}

// 가입·로그인한 회원의 목록. 서버 카테고리 탭과 최신순 글, "더 보기"(#104-S2, S3).
// 검색·정렬·좋아요·댓글 수는 서버 API가 없어 그리지 않는다(#104-S10).
export function ServerPostList({ posts: api }: { posts: PostApi }) {
  const router = useRouter();
  const search = useSearchParams();
  const { account } = useApp();
  const list = usePostList(api, search.get("category"));
  const category = list.category;
  const writeHref = category
    ? `/community/new?category=${category}`
    : "/community/new";
  return (
    <>
      <CollectionHeading
        title="커뮤니티"
        body={<>반려생활의 발견부터 소중한 기억까지,<br />서로의 이야기를 나누어요.</>}
      >
        <RecordActionLink href={writeHref}>글 쓰기</RecordActionLink>
      </CollectionHeading>
      <div className={s.communityBanner}>
        <Icon name="users" size={29} />
        <div>
          <strong>함께라서 조금 더 따뜻한 일상</strong>
          <p>나누고 싶은 질문과 경험을 들려주세요.</p>
        </div>
      </div>
      {list.categories && (
        <nav className={s.categories} aria-label="커뮤니티 카테고리">
          {list.categories.map((item) => (
            <button
              key={item.code}
              className={category === item.code ? s.selected : ""}
              aria-pressed={category === item.code}
              onClick={() =>
                router.replace(`/community?category=${encodeURIComponent(item.code)}`, {
                  scroll: false,
                })
              }
            >
              {item.name}
            </button>
          ))}
        </nav>
      )}
      {category && ["FUNERAL", "MEMORIAL"].includes(category) && (
        <DemoNote>
          개인 경험을 나누는 공간입니다. 장례·건강 관련 전문적인 판단은 해당
          전문가에게 확인해 주세요.
        </DemoNote>
      )}
      {list.error ? (
        <div className="form-stack">
          <LifeStatus error={list.error} />
          <button className="button secondary" onClick={list.retry}>
            다시 시도
          </button>
        </div>
      ) : !list.posts ? (
        <LifeStatus loading />
      ) : list.posts.length ? (
        <>
          <div className={s.postList}>
            {list.posts.map((post) => (
              <Link
                href={`/community/${post.id}`}
                key={post.id}
                className={s.postCard}
              >
                <div>
                  <span className="badge">{post.categoryName}</span>
                  <h2>{post.title}</h2>
                  <p>{post.preview}</p>
                  <footer>
                    <span>
                      {post.authorId === account?.id ? "나" : "보호자"} ·{" "}
                      {new Date(post.createdAt).toLocaleDateString("ko-KR")}
                    </span>
                  </footer>
                </div>
              </Link>
            ))}
          </div>
          <LifeStatus error={list.moreError} />
          {list.nextCursor && (
            <button
              className="button secondary"
              disabled={list.loadingMore}
              onClick={() => void list.loadMore()}
            >
              {list.loadingMore ? "불러오는 중…" : "더 보기"}
            </button>
          )}
        </>
      ) : (
        <LifeEmpty
          title="아직 이야기가 없어요"
          body="첫 번째 이야기를 나누어 주세요."
          href={writeHref}
          action="글 쓰기"
          actionIcon="edit"
        />
      )}
    </>
  );
}
