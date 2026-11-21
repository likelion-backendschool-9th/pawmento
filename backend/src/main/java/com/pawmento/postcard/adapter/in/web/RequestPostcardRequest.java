package com.pawmento.postcard.adapter.in.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

// 외부 AI 서비스로 글과 사진을 보내므로 요청마다 consent: true를 받는다.
record RequestPostcardRequest(
    @NotNull(message = "엽서로 만들 기록을 골라 주세요.") @Valid Source source,
    @Size(max = 200, message = "설명은 200자까지 쓸 수 있어요.") String note,
    @NotNull(message = "외부 AI 서비스로 보내는 데 동의해 주세요.")
        @AssertTrue(message = "외부 AI 서비스로 보내는 데 동의해 주세요.")
        Boolean consent) {
  record Source(
      @NotNull(message = "엽서로 만들 수 없는 원본이에요.")
          @Pattern(regexp = "DIARY", message = "엽서로 만들 수 없는 원본이에요.")
          String kind,
      @NotNull(message = "엽서로 만들 기록을 골라 주세요.") @Positive(message = "기록 ID가 올바르지 않아요.")
          Long id) {}
}
