package com.pawmento.media.application.port.out;

public class MediaValidationException extends RuntimeException {
  public MediaValidationException(String reason) {
    super(reason);
  }
}
