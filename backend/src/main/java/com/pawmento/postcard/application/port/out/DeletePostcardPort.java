package com.pawmento.postcard.application.port.out;

public interface DeletePostcardPort {
  // 지운 행이 있으면 true. 상태와 상관없이 지운다.
  boolean deleteById(Long postcardId);
}
