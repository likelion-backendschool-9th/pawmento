"use client";
import { useCallback, useEffect, useState } from "react";
import { useApp } from "@/app/AppProvider";
import { createRecentSearches } from "../utils/recentSearches.ts";

// 저장소를 모듈에 하나만 둔다. 저장소(localStorage)를 쓸 수 없을 때 "이번 방문 동안만" 기억하는 값은 이 객체 안에 있으므로,
// 검색 화면을 닫았다 다시 열어도(컴포넌트가 사라져도) 남도록 화면보다 오래 사는 곳에 둔다(#195-S14).
// 저장소는 브라우저에서만 쓸 수 있어 쓸 때마다 가져온다(서버 렌더링에서는 없는 값).
const recentSearches = createRecentSearches(() => (typeof window === "undefined" ? null : window.localStorage));

// 최근 검색어(#195-S14). 계정마다 이 기기의 브라우저에만 남긴다. 로그인 전에는 계정이 없어 이번 방문 동안만 기억한다.
export function useRecentSearches() {
  const { account } = useApp();
  const owner = account?.id ?? "";
  const store = recentSearches;
  const [items, setItems] = useState<string[]>([]);
  // 브라우저 저장소는 화면이 뜬 뒤에만 읽을 수 있어, 한 박자 뒤에 읽는다. 계정이 바뀌면 그 계정의 목록으로 바꾼다.
  useEffect(() => {
    const timer = setTimeout(() => setItems(store.load(owner)), 0);
    return () => clearTimeout(timer);
  }, [store, owner]);
  const add = useCallback((text: string) => setItems(store.add(owner, text)), [store, owner]);
  const remove = useCallback((text: string) => setItems(store.remove(owner, text)), [store, owner]);
  const clear = useCallback(() => setItems(store.clear(owner)), [store, owner]);
  return { items, add, remove, clear };
}
