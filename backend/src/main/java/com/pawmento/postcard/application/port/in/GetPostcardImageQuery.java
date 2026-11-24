package com.pawmento.postcard.application.port.in;

public record GetPostcardImageQuery(Long memberId, Long petId, Long postcardId) {}
