package com.pawmento.media.application.service;

import com.pawmento.media.application.exception.MediaAssetNotFoundException;
import com.pawmento.media.application.port.in.*;
import com.pawmento.media.application.port.out.*;
import com.pawmento.media.domain.*;
import java.time.Clock;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
public class ChangeMediaReferencesService
    implements ChangeMediaReferencesUseCase, LockMediaReferencesUseCase {
  private final LockMediaAssetPort load;
  private final LoadMediaOwnershipPort ownership;
  private final SaveMediaAssetPort save;
  private final InspectMediaReferencesPort references;
  private final MediaWorkQueuePort jobs;
  private final Clock clock;

  @Transactional(propagation = Propagation.MANDATORY)
  public void lock(LockMediaReferencesCommand c) {
    var ids = new TreeSet<>(c.assetIds());
    // 상태 엔티티를 영속성 컨텍스트에 남기지 않는 일괄 identity 조회로 먼저 검사한다.
    var owners = ownership.loadOwnership(List.copyOf(ids));
    if (owners.size() != ids.size()
        || owners.stream()
            .anyMatch(
                a ->
                    !Objects.equals(a.uploaderMemberId(), c.memberId())
                        || !Objects.equals(a.petId(), c.petId())
                        || a.purpose() != MediaPurpose.DIARY_ATTACHMENT))
      throw new MediaAssetNotFoundException();
    for (Long id : ids) load.findForUpdate(id).orElseThrow(MediaAssetNotFoundException::new);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void change(ChangeMediaReferencesCommand c) {
    var old = new HashSet<>(c.previous());
    var next = new HashSet<>(c.requested());
    if (old.size() != c.previous().size() || next.size() != c.requested().size())
      throw new IllegalArgumentException("Duplicate reference IDs");
    var ids = new TreeSet<Long>(old);
    ids.addAll(next);
    lock(new LockMediaReferencesCommand(c.memberId(), c.petId(), List.copyOf(ids)));
    for (Long id : ids) {
      var a = load.findForUpdate(id).orElseThrow(MediaAssetNotFoundException::new);
      if (old.contains(id) && next.contains(id)) continue; // 불일치 첨부도 유지·순서 변경은 참조 수를 건드리지 않는다.
      if (next.contains(id)) {
        a.acquireReference(clock.instant());
        save.save(a);
      } else {
        // 이미 0인 참조는 다시 감소시키지 않는다. 실제 부모 연결은 이 호출 전에 같은 TX에서 제거됐다.
        if (a.getReferenceCount() > 0) {
          a.releaseReference(clock.instant());
          a = save.save(a);
        }
        if (a.getReferenceCount() == 0
            && !references.isReferenced(id)
            && a.getStatus() != MediaUploadStatus.DELETING
            && a.getStatus() != MediaUploadStatus.DELETED) {
          a.requestDeletion(clock.instant());
          jobs.enqueueDeletion(id, clock.instant());
          save.save(a);
        }
      }
    }
  }
}
