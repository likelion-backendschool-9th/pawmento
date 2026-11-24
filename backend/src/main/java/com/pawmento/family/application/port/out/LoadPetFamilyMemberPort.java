package com.pawmento.family.application.port.out;

public interface LoadPetFamilyMemberPort {

    // 그 회원이 그 반려동물의 가족인가
    boolean existsByPetIdAndMemberId(Long petId, Long memberId);
}
