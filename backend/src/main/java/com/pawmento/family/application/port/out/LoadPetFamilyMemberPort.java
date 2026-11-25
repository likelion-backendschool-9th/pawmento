package com.pawmento.family.application.port.out;

import java.util.List;

import com.pawmento.family.domain.PetFamilyMember;

public interface LoadPetFamilyMemberPort {

    // 그 회원이 그 반려동물의 가족인가
    boolean existsByPetIdAndMemberId(Long petId, Long memberId);

    // 그 반려동물의 가족 전부. 먼저 가족이 된 순서(같으면 id 순)
    List<PetFamilyMember> loadByPetId(Long petId);
}
