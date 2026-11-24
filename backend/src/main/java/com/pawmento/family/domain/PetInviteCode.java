package com.pawmento.family.domain;

import java.time.Duration;
import java.time.Instant;

import lombok.Getter;

@Getter
public class PetInviteCode {

    public static final Duration VALID_FOR = Duration.ofHours(24);

    private Long id;

    private Long petId;

    private String codeHash;

    private Long issuerMemberId;

    private Instant expiresAt;

    private Instant createdAt;

    private PetInviteCode(Long petId, String codeHash, Long issuerMemberId, Instant expiresAt, Instant createdAt) {
        this.petId = petId;
        this.codeHash = codeHash;
        this.issuerMemberId = issuerMemberId;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public static PetInviteCode issue(Long petId, String codeHash, Long issuerMemberId, Instant now) {
        if (petId == null) {
            throw new IllegalArgumentException("반려동물이 필요합니다.");
        }
        if (codeHash == null || codeHash.isBlank()) {
            throw new IllegalArgumentException("코드 해시가 필요합니다.");
        }
        if (issuerMemberId == null) {
            throw new IllegalArgumentException("발급한 회원이 필요합니다.");
        }
        return new PetInviteCode(petId, codeHash, issuerMemberId, now.plus(VALID_FOR), now);
    }

    public static PetInviteCode restore(Long id, Long petId, String codeHash, Long issuerMemberId, Instant expiresAt, Instant createdAt) {
        PetInviteCode code = new PetInviteCode(petId, codeHash, issuerMemberId, expiresAt, createdAt);
        code.id = id;
        return code;
    }

    public boolean isUsableAt(Instant now) {
        return now.isBefore(expiresAt);
    }

}
