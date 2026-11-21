package com.pawmento.postcard.application.port.out;

public interface StorePostcardFilePort {
  // sources/{postcardId}.jpg로 저장하고 파일 이름을 돌려준다.
  String storeSourcePhoto(Long postcardId, byte[] jpeg);

  byte[] readSourcePhoto(String key);

  void deleteSourcePhoto(String key);

  // images/{postcardId}.<확장자>로 저장하고 파일 이름을 돌려준다.
  String storeImage(Long postcardId, byte[] bytes, String contentType);

  byte[] readImage(String key);

  void deleteImage(String key);
}
