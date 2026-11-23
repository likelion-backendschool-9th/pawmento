package com.pawmento.postcard.application.port.in;

public interface GetPostcardUsageUseCase {
  GetPostcardUsageResult get(GetPostcardUsageQuery query);
}
