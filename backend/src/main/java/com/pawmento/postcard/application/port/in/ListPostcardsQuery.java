package com.pawmento.postcard.application.port.in;

// cursor는 앞 페이지의 마지막 엽서 ID다. 없으면 처음부터 본다.
public record ListPostcardsQuery(Long memberId, Long petId, int size, Long cursor) {}
