"use client";
import { useCallback, useEffect, useState, useSyncExternalStore } from "react";
import { useRouter } from "next/navigation";
import { useApp } from "@/app/AppProvider";
import { ApplicationError } from "@/lib/application/ApplicationError";
import type { PostApi } from "@/features/community/api/postApi";
import { selectCommunitySource } from "@/features/community/source";
import type {
  PostCategory,
  PostDetail,
  PostSummary,
} from "@/features/community/types/post";
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

export function isLoginRequired(cause: unknown) {
  return cause instanceof ApplicationError && cause.code === "UNAUTHORIZED";
}

function isNotFound(cause: unknown) {
  return cause instanceof ApplicationError && cause.code === "NOT_FOUND";
}

// 쿠키가 만료돼 401을 받으면 로그인 화면으로 보내고, 로그인 뒤 지금 화면으로 돌아오게 한다.
// 주소는 부를 때 읽는다. 주소를 의존값으로 두면 탭을 바꿀 때마다 카테고리를 다시 받는다.
export function useLoginRedirect() {
  const router = useRouter();
  return useCallback(() => {
    const here = window.location.pathname + window.location.search;
    router.replace(`/sign-in?returnTo=${encodeURIComponent(here)}`);
  }, [router]);
}

// 저장·삭제한 글을 화면끼리 나눈다. Next는 지나온 화면을 숨겨 두었다가 다시 보이므로,
// 알리지 않으면 돌아간 목록과 상세에 고치기 전 글이나 지운 글이 남는다.
// 값이 null이면 지운 글이다. 로그아웃 뒤 다른 계정으로 들어오면 남지 않게 계정별로 둔다.
const changedPosts = new Map<string, PostDetail | null>();
let revision = 0;
const listeners = new Set<() => void>();

function changedKey(accountId: string | undefined, postId: string) {
  return `${accountId ?? ""}:${postId}`;
}

function publish(key: string, post: PostDetail | null | undefined) {
  if (post !== undefined) changedPosts.set(key, post);
  revision += 1;
  for (const listener of listeners) listener();
}

function subscribe(listener: () => void) {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

function usePostRevision() {
  return useSyncExternalStore(
    subscribe,
    () => revision,
    () => 0,
  );
}

// 서버 카테고리. 받지 못하면 하드코딩한 값으로 대신하지 않고 오류와 "다시 시도"를 보인다.
export function usePostCategories(api: PostApi) {
  const [categories, setCategories] = useState<PostCategory[]>();
  const [error, setError] = useState("");
  const [attempt, setAttempt] = useState(0);
  const toLogin = useLoginRedirect();
  useEffect(() => {
    let cancelled = false;
    api
      .categories()
      .then((value) => {
        if (cancelled) return;
        setCategories(value);
        setError("");
      })
      .catch((cause) => {
        if (cancelled) return;
        if (isLoginRequired(cause)) toLogin();
        else setError(message(cause, "카테고리를 불러오지 못했어요."));
      });
    return () => {
      cancelled = true;
    };
  }, [api, attempt, toLogin]);
  const retry = useCallback(() => {
    setError("");
    setAttempt((value) => value + 1);
  }, []);
  return { categories, error, retry };
}

type Page = {
  category: string;
  posts: PostSummary[];
  nextCursor: string | null;
};

// 서버 카테고리 탭과 최신순 목록, "더 보기"(#104-S2, S3).
export function usePostList(api: PostApi, requestedCategory: string | null) {
  const {
    categories,
    error: categoryError,
    retry: retryCategories,
  } = usePostCategories(api);
  const [page, setPage] = useState<Page>();
  const [pageError, setPageError] = useState<{ category: string; message: string }>();
  const [pageAttempt, setPageAttempt] = useState(0);
  const [loadingMore, setLoadingMore] = useState(false);
  const [moreError, setMoreError] = useState<{ category: string; message: string }>();
  const category = categories && pickCategory(categories, requestedCategory);
  // 다른 화면에서 글을 쓰거나 고치거나 지우면 처음 페이지부터 다시 받는다.
  const changes = usePostRevision();
  const toLogin = useLoginRedirect();

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
        if (cancelled) return;
        if (isLoginRequired(cause)) toLogin();
        else setPageError({ category, message: message(cause, "글을 불러오지 못했어요.") });
      });
    return () => {
      cancelled = true;
    };
  }, [api, category, pageAttempt, changes, toLogin]);

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
      if (isLoginRequired(cause)) return toLogin();
      setMoreError({
        category: current.category,
        message: message(cause, "글을 더 불러오지 못했어요."),
      });
    } finally {
      setLoadingMore(false);
    }
  }, [api, current, loadingMore, toLogin]);

  const retry = useCallback(() => {
    if (categoryError) {
      retryCategories();
    } else {
      setPageError(undefined);
      setPageAttempt((value) => value + 1);
    }
  }, [categoryError, retryCategories]);

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

