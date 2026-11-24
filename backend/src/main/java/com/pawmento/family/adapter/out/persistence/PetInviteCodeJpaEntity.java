package com.pawmento.family.adapter.out.persistence;

import java.time.Instant;

import com.pawmento.family.domain.PetInviteCode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "pet_invite_codes", uniqueConstraints = @UniqueConstraint(
        name = PetInviteCodeJpaEntity.CODE_HASH_UNIQUE_CONSTRAINT, columnNames = "code_hash"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class PetInviteCodeJpaEntity {
    static final String CODE_HASH_UNIQUE_CONSTRAINT = "uk_pet_invite_codes_code_hash";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pet_id", nullable = false, updatable = false)
    private Long petId;

    @Column(name = "code_hash", nullable = false, updatable = false, length = 64)
    private String codeHash;

    @Column(name = "issuer_member_id", nullable = false, updatable = false)
    private Long issuerMemberId;

    @Column(nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    static PetInviteCodeJpaEntity from(PetInviteCode code) {
        PetInviteCodeJpaEntity entity = new PetInviteCodeJpaEntity();
        entity.id = code.getId();
        entity.petId = code.getPetId();
        entity.codeHash = code.getCodeHash();
        entity.issuerMemberId = code.getIssuerMemberId();
        entity.expiresAt = code.getExpiresAt();
        entity.createdAt = code.getCreatedAt();
        return entity;
    }

    PetInviteCode toDomain() {
        return PetInviteCode.restore(id, petId, codeHash, issuerMemberId, expiresAt, createdAt);
    }
}
