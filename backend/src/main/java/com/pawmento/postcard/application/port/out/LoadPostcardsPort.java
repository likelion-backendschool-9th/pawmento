package com.pawmento.postcard.application.port.out;

import com.pawmento.postcard.domain.Postcard;
import java.util.List;

public interface LoadPostcardsPort {
  // 최근 요청 순(ID 내림차순)으로 cursor보다 작은 ID만 limit개까지
  List<Postcard> findByPet(Long petId, Long cursor, int limit);
}
