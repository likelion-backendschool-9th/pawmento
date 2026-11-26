package com.pawmento.member.domain;

import java.time.Duration;
import java.time.Instant;

import lombok.Getter;

@Getter
public class EmailVerification {

    public static final Duration CODE_VALID_FOR = Duration.ofMinutes(10);
    public static final Duration RESEND_AFTER = Duration.ofSeconds(60);

    private Long id;

    private String email;

    private VerificationPurpose purpose;

    private String codeHash;

    private int failedAttempts;

    private Instant codeExpiresAt;

    private Instant sentAt;

    private EmailVerification(String email, VerificationPurpose purpose, String codeHash, int failedAttempts,
            Instant codeExpiresAt, Instant sentAt) {
        this.email = email;
        this.purpose = purpose;
        this.codeHash = codeHash;
        this.failedAttempts = failedAttempts;
        this.codeExpiresAt = codeExpiresAt;
        this.sentAt = sentAt;
    }

    public static EmailVerification send(String email, VerificationPurpose purpose, String codeHash, Instant now) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("이메일이 필요합니다.");
        }
        if (purpose == null) {
            throw new IllegalArgumentException("인증 용도가 필요합니다.");
        }
        if (codeHash == null || codeHash.isBlank()) {
            throw new IllegalArgumentException("인증번호 해시가 필요합니다.");
        }
        return new EmailVerification(Member.normalizeEmail(email), purpose, codeHash, 0,
                now.plus(CODE_VALID_FOR), now);
    }

    public static EmailVerification restore(Long id, String email, VerificationPurpose purpose, String codeHash,
            int failedAttempts, Instant codeExpiresAt, Instant sentAt) {
        EmailVerification verification = new EmailVerification(email, purpose, codeHash, failedAttempts,
                codeExpiresAt, sentAt);
        verification.id = id;
        return verification;
    }

    public Instant resendableAt() {
        return sentAt.plus(RESEND_AFTER);
    }

    public boolean canResendAt(Instant now) {
        return !now.isBefore(resendableAt());
    }
}
