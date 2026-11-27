import type { MapBounds } from "../types/MapBounds.ts";
import type { PlaceCategory, PlaceList, Place } from "../types/Place.ts";

// 가까운 순 목록의 조회 흐름(#226-S5~S9). 지도 화면이 움직일 때마다 하는 영역 조회(#172-S16)와 따로, 기준점을 가운데로 한 영역을 조회한다.
// 화면 틀과 상관없이 돌도록 React 없이 만들었다.
// - 조건(영역·종류)이 같으면 다시 조회하지 않는다. 지도를 움직이거나 장소를 눌러 지도가 옮겨 가도 목록은 그대로다.
// - 조건이 바뀌면 이전 요청의 응답은 버린다.
// - 실패하면 예시 결과로 바꾸지 않고 오류를 알리며, retry()로 같은 조건을 다시 부른다.

export type NearbyQuery = { bounds: MapBounds; categories: readonly PlaceCategory[] };

export type NearbyListState = {
  // idle: 조회할 기준이 없음, loading: 조회 중, done: 결과를 받음, error: 실패
  status: "idle" | "loading" | "done" | "error";
  places: Place[];
  // 영역 안의 장소가 서버가 한 번에 주는 최대 개수(500)를 넘어 잘렸다
  truncated: boolean;
  dataAsOf: string | null;
  // 실제 데이터가 아닌 개발·테스트용 예시 장소다
  sample: boolean;
  // error일 때만 있는 안내 문구
  message: string;
};

const IDLE: NearbyListState = { status: "idle", places: [], truncated: false, dataAsOf: null, sample: false, message: "" };
const DEFAULT_ERROR = "장소를 불러오지 못했어요.";

export class NearbyListController {
  private state: NearbyListState = IDLE;
  private query: NearbyQuery | null = null;
  private key: string | null = null;
  private current = 0;
  private readonly listeners = new Set<() => void>();

  private readonly list: (bounds: MapBounds, query: { categories: readonly PlaceCategory[] }) => Promise<PlaceList>;

  // 테스트를 돌리는 Node의 변환 방식은 생성자 매개변수 속성(private readonly …)을 지원하지 않아 필드를 따로 둔다
  constructor(list: (bounds: MapBounds, query: { categories: readonly PlaceCategory[] }) => Promise<PlaceList>) {
    this.list = list;
  }

  getState = (): NearbyListState => this.state;

  subscribe = (listener: () => void): (() => void) => {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  };

  // 조회할 조건을 알린다. null이면 기준이 없는 것이라 조회를 멈추고 처음 상태로 돌아간다
  load = (query: NearbyQuery | null): void => {
    const key = query === null ? null : JSON.stringify(query);
    if (key === this.key) return;
    this.key = key;
    this.current += 1;
    this.query = query;
    if (query === null) {
      this.set(IDLE);
      return;
    }
    void this.run();
  };

  // 실패한 조회를 바로 다시 부른다. 실패 상태가 아니면 아무것도 하지 않는다
  retry = (): void => {
    if (this.state.status !== "error" || this.query === null) return;
    void this.run();
  };

  // 화면이 사라질 때 부른다. 요청 중인 조회의 응답은 버리고, 같은 조건으로 다시 load하면 다시 조회한다
  dispose = (): void => {
    this.current += 1;
    this.key = null;
    if (this.state.status === "loading") this.set(IDLE);
  };

  private async run(): Promise<void> {
    const query = this.query;
    if (query === null) return;
    const request = ++this.current;
    this.set({ ...IDLE, status: "loading" });
    try {
      const result = await this.list(query.bounds, { categories: query.categories });
      if (request !== this.current) return;
      this.set({
        status: "done",
        places: result.places,
        truncated: result.truncated,
        dataAsOf: result.dataAsOf,
        sample: result.sample,
        message: "",
      });
    } catch (cause) {
      if (request !== this.current) return;
      const message = cause instanceof Error && cause.message.trim() !== "" ? cause.message : DEFAULT_ERROR;
      this.set({ ...IDLE, status: "error", message });
    }
  }

  private set(next: NearbyListState) {
    this.state = next;
    this.listeners.forEach((listener) => listener());
  }
}
