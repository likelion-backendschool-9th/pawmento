package com.pawmento.postcard.application.port.in;

public record GetPostcardQuery(Long memberId, Long petId, Long postcardId) {}
