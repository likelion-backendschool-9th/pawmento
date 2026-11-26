"use client";
import { useCallback, useEffect, useState } from "react";
import { useApp } from "@/app/AppProvider";
import type { PostApi } from "@/features/community/api/postApi";
import { selectCommunitySource } from "@/features/community/source";
import type { PostCategory, PostSummary } from "@/features/community/types/post";
import { appendPosts, pickCategory } from "@/features/community/utils/postList";

// 로그인한 계정을 보고 서버 글과 브라우저 데모 중 무엇을 쓸지 고른다(#104-S9).
export function useCommunitySource() {
  const { account, dependencies } = useApp();
  return selectCommunitySource({
    account,
    server: dependencies.posts,
    demo: dependencies.life,
  });
}

function message(cause: unknown, fallback: string) {
  return cause instanceof Error ? cause.message : fallback;
}

type Page = {
  category: string;
  posts: PostSummary[];
  nextCursor: string | null;
};

// 서버 카테고리 탭과 최신순 목록, "더 보기"(#104-S2, S3).
export function usePostList(api: PostApi, requestedCategory: string | null) {
  const [categories, setCategories] = useState<PostCategory[]>();
  const [categoryError, setCategoryError] = useState("");
  const [categoryAttempt, setCategoryAttempt] = useState(0);
  const [page, setPage] = useState<Page>();
  const [pageError, setPageError] = useState<{ category: string; message: string }>();
  const [pageAttempt, setPageAttempt] = useState(0);
  const [loadingMore, setLoadingMore] = useState(false);
  const [moreError, setMoreError] = useState<{ category: string; message: string }>();
  const category = categories && pickCategory(categories, requestedCategory);

  useEffect(() => {
    let cancelled = false;
    api
      .categories()
      .then((value) => {
        if (cancelled) return;
        setCategories(value);
        setCategoryError("");
      })
      .catch((cause) => {
        if (!cancelled) setCategoryError(message(cause, "카테고리를 불러오지 못했어요."));
      });
    return () => {
      cancelled = true;
    };
  }, [api, categoryAttempt]);

  useEffect(() => {
    if (!category) return;
    let cancelled = false;
    api
      .list({ category })
      .then((value) => {
        if (cancelled) return;
        setPage({ category, ...value });
        setPageError(undefined);
      })
      .catch((cause) => {
        if (!cancelled)
          setPageError({ category, message: message(cause, "글을 불러오지 못했어요.") });
      });
    return () => {
      cancelled = true;
    };
  }, [api, category, pageAttempt]);

  // 카테고리를 바꾸면 처음부터 다시 받는다. 받기 전에는 이전 카테고리의 글과 오류를 보이지 않는다.
  const current = page && page.category === category ? page : undefined;
  const currentError = pageError && pageError.category === category ? pageError.message : "";
  const currentMoreError = moreError && moreError.category === category ? moreError.message : "";

  const loadMore = useCallback(async () => {
    if (!current?.nextCursor || loadingMore) return;
    setLoadingMore(true);
    setMoreError(undefined);
    try {
      const next = await api.list({ category: current.category, cursor: current.nextCursor });
      // 그사이 카테고리를 바꿨다 돌아와 목록을 새로 받았으면 이어 붙이지 않는다.
      setPage((latest) =>
        latest === current
          ? {
              category: current.category,
              posts: appendPosts(current.posts, next.posts),
              nextCursor: next.nextCursor,
            }
          : latest,
      );
    } catch (cause) {
      setMoreError({
        category: current.category,
        message: message(cause, "글을 더 불러오지 못했어요."),
      });
    } finally {
      setLoadingMore(false);
    }
  }, [api, current, loadingMore]);

  const retry = useCallback(() => {
    if (categoryError) {
      setCategoryError("");
      setCategoryAttempt((value) => value + 1);
    } else {
      setPageError(undefined);
      setPageAttempt((value) => value + 1);
    }
  }, [categoryError]);

  return {
    categories,
    category,
    posts: current?.posts,
    nextCursor: current?.nextCursor ?? null,
    error: categoryError || currentError,
    loadingMore,
    moreError: currentMoreError,
    loadMore,
    retry,
  };
}
