package com.pawmento.postcard.application.port.out;

public interface StorePostcardFilePort {
  // sources/{postcardId}.jpg로 저장하고 파일 이름을 돌려준다.
  String storeSourcePhoto(Long postcardId, byte[] jpeg);
}
