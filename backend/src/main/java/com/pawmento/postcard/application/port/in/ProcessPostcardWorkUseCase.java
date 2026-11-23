package com.pawmento.postcard.application.port.in;

public interface ProcessPostcardWorkUseCase {
  // 만료된 엽서를 실패로 바꾸고, 엽서 하나를 처리한다. 처리한 엽서가 있으면 true.
  boolean processNext();
}
