package com.pawmento.postcard.application.port.out;

public interface GeneratePostcardImagePort {
  // 엽서에 기록하는 생성기 이름. 요청 때 동의한 생성기와 다르면 작업이 부르지 않는다.
  String name();

  // 실패(시간 초과, 차단, 빈 응답)는 PostcardGenerationFailedException으로 던진다.
  // 예외 메시지에 API 키나 외부 응답 본문을 넣지 않는다.
  GeneratedPostcardImage generate(PostcardImageRequest request);
}
