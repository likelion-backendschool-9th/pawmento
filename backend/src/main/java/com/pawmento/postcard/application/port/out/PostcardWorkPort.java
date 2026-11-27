package com.pawmento.postcard.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

// 상태 전환은 모두 status = 'PROCESSING' 조건부 갱신이다. 완성·실패·만료 중 하나만 성공한다.
public interface PostcardWorkPort {
  // 시작 시각이 비어 있는 PROCESSING 엽서 하나에 시작 시각을 기록해 선점하고 그 ID를 돌려준다.
  Optional<Long> claimNext(Instant now);

  boolean complete(Long postcardId, String imageKey, String contentType, Instant now);

  // 전환에 성공하면 환불할 회원과 날짜를 돌려준다. 행을 도메인으로 복원하지 않는다.
  Optional<PostcardCharge> fail(Long postcardId, String message);

  List<Long> startedBefore(Instant cutoff);
}
