package com.pawmento.family.application.port.out;

public interface DeletePetInviteCodesByIssuerPort {

    // 그 회원이 그 반려동물에 발급한 코드를 모두 지운다(만료 여부와 관계없이)
    int deleteByIssuer(Long petId, Long issuerMemberId);
}