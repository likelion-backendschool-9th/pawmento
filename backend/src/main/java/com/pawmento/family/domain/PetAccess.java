package com.pawmento.family.domain;

public enum PetAccess {
    OWNER,
    FAMILY,
    NONE;

    public boolean canRecord() {
        return this != NONE;
    }
    
}
