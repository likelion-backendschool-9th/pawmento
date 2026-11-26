package com.pawmento.member.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.pawmento.member.domain.exception.InvalidEmailVerificationException;

import lombok.Getter;

@Getter
public class EmailVerification {

    public static final Duration CODE_VALID_FOR = Duration.ofMinutes(10);
    public static final Duration RESEND_AFTER = Duration.ofSeconds(60);

    private Long id;

    private String email;

    private VerificationPurpose purpose;

    private String codeHash;

    private String sendId;

    private int failedAttempts;

    private Instant codeExpiresAt;

    private Instant sentAt;

    // 만들 때와 DB에서 되살릴 때 모두 이 검사를 거친다
	private EmailVerification(String email, VerificationPurpose purpose, String codeHash, String sendId,
			int failedAttempts, Instant codeExpiresAt, Instant sentAt) {
		if (email == null || email.isBlank()) {
			throw new InvalidEmailVerificationException("이메일이 필요합니다.");
		}
		if (purpose == null) {
			throw new InvalidEmailVerificationException("인증 용도가 필요합니다.");
		}
		if (codeHash == null || codeHash.isBlank()) {
			throw new InvalidEmailVerificationException("인증번호 해시가 필요합니다.");
		}
		if (sendId == null || sendId.isBlank()) {
			throw new InvalidEmailVerificationException("발송 식별자가 필요합니다.");
		}
		if (failedAttempts < 0) {
			throw new InvalidEmailVerificationException("틀린 횟수는 0 이상이어야 합니다.");
		}
		if (codeExpiresAt == null || sentAt == null) {
			throw new InvalidEmailVerificationException("보낸 시각과 만료 시각이 필요합니다.");
		}
		this.email = email;
		this.purpose = purpose;
		this.codeHash = codeHash;
		this.sendId = sendId;
		this.failedAttempts = failedAttempts;
		this.codeExpiresAt = codeExpiresAt;
		this.sentAt = sentAt;
	}

	public static EmailVerification send(String email, VerificationPurpose purpose, String codeHash, Instant now) {
		if (now == null) {
			throw new InvalidEmailVerificationException("보낸 시각이 필요합니다.");
		}
		String normalized = email == null ? null : Member.normalizeEmail(email);
		return new EmailVerification(normalized, purpose, codeHash, UUID.randomUUID().toString(), 0,
				now.plus(CODE_VALID_FOR), now);
	}

	public static EmailVerification restore(Long id, String email, VerificationPurpose purpose, String codeHash,
			String sendId, int failedAttempts, Instant codeExpiresAt, Instant sentAt) {
		EmailVerification verification = new EmailVerification(email, purpose, codeHash, sendId, failedAttempts,
				codeExpiresAt, sentAt);
		verification.id = id;
		return verification;
	}

	// 같은 줄에 새 번호를 보낸다. 직전 번호와 같은 번호는 보내지 않는다 (#279-S3 이전 번호 무효)
	public EmailVerification resend(String newCodeHash, Instant now) {
		if (codeHash.equals(newCodeHash)) {
			throw new InvalidEmailVerificationException("직전 번호와 다른 번호를 보내야 합니다.");
		}
		EmailVerification next = send(email, purpose, newCodeHash, now);
		next.id = id;
		return next;
	}

    public Instant resendableAt() {
        return sentAt.plus(RESEND_AFTER);
    }

    public boolean canResendAt(Instant now) {
        return !now.isBefore(resendableAt());
    }
}
