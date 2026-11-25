package com.pawmento.family.application.port.out;

import java.util.Optional;

import com.pawmento.family.domain.PetInviteCode;

public interface LoadPetInviteCodePort {

    Optional<PetInviteCode> loadByCodeHash(String codeHash);

}
