package com.pawmento.postcard.application.port.out;

public record GeneratedPostcardImage(byte[] bytes, String contentType) {
  public GeneratedPostcardImage {
    if (bytes == null || bytes.length == 0 || contentType == null)
      throw new IllegalArgumentException("생성 결과가 비어 있어요.");
  }
}
