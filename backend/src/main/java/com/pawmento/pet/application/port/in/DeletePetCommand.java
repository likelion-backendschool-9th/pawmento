package com.pawmento.pet.application.port.in;

public record DeletePetCommand(Long memberId, Long petId) {
}
