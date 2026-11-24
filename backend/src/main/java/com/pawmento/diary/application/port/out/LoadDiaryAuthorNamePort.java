package com.pawmento.diary.application.port.out;

import com.pawmento.diary.domain.DiaryAuthorName;

// 일반 작성용. 호출자의 트랜잭션에 참여하고, 회원이 없으면 DiaryAuthorMemberNotFoundException을 던진다.
public interface LoadDiaryAuthorNamePort {
  DiaryAuthorName loadName(Long memberId);
}
