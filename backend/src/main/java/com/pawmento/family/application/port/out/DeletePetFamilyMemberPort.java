package com.pawmento.family.application.port.out;

public interface DeletePetFamilyMemberPort {

    // 한 문장으로 지운다. 지운 줄이 있으면 true
    boolean delete(Long petId, Long memberId);
}