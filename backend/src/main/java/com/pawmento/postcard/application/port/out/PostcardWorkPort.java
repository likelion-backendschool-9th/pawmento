package com.pawmento.postcard.application.port.out;

import com.pawmento.postcard.domain.Postcard;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

// 상태 전환은 모두 status = 'PROCESSING' 조건부 갱신이다. 완성·실패·만료 중 하나만 성공한다.
public interface PostcardWorkPort {
  // 시작 시각이 비어 있는 PROCESSING 엽서 하나에 시작 시각을 기록해 선점한다.
  Optional<Postcard> claimNext(Instant now);

  boolean complete(Long postcardId, String imageKey, String contentType, Instant now);

  boolean fail(Long postcardId, String message);

  List<Long> startedBefore(Instant cutoff);
}
