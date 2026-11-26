package com.pawmento.member.adapter.out.persistence;

import java.time.Instant;

import com.pawmento.member.domain.EmailVerification;
import com.pawmento.member.domain.VerificationPurpose;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;

@Entity
@Table(name = "email_verification", uniqueConstraints = @UniqueConstraint(
    name = EmailVerificationJpaEntity.EMAIL_PURPOSE_UNIQUE_CONSTRAINT, columnNames = { "email", "purpose" }))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class EmailVerificationJpaEntity {
    static final String EMAIL_PURPOSE_UNIQUE_CONSTRAINT = "uk_email_verification_email_purpose";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 254)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VerificationPurpose purpose;

    @Column(nullable = false, length = 64)
    private String codeHash;

    @Column(nullable = false)
    private int failedAttempts;

    @Column(nullable = false)
    private Instant codeExpiresAt;

    @Column(nullable = false)
    private Instant sentAt;

    static EmailVerificationJpaEntity from(EmailVerification verification) {
        EmailVerificationJpaEntity entity = new EmailVerificationJpaEntity();
        entity.id = verification.getId();
        entity.email = verification.getEmail();
        entity.purpose = verification.getPurpose();
        entity.codeHash = verification.getCodeHash();
        entity.failedAttempts = verification.getFailedAttempts();
        entity.codeExpiresAt = verification.getCodeExpiresAt();
        entity.sentAt = verification.getSentAt();
        return entity;
    }

    EmailVerification toDomain() {
        return EmailVerification.restore(id, email, purpose, codeHash, failedAttempts, codeExpiresAt, sentAt);
    }
}
