package com.pawmento.media.adapter.out.persistence;

import com.pawmento.media.application.port.out.EnqueueMediaValidationPort;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MediaValidationQueueAdapter implements EnqueueMediaValidationPort {
  private final MediaValidationJobJpaRepository jobs;

  @Override
  public void enqueue(Long id, Instant now) {
    jobs.saveAndFlush(MediaValidationJobJpaEntity.pending(id, now));
  }
}
