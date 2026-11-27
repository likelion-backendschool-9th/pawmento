// "내 위치"의 안내 문구(#226-S1, S3, S4). 화면은 이 글을 그대로 보인다.

export const LOCATION_INTRO = {
  title: "내 주변을 찾아볼까요?",
  body: "내 주변 장소를 찾으려고 현재 위치를 써요. 위치는 앱에 저장하지 않아요.",
  // 믿고 눌러도 되는 이유. 모두 이 앱이 실제로 하는 일이다
  points: ["누를 때 한 번만 읽어요", "움직여도 따라다니지 않아요", "브라우저 설정에서 언제든 끌 수 있어요"],
  // 주변 장소를 찾으려면 내 위치 근처의 지도 범위(위·경도)가 GET 주소의 쿼리로 서버에 가고, 서버의 접속 기록에 남을 수 있다(호스트 nginx의 기본 로그 형식은 쿼리를 적는다).
  // 숨기지 않고 알린다(#226-S10). 서버가 쿼리를 적지 않도록 고친 뒤에 이 문구를 줄일 수 있다
  note: "찾는 동안 내 위치 근처의 지도 범위가 서버로 전송되고, 서버 접속 기록에 남을 수 있어요.",
  confirm: "계속",
  later: "나중에",
};

export const LOCATION_DENIED = {
  title: "위치 권한이 꺼져 있어요",
  reason: "내 주변을 가까운 순으로 보려면 위치가 필요해요.",
  fix: "설정에서 이 사이트의 위치를 허용해 주세요. 지도와 검색은 그대로 쓸 수 있어요.",
  guideTitle: "설정 방법 보기",
  retry: "다시 시도",
  close: "닫기",
  // 기기별 설정 방법
  guides: [
    {
      device: "iPhone",
      steps: [
        "설정 → 개인정보 보호 및 보안 → 위치 서비스를 켜요",
        "목록에서 Safari 웹사이트(홈 화면 앱이면 앱 이름)를 골라요",
        "'앱을 사용하는 동안'으로 바꿔요",
        "이 사이트만 거부했다면: Safari 주소창의 aA → 웹사이트 설정 → 위치를 '허용'으로 바꿔요",
      ],
    },
    {
      device: "Android",
      steps: ["Chrome 주소창 왼쪽의 아이콘을 눌러요", "권한(또는 사이트 설정) → 위치를 골라요", "'허용'으로 바꿔요"],
    },
  ],
};

export const LOCATION_FAILED = "위치를 찾지 못했어요. 잠시 뒤 다시 눌러 주세요.";
export const LOCATION_UNSUPPORTED = "이 기기에서는 내 위치를 쓸 수 없어요.";

// 지도의 내 위치 버튼(아이콘만 있다)의 이름. 화면 읽기 프로그램과 마우스를 올렸을 때 보이는 안내에 쓴다
export const LOCATE_LABEL = {
  idle: "내 위치로 이동",
  reading: "내 위치를 찾는 중",
  // 이 기기가 위치를 지원하지 않아도 버튼은 눌러서 이유를 볼 수 있다. 사용 불가(aria-disabled)가 아니라 이름으로 알린다
  unsupported: "내 위치: 이 기기에서는 쓸 수 없어요. 눌러서 이유 보기",
};

export function locateLabel({ reading, supported }: { reading: boolean; supported: boolean }): string {
  if (!supported) return LOCATE_LABEL.unsupported;
  return reading ? LOCATE_LABEL.reading : LOCATE_LABEL.idle;
}
