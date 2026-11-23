// 핀을 눌렀을 때 일기 상세로 갈 수 있는지 정한다.
// 상세 화면은 지금 브라우저에 저장된 기록만 찾고 서버 일기는 읽지 못한다. 서버 핀의 diaryId로 이동하면 404가 되므로,
// 서버 일기 상세를 읽도록 연결하기 전에는 브라우저 상태에 있는 기록의 핀만 링크로 연다.
export type PinDetail = "OPEN" | "SAMPLE" | "NOT_READY";

export function pinDetail(sample: boolean, openable: boolean): PinDetail {
  if (sample) return "SAMPLE";
  return openable ? "OPEN" : "NOT_READY";
}

// 이 반려동물의 브라우저 기록 중에 해당 일기 id가 있는가
export function isOpenableDiary(
  diaries: readonly { id: string; petId: string }[],
  petId: string,
  diaryId: string,
): boolean {
  return diaries.some((entry) => entry.petId === petId && entry.id === diaryId);
}
