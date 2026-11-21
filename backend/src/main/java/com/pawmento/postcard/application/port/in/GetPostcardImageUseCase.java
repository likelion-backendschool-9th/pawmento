package com.pawmento.postcard.application.port.in;

public interface GetPostcardImageUseCase {
  GetPostcardImageResult get(GetPostcardImageQuery query);
}
