package com.pawmento.diary.application.port.out;

import java.util.Optional;

public interface FindDiaryAuthorNamePort {
  // 기존 데이터 보강 전용. 빈 값은 실제 회원 부재만 뜻하고 조회 장애는 예외로 전달한다.
  Optional<String> findName(Long memberId);
}
