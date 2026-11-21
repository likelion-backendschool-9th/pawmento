import type { WalkRecord } from "@/map/domain/WalkRecord";

// 그날의 산책 경로를 그리는 쪽(네이버 지도, 예시 지도)이 화면과 주고받는 약속이다.
export type WalkRoutesCanvasProps = {
  walks: readonly WalkRecord[];
  selectedId: string | null;
  onSelect: (walkId: string | null) => void;
};
