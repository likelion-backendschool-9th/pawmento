package com.pawmento.media.application.port.out;

public class MediaStorageException extends RuntimeException {
  private final boolean tooLarge;

  public MediaStorageException(boolean tooLarge, Throwable cause) {
    super("파일을 저장하지 못했어요.", cause);
    this.tooLarge = tooLarge;
  }

  public boolean tooLarge() {
    return tooLarge;
  }
}
