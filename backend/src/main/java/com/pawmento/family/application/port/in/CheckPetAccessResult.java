package com.pawmento.family.application.port.in;

import com.pawmento.family.domain.PetAccess;

public record CheckPetAccessResult(String role) {

    public static CheckPetAccessResult from(PetAccess access) {
        return new CheckPetAccessResult(access.name());
    }

    public boolean isOwner() {
        return PetAccess.OWNER.name().equals(role);
    }

    public boolean canRecord() {
        return PetAccess.valueOf(role).canRecord();
    }

}
