import type { PlaceSearchQuery, PlaceSearchResult } from "../types/Place.ts";
import { checkSearchText } from "./placeSearchText.ts";

// 검색창의 입력을 검색 요청으로 바꾸는 흐름(#195-S10, S13). 화면 틀과 상관없이 돌도록 React 없이 만들었다.
// - 입력이 멈추고 250ms 뒤에 검색한다(입력 중에는 부르지 않는다).
// - 이전 요청의 응답은 버린다(느린 응답이 나중에 도착해도 지금 입력의 결과를 덮지 않는다).
// - 검색어가 두 글자 미만(정규화한 뒤)이면 검색하지 않고 처음 상태로 돌아간다.
// - 실패하면 예시 결과로 바꾸지 않고 오류를 그대로 알리며, retry()로 같은 검색어를 다시 부른다.
export const SEARCH_DELAY_MS = 250;

export type SearchContext = Pick<PlaceSearchQuery, "categories" | "center">;

export type PlaceSearchState = {
  // idle: 검색할 수 없는 입력(비었거나 두 글자 미만), loading: 입력이 멈추기를 기다리거나 요청 중, done: 결과를 받음, error: 실패
  status: "idle" | "loading" | "done" | "error";
  // 지금 입력한 글자(그대로)
  text: string;
  // 마지막으로 받은 결과. loading 동안에는 이전 결과를 남겨 화면이 깜빡이지 않게 한다. idle과 error에서는 null이다
  result: PlaceSearchResult | null;
  // error일 때만 있는 안내 문구. 검색어는 담지 않는다
  message: string;
};

const INITIAL: PlaceSearchState = { status: "idle", text: "", result: null, message: "" };

const DEFAULT_ERROR = "검색하지 못했어요. 잠시 뒤에 다시 시도해 주세요.";

function messageOf(cause: unknown): string {
  return cause instanceof Error && cause.message.trim() !== "" ? cause.message : DEFAULT_ERROR;
}

export class PlaceSearchController {
  private state: PlaceSearchState = INITIAL;
  private context: SearchContext = {};
  private timer: ReturnType<typeof setTimeout> | null = null;
  private inFlight = false;
  private current = 0;
  private readonly listeners = new Set<() => void>();

  private readonly search: (query: PlaceSearchQuery) => Promise<PlaceSearchResult>;
  private readonly delayMs: number;

  // 테스트를 돌리는 Node의 변환 방식은 생성자 매개변수 속성(private readonly …)을 지원하지 않아 필드를 따로 둔다
  constructor(
    search: (query: PlaceSearchQuery) => Promise<PlaceSearchResult>,
    delayMs: number = SEARCH_DELAY_MS,
  ) {
    this.search = search;
    this.delayMs = delayMs;
  }

  getState = (): PlaceSearchState => this.state;

  subscribe = (listener: () => void): (() => void) => {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  };

  // 입력이 바뀔 때마다 부른다. 지도 중심·종류가 바뀌어도 같은 검색어로 다시 부르면 그 값으로 다시 찾는다.
  // 같은 입력이어도 기다리거나 요청 중인 검색이 실제로 없으면(dispose로 취소된 뒤 등) 다시 검색한다.
  setInput = (text: string, context: SearchContext = {}): void => {
    const sameInput =
      text === this.state.text &&
      JSON.stringify(context) === JSON.stringify(this.context) &&
      (this.state.status === "done" || (this.state.status === "loading" && this.pending));
    if (sameInput) return;
    this.context = context;
    this.cancel();
    if (!checkSearchText(text).ok) {
      this.set({ status: "idle", text, result: null, message: "" });
      return;
    }
    this.set({ status: "loading", text, result: this.state.result, message: "" });
    this.schedule();
  };

  // 화면이 다시 보일 때 부른다. dispose로 검색이 취소돼 loading에 멈춰 있으면 같은 입력으로 다시 검색한다.
  resume = (): void => {
    if (this.state.status === "loading" && !this.pending) this.schedule();
  };

  // 실패한 검색을 기다리지 않고 바로 다시 부른다. 실패 상태가 아니면 아무것도 하지 않는다.
  retry = (): void => {
    if (this.state.status !== "error" || !checkSearchText(this.state.text).ok) return;
    this.cancel();
    this.set({ ...this.state, status: "loading", message: "" });
    void this.run();
  };

  // 기다리는 검색과 요청 중인 검색의 응답을 모두 버린다. 화면이 사라질 때 부른다. 그 뒤에 같은 입력으로 다시 쓸 수 있고
  // (setInput), 화면이 다시 보일 때 resume()을 부르면 취소된 검색을 이어서 한다.
  dispose = (): void => {
    this.cancel();
  };

  // 기다리는 타이머나 요청 중인 검색이 있다
  private get pending(): boolean {
    return this.timer !== null || this.inFlight;
  }

  private schedule() {
    this.timer = setTimeout(() => {
      this.timer = null;
      void this.run();
    }, this.delayMs);
  }

  private cancel() {
    if (this.timer !== null) clearTimeout(this.timer);
    this.timer = null;
    this.inFlight = false;
    this.current += 1;
  }

  private async run(): Promise<void> {
    const request = ++this.current;
    this.inFlight = true;
    const text = this.state.text;
    try {
      const result = await this.search({ q: text, ...this.context });
      if (request !== this.current) return;
      this.inFlight = false;
      this.set({ status: "done", text, result, message: "" });
    } catch (cause) {
      if (request !== this.current) return;
      this.inFlight = false;
      this.set({ status: "error", text, result: null, message: messageOf(cause) });
    }
  }

  private set(next: PlaceSearchState) {
    this.state = next;
    this.listeners.forEach((listener) => listener());
  }
}
