package com.pawmento.diary.application.port.out;

import com.pawmento.diary.domain.DiaryAuthorName;
import java.util.Optional;

// 기존 일기의 작성자 이름 보강 전용. 조회마다 따로 격리한 읽기 트랜잭션을 쓴다.
// 빈 값은 실제 회원 부재만 뜻하고, 조회 장애와 잘못된 이름은 예외로 전달한다.
public interface LoadDiaryAuthorNameForBackfillPort {
  Optional<DiaryAuthorName> loadNameForBackfill(Long memberId);
}
