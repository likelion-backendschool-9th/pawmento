package com.pawmento.media.application.service;

import com.pawmento.media.application.port.in.CleanupMediaUseCase;
import com.pawmento.media.application.port.out.*;
import java.time.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CleanupMediaService implements CleanupMediaUseCase {
  private final FindMediaCleanupPort find;
  private final MediaCleanupTransactions transactions;
  private final CollectMediaOrphansPort orphans;
  private final Clock clock;

  public void cleanup() {
    for (Long id : find.candidates(clock.instant(), 100)) transactions.schedule(id);
    orphans.collect(clock.instant().minus(Duration.ofHours(24)));
  }
}
