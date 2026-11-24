// 개발·테스트 목업의 활성 여부만 정한다.
// 데모로 시작하기(운영 체험)는 #168 결정 전까지 이 값과 무관하게 동작한다.
const DEVELOPMENT_MOCK_FLAG = "1";

export function assertDevelopmentMockAllowed(
  requested = process.env.NEXT_PUBLIC_PAWMENTO_MOCK === DEVELOPMENT_MOCK_FLAG,
): void {
  if (process.env.NODE_ENV === "production" && requested) {
    throw new Error("운영 빌드에서는 개발용 목업을 켤 수 없습니다.");
  }
}

// 개발·테스트에서 예시 데이터를 쓰도록 명시적으로 켰는지. 운영 빌드에서는 assertDevelopmentMockAllowed가 켜지 못하게 막는다.
export function isDevelopmentMockRequested(
  requested = process.env.NEXT_PUBLIC_PAWMENTO_MOCK === DEVELOPMENT_MOCK_FLAG,
): boolean {
  return requested;
}
