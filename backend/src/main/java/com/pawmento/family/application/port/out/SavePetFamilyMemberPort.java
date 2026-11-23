package com.pawmento.family.application.port.out;

import com.pawmento.family.domain.PetFamilyMember;

public interface SavePetFamilyMemberPort {

    PetFamilyMember save(PetFamilyMember member);
    
}
