package com.pawmento.postcard.application.service;

import com.pawmento.postcard.application.port.out.*;
import com.pawmento.postcard.domain.Postcard;
import com.pawmento.postcard.domain.exception.InvalidPostcardException;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 외부 호출을 트랜잭션 밖에 두려고 단계마다 짧은 트랜잭션을 연다.
@Component
@RequiredArgsConstructor
@Slf4j
public class PostcardWorkTransactions {
  private final PostcardWorkPort work;
  private final LoadPostcardPort load;
  private final PostcardUsagePort usage;

  // 선점한 엽서. 저장된 값이 도메인 규칙에 맞지 않으면 postcard가 비어 있고, 이미 실패로 돌린 뒤다.
  public record Claimed(Long id, Postcard postcard) {}

  // 복원하지 못한 엽서는 선점한 트랜잭션에서 바로 실패로 돌린다. 롤백하면 다음 실행이 같은 행을 다시 골라
  // 그 뒤의 엽서가 계속 처리되지 않는다.
  @Transactional
  public Optional<Claimed> claim(Instant now) {
    return work.claimNext(now)
        .map(
            id -> {
              try {
                return new Claimed(id, load.findById(id).orElseThrow());
              } catch (InvalidPostcardException e) {
                log.warn("Postcard {} could not be restored and was failed", id);
                fail(id);
                return new Claimed(id, null);
              }
            });
  }

  @Transactional
  public boolean complete(Long postcardId, String imageKey, String contentType, Instant now) {
    return work.complete(postcardId, imageKey, contentType, now);
  }

  // 전환에 성공한 쪽만 환불해 겹친 실패·만료에서도 한 번만 돌려준다.
  @Transactional
  public boolean fail(Long postcardId) {
    var charge = work.fail(postcardId, Postcard.FAILURE_MESSAGE);
    charge.ifPresent(c -> usage.refundMember(c.requestedOn(), c.memberId()));
    return charge.isPresent();
  }
}
