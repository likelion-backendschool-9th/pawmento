package com.pawmento.postcard.application.port.in;

import com.pawmento.postcard.domain.Postcard;
import com.pawmento.postcard.domain.PostcardStatus;
import java.time.Instant;

// 상세와 목록 요약이 같은 값을 쓴다. 상태에 따라 imageAvailable·completedAt·errorMessage가 빈다.
public record GetPostcardResult(
    Long postcardId,
    Long petId,
    String sourceKind,
    Long sourceId,
    String status,
    String title,
    String text,
    String note,
    boolean imageAvailable,
    Instant createdAt,
    Instant completedAt,
    String errorMessage) {
  public static GetPostcardResult from(Postcard p) {
    return new GetPostcardResult(
        p.getId(),
        p.getPetId(),
        p.getSourceKind().name(),
        p.getSourceId(),
        p.getStatus().name(),
        p.getTitle(),
        p.getText(),
        p.getSourceNote(),
        p.getStatus() == PostcardStatus.COMPLETED,
        p.getCreatedAt(),
        p.getCompletedAt(),
        p.getErrorMessage());
  }
}
