package com.pawmento.global.exception;

public abstract class StorageUnavailableException extends RuntimeException {
  protected StorageUnavailableException(String message) {
    super(message);
  }

  protected StorageUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
