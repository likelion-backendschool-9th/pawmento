package com.pawmento.media.domain;

public class MediaViolation extends RuntimeException {
  public enum Reason {
    INVALID,
    FORBIDDEN,
    CONFLICT,
    TOO_LARGE,
    UNSUPPORTED
  }

  private final Reason reason;

  public MediaViolation(Reason reason, String message) {
    super(message);
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }
}
