package com.pawmento.media.adapter.in.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record CreateMediaUploadRequest(
    @NotBlank @Pattern(regexp = "DIARY_ATTACHMENT|QUESTION_ANSWER_ATTACHMENT") String purpose,
    @NotBlank @Size(max = 255) String filename,
    @NotBlank @Size(max = 100) String mimeType,
    @NotNull @Positive Long sizeBytes,
    @Valid LocationConsent locationConsent) {
  public record LocationConsent(@NotNull Boolean granted, @Size(max = 100) String policyVersion) {}
}
