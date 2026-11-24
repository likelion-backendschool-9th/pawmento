package com.pawmento.media.application.port.in;

final class MediaReferencePurpose {
  private MediaReferencePurpose() {}

  static void require(String purpose) {
    if (!"DIARY_ATTACHMENT".equals(purpose)
        && !"QUESTION_ANSWER_ATTACHMENT".equals(purpose)
        && !"PET_PROFILE".equals(purpose))
      throw new IllegalArgumentException("Unsupported attachment purpose");
  }
}
