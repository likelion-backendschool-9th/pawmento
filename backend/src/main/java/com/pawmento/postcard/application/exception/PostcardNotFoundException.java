package com.pawmento.postcard.application.exception;

import com.pawmento.global.exception.ResourceNotFoundException;

public class PostcardNotFoundException extends ResourceNotFoundException {
  public PostcardNotFoundException() {
    super("엽서를 찾을 수 없어요.");
  }
}
