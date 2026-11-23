package com.pawmento.postcard.application.port.out;

import java.time.LocalDate;

// 하루 횟수는 엽서 행이 아니라 따로 센다. 엽서를 지워도 줄지 않는다.
public interface PostcardUsagePort {
  // 전체 → 회원 순서로 그날 행을 잠근다(없으면 만든다). 순서를 고정해 교착을 막는다.
  PostcardUsage lock(LocalDate date, Long memberId);

  void increment(LocalDate date, Long memberId);

  long memberCount(LocalDate date, Long memberId);
}
