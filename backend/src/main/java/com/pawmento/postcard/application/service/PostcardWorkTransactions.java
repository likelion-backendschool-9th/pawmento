package com.pawmento.postcard.application.service;

import com.pawmento.postcard.application.port.out.*;
import com.pawmento.postcard.domain.Postcard;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 외부 호출을 트랜잭션 밖에 두려고 단계마다 짧은 트랜잭션을 연다.
@Component
@RequiredArgsConstructor
public class PostcardWorkTransactions {
  private final PostcardWorkPort work;
  private final PostcardUsagePort usage;

  @Transactional
  public Optional<Postcard> claim(Instant now) {
    return work.claimNext(now);
  }

  @Transactional
  public boolean complete(Long postcardId, String imageKey, String contentType, Instant now) {
    return work.complete(postcardId, imageKey, contentType, now);
  }

  // 전환에 성공한 쪽만 환불해 겹친 실패·만료에서도 한 번만 돌려준다.
  @Transactional
  public boolean fail(Postcard postcard) {
    if (!work.fail(postcard.getId(), Postcard.FAILURE_MESSAGE)) return false;
    usage.refundMember(postcard.getRequestedOn(), postcard.getOwnerMemberId());
    return true;
  }
}
