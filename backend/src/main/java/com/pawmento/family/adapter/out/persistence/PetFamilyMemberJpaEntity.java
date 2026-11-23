package com.pawmento.family.adapter.out.persistence;

import java.time.Instant;

import com.pawmento.family.domain.PetFamilyMember;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;

@Entity
@Table(name = "pet_family_members", uniqueConstraints = @UniqueConstraint(
    name = PetFamilyMemberJpaEntity.PET_MEMBER_UNIQUE_CONSTRAINT, columnNames = { "pet_id", "member_id" }
) )
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class PetFamilyMemberJpaEntity {
    static final String PET_MEMBER_UNIQUE_CONSTRAINT = "uk_pet_family_members_pet_member";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pet_id", nullable = false, updatable = false)
    private Long petId;

    @Column(name = "member_id", nullable = false, updatable = false)
    private Long memberId;

    @Column(nullable = false, updatable = false)
    private Instant joinedAt;

    static PetFamilyMemberJpaEntity from(PetFamilyMember member) {
        PetFamilyMemberJpaEntity entity = new PetFamilyMemberJpaEntity();
        entity.id = member.getId();
        entity.petId = member.getPetId();
        entity.memberId = member.getMemberId();
        entity.joinedAt = member.getJoinedAt();
        return entity;
    }

    PetFamilyMember toDomain() {
        return PetFamilyMember.restore(id, petId, memberId, joinedAt);
    }
}