type PostResult =
  | { postId: string; kind: "found"; post: PostDetail }
  | { postId: string; kind: "missing" }
  | { postId: string; kind: "error"; message: string };

// 상세와 수정 폼이 쓰는 글 하나(#104-S4, S7). 404면 "missing"이다.
// 이 탭에서 고치거나 지운 글은 서버에 다시 묻지 않고 그 결과를 쓴다.
export function usePost(api: PostApi, postId: string) {
  const key = changedKey(useApp().account?.id, postId);
  const [result, setResult] = useState<PostResult>();
  const [attempt, setAttempt] = useState(0);
  usePostRevision();
  const toLogin = useLoginRedirect();
  useEffect(() => {
    // 이 탭에서 방금 고친 글은 PUT 응답을 그대로 쓰고 GET을 다시 부르지 않는다.
    if (changedPosts.has(key)) return;
    let cancelled = false;
    api
      .get(postId)
      .then((post) => {
        if (!cancelled) setResult({ postId, kind: "found", post });
      })
      .catch((cause) => {
        if (cancelled) return;
        if (isLoginRequired(cause)) toLogin();
        else if (isNotFound(cause)) setResult({ postId, kind: "missing" });
        else
          setResult({
            postId,
            kind: "error",
            message: message(cause, "글을 불러오지 못했어요."),
          });
      });
    return () => {
      cancelled = true;
    };
  }, [api, postId, key, attempt, toLogin]);
  const retry = useCallback(() => {
    setResult(undefined);
    setAttempt((value) => value + 1);
  }, []);
  const current = result?.postId === postId ? result : undefined;
  const changed = changedPosts.get(key);
  const post =
    changed === null
      ? undefined
      : (changed ?? (current?.kind === "found" ? current.post : undefined));
  const status: "loading" | "found" | "missing" | "error" =
    changed === null || current?.kind === "missing"
      ? "missing"
      : post
        ? "found"
        : current?.kind === "error"
          ? "error"
          : "loading";
  return {
    status,
    post,
    error: current?.kind === "error" ? current.message : "",
    retry,
  };
}

// 글쓰기·수정·삭제. 성공하면 다른 화면에 알려 목록과 상세를 맞춘다.
export function usePostActions(api: PostApi) {
  const accountId = useApp().account?.id;
  const create = useCallback(
    async (input: Parameters<PostApi["create"]>[0]) => {
      const postId = await api.create(input);
      publish(changedKey(accountId, postId), undefined);
      return postId;
    },
    [api, accountId],
  );
  const update = useCallback(
    async (postId: string, input: Parameters<PostApi["update"]>[1]) => {
      const post = await api.update(postId, input);
      publish(changedKey(accountId, postId), post);
      return post;
    },
    [api, accountId],
  );
  const remove = useCallback(
    async (postId: string) => {
      await api.remove(postId);
      publish(changedKey(accountId, postId), null);
    },
    [api, accountId],
  );
  return { create, update, remove };
}
