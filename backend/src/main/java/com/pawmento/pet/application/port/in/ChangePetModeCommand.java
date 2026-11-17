package com.pawmento.pet.application.port.in;

public record ChangePetModeCommand(Long memberId, Long petId, String mode) {
}
