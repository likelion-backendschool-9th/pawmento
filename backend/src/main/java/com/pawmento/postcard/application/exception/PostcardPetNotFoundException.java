package com.pawmento.postcard.application.exception;

import com.pawmento.global.exception.ResourceNotFoundException;

// 확인 뒤 반려동물이 먼저 지워져 엽서를 저장하지 못했다.
public class PostcardPetNotFoundException extends ResourceNotFoundException {
  public PostcardPetNotFoundException(Throwable cause) {
    super("반려동물을 찾을 수 없습니다.", cause);
  }
}
