"use client";
import { useEffect, useMemo, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { Icon } from "@/components/Icon";
import type { MapPoint } from "@/features/map/types/MapBounds";
import { usePlaceSearch } from "../hooks/usePlaceSearch";
import { useRecentSearches } from "../hooks/useRecentSearches";
import { distanceLabel } from "../utils/placeDistance";
import { open24hLabel } from "../utils/placeCard";
import { PLACE_STYLE, placeMarkup } from "../utils/placeMarkup";
import { highlightParts, SEARCH_SUGGESTIONS, searchView } from "../utils/placeSearchView";
import { SEARCH_TEXT_MESSAGES, checkSearchText } from "../utils/placeSearchText";
import styles from "./PlaceSearchPanel.module.css";

function Highlight({ text, query }: { text: string; query: string }) {
  return (
    <>
      {highlightParts(text, query).map((part, index) =>
        part.match ? <mark key={index}>{part.text}</mark> : <span key={index}>{part.text}</span>,
      )}
    </>
  );
}

// 정보 지도의 장소 검색 화면(#195-S9, S10, S12, S13, S14). 이름이나 주소를 입력하면 멈춘 뒤 검색하고, 입력이 없으면 최근 검색어를 보인다.
// 결과를 눌러 지도로 이동하는 부분(S11)은 다음 PR에서 연결한다. 지도 중심은 열 때 정해 거리 계산에만 쓴다.
export function PlaceSearchPanel({
  center,
  onClose,
}: {
  center: MapPoint | null;
  onClose: () => void;
}) {
  const { state, setInput, retry } = usePlaceSearch();
  const recent = useRecentSearches();
  const [text, setText] = useState("");
  const dialog = useRef<HTMLDialogElement>(null);
  const input = useRef<HTMLInputElement>(null);
  const body = useRef<HTMLDivElement>(null);
  // 한글 같은 조합 입력 중인지. 조합 중의 글자(강나)로는 검색하지 않고, 조합이 끝난 글자(강남)로 검색한다
  const composing = useRef(false);
  // 검색은 종류 칩과 상관없이 세 종류 모두에서 찾는다(칩을 모두 꺼도 검색은 되어야 한다)
  const context = useMemo(
    () => (center ? { center: { latitude: center.latitude, longitude: center.longitude } } : {}),
    [center],
  );

  // 브라우저의 모달 dialog(showModal)를 쓴다. 열려 있는 동안 Tab 이동이 안에서만 돌고 뒤 화면은 조작할 수 없다. Esc는 cancel 이벤트로 받는다.
  useEffect(() => {
    const element = dialog.current;
    const previous = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    if (element && !element.open) element.showModal();
    // showModal은 첫 번째 버튼(닫기)에 초점을 주므로, 입력 칸에 커서가 있도록 옮긴다(#195-S9)
    input.current?.focus();
    return () => {
      document.body.style.overflow = previous;
      if (element?.open) element.close();
    };
  }, []);

  function change(next: string) {
    setText(next);
    setInput(next, context);
  }

  // 입력 칸의 글자는 바로 바꾸되, 조합 중에는 검색 타이머를 시작하지 않는다
  function type(next: string) {
    setText(next);
    if (!composing.current) setInput(next, context);
  }

  // 최근 검색어나 제안을 눌러 그 검색어로 바로 검색한다(#195-S14)
  function pick(next: string) {
    change(next);
    recent.add(next);
    input.current?.focus();
  }

  const view = searchView(state, recent.items.length > 0);
  const places = state.result?.places ?? [];
  // 새 결과가 도착하거나 보이는 내용이 바뀌면 목록을 맨 위부터 보인다(이전 스크롤 위치가 남아 첫 결과가 가려지지 않게)
  useEffect(() => {
    body.current?.scrollTo({ top: 0 });
  }, [state.result, view]);
  const shown = state.text.trim();
  const problem = checkSearchText(state.text);
  const announcement =
    view === "results"
      ? `검색 결과 ${places.length}곳${state.result?.truncated ? " 이상" : ""}`
      : view === "empty"
        ? "맞는 장소가 없어요"
        : view === "error"
          ? "검색하지 못했어요"
          : view === "searching"
            ? "찾는 중이에요"
            : "";

  return createPortal(
    <dialog
      ref={dialog}
      className={styles.dialog}
      aria-label="장소 검색"
      onCancel={(event) => {
        // Esc: 닫는 일은 부모의 상태가 한다
        event.preventDefault();
        onClose();
      }}
    >
      <form
        className={styles.bar}
        role="search"
        onSubmit={(event) => {
          // 키보드의 검색을 누르면 최근 검색어에 남기고 키보드를 내려 결과가 보이게 한다(#195-S14)
          event.preventDefault();
          event.stopPropagation();
          recent.add(text);
          input.current?.blur();
        }}
      >
        <button type="button" className={styles.back} onClick={onClose} aria-label="검색 닫기">
          <Icon name="chevron" size={20} />
        </button>
        <label className={styles.field}>
          <span className="visually-hidden">병원·미용·보호센터 이름이나 주소</span>
          <Icon name="search" size={18} />
          <input
            ref={input}
            type="search"
            enterKeyHint="search"
            autoComplete="off"
            autoCapitalize="off"
            spellCheck={false}
            value={text}
            placeholder="병원·미용·보호센터 이름이나 주소"
            onChange={(event) => type(event.target.value)}
            onCompositionStart={() => {
              composing.current = true;
            }}
            onCompositionEnd={(event) => {
              // 조합이 끝난 글자로 검색한다. 브라우저에 따라 마지막 input 이벤트가 조합 끝보다 먼저일 수 있어 여기서 한 번 더 읽는다
              composing.current = false;
              change(event.currentTarget.value);
            }}
          />
          {text !== "" && (
            <button
              type="button"
              className={styles.clear}
              aria-label="입력 지우기"
              onClick={() => {
                change("");
                input.current?.focus();
              }}
            >
              <Icon name="close" size={16} />
            </button>
          )}
        </label>
      </form>

      <div ref={body} className={styles.body} aria-busy={state.status === "loading"}>
        <p className="visually-hidden" role="status">
          {announcement}
        </p>

        {view === "recent" && (
          <section className={styles.recent} aria-label="최근 검색어">
            <header>
              <h2>최근 검색어</h2>
              <button type="button" className={styles.link} onClick={recent.clear}>
                전체 삭제
              </button>
            </header>
            <ul>
              {recent.items.map((item) => (
                <li key={item}>
                  <button type="button" className={styles.recentItem} onClick={() => pick(item)}>
                    <Icon name="clock" size={17} />
                    <span>{item}</span>
                  </button>
                  <button
                    type="button"
                    className={styles.remove}
                    aria-label={`최근 검색어 ${item} 지우기`}
                    onClick={() => recent.remove(item)}
                  >
                    <Icon name="close" size={16} />
                  </button>
                </li>
              ))}
            </ul>
          </section>
        )}

        {view === "guide" && (
          <p className={styles.guide}>
            동물병원·동물미용·동물보호센터의 이름이나 주소를 두 글자 이상 입력해 보세요.
            <br />
            예: 서울숲, 강남
          </p>
        )}

        {view === "invalid" && !problem.ok && (
          <p className={styles.guide} role="alert">
            {SEARCH_TEXT_MESSAGES[problem.problem]}
          </p>
        )}

        {view === "searching" && <p className={styles.guide}>찾는 중이에요…</p>}

        {view === "results" && (
          <>
            <ul
              className={`${styles.results} ${state.status === "loading" ? styles.stale : ""}`}
              aria-label="검색 결과"
            >
              {places.map((place) => {
                const open24h = open24hLabel(place);
                return (
                  <li key={place.id} className={styles.result}>
                    <span
                      className={styles.kindIcon}
                      aria-hidden="true"
                      dangerouslySetInnerHTML={{ __html: placeMarkup(place.category, false, 28) }}
                    />
                    <span className={styles.text}>
                      <span className={styles.name}>
                        {/* 장소 이름은 한 덩어리로 묶는다. 묶지 않으면 강조 조각 사이에도 간격이 생겨 "서울동물병원"이 "서울 동물 병원"처럼 보인다 */}
                        <span>
                          <Highlight text={place.name} query={state.text} />
                        </span>
                        {open24h && <span className={styles.badge24}>{open24h}</span>}
                      </span>
                      <span className={styles.meta}>
                        {PLACE_STYLE[place.category].label} ·{" "}
                        <Highlight text={place.address ?? "주소 정보 없음"} query={state.text} />
                      </span>
                    </span>
                    {place.distanceMeters !== null && (
                      <span className={styles.distance}>{distanceLabel(place.distanceMeters)}</span>
                    )}
                  </li>
                );
              })}
            </ul>
            {state.result?.truncated && state.status !== "loading" && (
              <p className={styles.more}>더 있어요. 더 자세히 입력해 보세요</p>
            )}
          </>
        )}

        {view === "empty" && (
          <div className={styles.message}>
            <strong>‘{shown}’에 맞는 장소가 없어요</strong>
            <p>지금은 동물병원·동물미용·동물보호센터만 찾을 수 있어요. 이름이나 주소의 일부를 입력해 보세요.</p>
            <div className={styles.suggest}>
              {SEARCH_SUGGESTIONS.map((suggestion) => (
                <button key={suggestion} type="button" onClick={() => pick(suggestion)}>
                  {suggestion}
                </button>
              ))}
            </div>
          </div>
        )}

        {view === "error" && (
          <div className={styles.message} role="alert">
            <strong>검색하지 못했어요</strong>
            <p>{state.message}</p>
            <button type="button" className="button primary" onClick={retry}>
              다시 시도
            </button>
          </div>
        )}
      </div>
    </dialog>,
    document.body,
  );
}
