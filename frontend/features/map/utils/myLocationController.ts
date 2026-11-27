import type { MapPoint } from "../types/MapBounds.ts";

// "내 위치" 버튼의 흐름(#226-S1~S4, S10). 브라우저 위치 기능을 직접 쓰지 않고 source로 받아서 화면 틀과 상관없이 돌도록 React 없이 만들었다.
// - 처음 누르면 브라우저 권한 창보다 먼저 우리 안내를 보인다. 이미 허락했거나 안내를 본 적이 있으면 바로 읽는다.
// - 읽는 동안 다시 눌러도 겹쳐 읽지 않는다.
// - 거부·읽기 실패·지원 안 함은 이유와 함께 알리고, 기본 위치로 옮기지 않는다.
// - 위치는 이 흐름의 메모리에만 둔다. 저장소에는 쓰지 않는다(안내를 봤다는 사실만 introSeen으로 남긴다).

export type LocationFix = { point: MapPoint; accuracy: number };

// 브라우저가 알려 주는 위치 권한. 알 수 없으면 unknown이다(권한 조회를 지원하지 않는 브라우저)
export type LocationPermission = "granted" | "denied" | "prompt" | "unknown";

export type LocationReadResult =
  | { ok: true; fix: LocationFix }
  // denied: 권한이 꺼져 있음, unavailable: 위치를 알 수 없음, timeout: 시간 초과(10초)
  | { ok: false; reason: "denied" | "unavailable" | "timeout" };

export type LocationSource = {
  // 이 브라우저가 위치 기능을 지원하는지
  supported: boolean;
  permission(): Promise<LocationPermission>;
  // 위치를 한 번 읽는다. 실패해도 던지지 않고 결과로 알린다
  read(): Promise<LocationReadResult>;
};

export type IntroSeen = { get(): boolean; set(): void };

export type MyLocationState = {
  // idle: 아무 안내가 없음, intro: 권한을 묻기 전 안내, reading: 위치를 읽는 중,
  // denied: 권한이 꺼져 있음, failed: 읽지 못함, unsupported: 이 기기는 위치를 쓸 수 없음
  status: "idle" | "intro" | "reading" | "denied" | "failed" | "unsupported";
  // 마지막으로 읽은 내 위치. 다시 읽는 동안과 실패한 뒤에도 남긴다(점이 그동안 움직이지 않는다, #226-S2)
  fix: LocationFix | null;
};

const INITIAL: MyLocationState = { status: "idle", fix: null };

export class MyLocationController {
  private state: MyLocationState = INITIAL;
  private current = 0;
  // 권한을 알아보는 중이거나 읽는 중이라 새로 시작하지 않는다
  private busy = false;
  private readonly listeners = new Set<() => void>();

  private readonly source: LocationSource;
  private readonly introSeen: IntroSeen;
  private onFix: (fix: LocationFix) => void;

  // 테스트를 돌리는 Node의 변환 방식은 생성자 매개변수 속성(private readonly …)을 지원하지 않아 필드를 따로 둔다
  constructor(source: LocationSource, introSeen: IntroSeen, onFix: (fix: LocationFix) => void = () => {}) {
    this.source = source;
    this.introSeen = introSeen;
    this.onFix = onFix;
  }

  getState = (): MyLocationState => this.state;

  // 위치를 읽었을 때 알릴 곳을 바꾼다. 화면이 최신 함수를 넘길 때 쓴다
  setOnFix = (onFix: (fix: LocationFix) => void): void => {
    this.onFix = onFix;
  };

  subscribe = (listener: () => void): (() => void) => {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  };

  // "내 위치" 버튼을 눌렀다
  press = async (): Promise<void> => {
    if (this.busy) return;
    if (!this.source.supported) {
      this.set({ ...this.state, status: "unsupported" });
      return;
    }
    this.busy = true;
    const request = ++this.current;
    const permission = await this.source.permission().catch((): LocationPermission => "unknown");
    if (request !== this.current) return;
    if (permission === "denied") {
      this.busy = false;
      this.set({ ...this.state, status: "denied" });
    } else if (permission === "granted" || this.introSeen.get()) {
      await this.read(request);
    } else {
      this.busy = false;
      this.set({ ...this.state, status: "intro" });
    }
  };

  // 안내의 "계속": 브라우저의 권한 창이 뜨도록 위치를 읽는다
  continueIntro = async (): Promise<void> => {
    if (this.state.status !== "intro" || this.busy) return;
    this.introSeen.set();
    this.busy = true;
    await this.read(++this.current);
  };

  // 안내의 "나중에"나 닫기: 아무것도 바꾸지 않고 안내만 닫는다
  dismiss = (): void => {
    if (this.state.status === "reading" || this.state.status === "idle") return;
    this.set({ ...this.state, status: "idle" });
  };

  // 화면이 사라질 때 부른다. 읽던 위치의 응답은 버린다
  dispose = (): void => {
    this.current += 1;
    this.busy = false;
    if (this.state.status === "reading") this.set({ ...this.state, status: "idle" });
  };

  private async read(request: number): Promise<void> {
    this.set({ ...this.state, status: "reading" });
    const result = await this.source.read().catch((): LocationReadResult => ({ ok: false, reason: "unavailable" }));
    if (request !== this.current) return;
    this.busy = false;
    if (result.ok) {
      this.set({ status: "idle", fix: result.fix });
      this.onFix(result.fix);
    } else {
      this.set({ ...this.state, status: result.reason === "denied" ? "denied" : "failed" });
    }
  }

  private set(next: MyLocationState) {
    this.state = next;
    this.listeners.forEach((listener) => listener());
  }
}
