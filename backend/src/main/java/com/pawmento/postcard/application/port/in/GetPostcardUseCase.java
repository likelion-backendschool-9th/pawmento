package com.pawmento.postcard.application.port.in;

public interface GetPostcardUseCase {
  GetPostcardResult get(GetPostcardQuery query);
}
