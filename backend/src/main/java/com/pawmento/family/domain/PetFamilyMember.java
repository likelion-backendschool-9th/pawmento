package com.pawmento.family.domain;

import java.time.Instant;

import lombok.Getter;

@Getter
public class PetFamilyMember {

    private Long id;

    private Long petId;

    private Long memberId;

    private Instant joinedAt;

    private PetFamilyMember(Long petId, Long memberId, Instant joinedAt) {
        this.petId = petId;
        this.memberId = memberId;
        this.joinedAt = joinedAt;
    }

    public static PetFamilyMember join(Long petId, Long memberId, Instant now) {
        if (petId == null) {
            throw new IllegalArgumentException("반려동물이 필요합니다.");
        }
        if (memberId == null) {
            throw new IllegalArgumentException("회원이 필요합니다.");
        }
        return new PetFamilyMember(petId, memberId, now);
    }

    public static PetFamilyMember restore(Long id, Long petId, Long memberId, Instant joinedAt) {
        PetFamilyMember member = new PetFamilyMember(petId, memberId, joinedAt);
        member.id = id;
        return member;
    }
}
