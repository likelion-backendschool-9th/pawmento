package com.pawmento.postcard.application.service;

import com.pawmento.postcard.application.port.in.*;
import com.pawmento.postcard.application.port.out.*;
import com.pawmento.postcard.domain.Postcard;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetPostcardUsageService implements GetPostcardUsageUseCase {
  private final LoadPostcardPetPort pets;
  private final LoadPostcardPolicyPort policies;
  private final PostcardUsagePort usage;
  private final Clock clock;

  @Override
  @Transactional(readOnly = true)
  public GetPostcardUsageResult get(GetPostcardUsageQuery q) {
    pets.loadOwned(q.memberId(), q.petId());
    var policy = policies.load();
    long used = usage.memberCount(LocalDate.ofInstant(clock.instant(), Postcard.ZONE), q.memberId());
    return new GetPostcardUsageResult(
        policy.beta(), policy.dailyLimitPerMember(), used, policy.remaining(used));
  }
}
