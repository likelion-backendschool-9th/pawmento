package com.pawmento.postcard.application.port.in;

public interface ListPostcardsUseCase {
  ListPostcardsResult list(ListPostcardsQuery query);
}
