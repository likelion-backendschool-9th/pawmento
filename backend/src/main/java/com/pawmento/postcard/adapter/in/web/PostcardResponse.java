package com.pawmento.postcard.adapter.in.web;

import com.pawmento.global.web.ApiPaths;
import com.pawmento.postcard.application.port.in.GetPostcardResult;
import com.pawmento.postcard.application.port.in.GetPostcardUsageResult;
import com.pawmento.postcard.application.port.in.RequestPostcardResult;
import java.time.Instant;

record PostcardResponse(
    Long postcardId,
    Long petId,
    Source source,
    String status,
    String title,
    String text,
    String note,
    String imageUrl,
    Instant createdAt,
    Instant completedAt,
    String errorMessage) {
  record Source(String kind, Long id) {}

  record Created(Long postcardId, String status) {
    static Created from(RequestPostcardResult r) {
      return new Created(r.postcardId(), r.status());
    }
  }

  record Usage(boolean beta, int dailyLimit, long usedToday, long remainingToday) {
    static Usage from(GetPostcardUsageResult r) {
      return new Usage(r.beta(), r.dailyLimit(), r.usedToday(), r.remainingToday());
    }
  }

  static PostcardResponse from(GetPostcardResult r) {
    return new PostcardResponse(
        r.postcardId(),
        r.petId(),
        new Source(r.sourceKind(), r.sourceId()),
        r.status(),
        r.title(),
        r.text(),
        r.note(),
        r.imageAvailable() ? imageUrl(r.postcardId(), r.petId()) : null,
        r.createdAt(),
        r.completedAt(),
        r.errorMessage());
  }

  static String imageUrl(Long postcardId, Long petId) {
    return ApiPaths.V1 + "/postcards/" + postcardId + "/image?petId=" + petId;
  }
}
