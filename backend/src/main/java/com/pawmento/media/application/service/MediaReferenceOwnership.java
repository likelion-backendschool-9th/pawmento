package com.pawmento.media.application.service;

import com.pawmento.media.application.exception.MediaAssetNotFoundException;
import com.pawmento.media.application.port.out.MediaOwnership;
import com.pawmento.media.domain.MediaPurpose;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

// 첨부로 쓸 수 있는 자산인지 판정한다. 잠금과 사전 검증이 같은 정책을 쓰도록 한곳에 둔다.
// 같은 아이·용도여야 하고, 이미 연결된 첨부가 아니면 요청한 회원이 올린 자산이어야 한다.
final class MediaReferenceOwnership {
  private MediaReferenceOwnership() {}

  static void require(
      List<MediaOwnership> owners,
      Collection<Long> ids,
      Long memberId,
      Long petId,
      MediaPurpose purpose,
      Collection<Long> previous) {
    if (owners.size() != ids.size()
        || owners.stream()
            .anyMatch(
                a ->
                    !Objects.equals(a.petId(), petId)
                        || a.purpose() != purpose
                        || (!Objects.equals(a.uploaderMemberId(), memberId)
                            && !previous.contains(a.assetId()))))
      throw new MediaAssetNotFoundException();
  }
}
