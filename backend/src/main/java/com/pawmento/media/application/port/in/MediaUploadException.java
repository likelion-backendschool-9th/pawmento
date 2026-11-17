package com.pawmento.media.application.port.in;

import com.pawmento.media.domain.MediaViolation;

public class MediaUploadException extends RuntimeException {
  public enum Kind {
    INVALID,
    NOT_FOUND,
    FORBIDDEN,
    CONFLICT,
    TOO_LARGE,
    UNSUPPORTED,
    STORAGE
  }

  private final Kind kind;

  public MediaUploadException(Kind kind, String message) {
    super(message);
    this.kind = kind;
  }

  public Kind kind() {
    return kind;
  }

  public static MediaUploadException from(MediaViolation e) {
    return new MediaUploadException(Kind.valueOf(e.reason().name()), e.getMessage());
  }
}
