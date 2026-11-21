package com.pawmento.postcard.application.port.in;

public interface RequestPostcardUseCase {
  RequestPostcardResult request(RequestPostcardCommand command);
}
