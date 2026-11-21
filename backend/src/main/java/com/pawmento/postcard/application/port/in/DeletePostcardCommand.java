package com.pawmento.postcard.application.port.in;

public record DeletePostcardCommand(Long memberId, Long petId, Long postcardId) {}
