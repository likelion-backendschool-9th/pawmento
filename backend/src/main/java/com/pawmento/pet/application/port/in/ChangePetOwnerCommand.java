package com.pawmento.pet.application.port.in;

public record ChangePetOwnerCommand(Long petId, Long currentOwnerId, Long newOwnerId) {
}
