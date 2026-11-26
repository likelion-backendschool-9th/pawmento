// 탭 목록의 방향키 이동(WAI-ARIA 탭 패턴). 화면 없이 테스트한다.
// 왼쪽·오른쪽은 처음과 끝이 이어지고, Home·End는 처음과 끝으로 간다. 탭이 아닌 키는 null이다.
export function nextTabIndex(key: string, current: number, count: number): number | null {
  if (count <= 0) return null;
  switch (key) {
    case "ArrowRight":
    case "ArrowDown":
      return (current + 1) % count;
    case "ArrowLeft":
    case "ArrowUp":
      return (current - 1 + count) % count;
    case "Home":
      return 0;
    case "End":
      return count - 1;
    default:
      return null;
  }
}
