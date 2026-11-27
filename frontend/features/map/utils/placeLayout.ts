// 내 위치를 보이는 동안 지도 가운데의 안내(장소 없음, 종류를 모두 끔, 조회 오류)를 어떤 모양으로 보일지(#226-S2).
// 화면(브라우저 창)의 높이가 아니라 지도(stage)의 실제 높이로 정한다. 지도 위에는 검색창·종류 칩·"내 위치" 버튼·카드가 함께 있어서
// 창의 높이가 같아도 머리글·하단 메뉴 때문에 지도에 남는 공간이 다르다.
//
// 큰 카드(그림 포함, 높이 약 200px)는 지도 높이의 69% 지점이 가운데이고, 내 위치 점은 지도의 한가운데(50%)에 있다.
// 예시 지도는 점이 한가운데이고, 네이버 지도는 fitBounds에 위·아래가 같은 여백을 넘겨(fitMargin, utils/placeCanvas.ts) 한가운데에 놓는다.
// 여백이 위·아래가 다르면 점이 한가운데를 벗어나 이 계산이 맞지 않는다.
// 카드 위쪽이 점(반지름 10px)과 여유 약 8px 아래에 있으려면 0.69H - 100 ≥ 0.5H + 18, 즉 H ≥ 약 620px이다.
// 그보다 낮은 지도에서는 아래쪽 한 줄 카드로 줄인다. 지도 높이를 아직 모르면(0) 큰 카드로 둔다.
export const COMPACT_STAGE_HEIGHT = 620;

export function isCompactStage(stageHeight: number): boolean {
  return stageHeight > 0 && stageHeight <= COMPACT_STAGE_HEIGHT;
}
