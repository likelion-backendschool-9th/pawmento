package com.pawmento.postcard.application.port.out;

// 저장 폴더의 파일 하나. name은 "<엽서 ID>.<확장자>"이고, 쓰다 만 파일은 partial이다.
public record PostcardFile(Kind kind, String name, Long postcardId, boolean partial) {
  public enum Kind {
    IMAGE,
    SOURCE
  }
}
