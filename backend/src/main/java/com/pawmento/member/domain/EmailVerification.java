package com.pawmento.member.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.pawmento.member.domain.exception.InvalidEmailVerificationException;

import lombok.Getter;

// 이메일·용도마다 하나. 다시 받으면 새 번호로 바꾸고, 맞는 번호를 넣으면 가입에 쓸 토큰을 둔다 (docs/member/email-verification.md)
@Getter
public class EmailVerification {

	public static final Duration CODE_VALID_FOR = Duration.ofMinutes(5);
	public static final Duration RESEND_AFTER = Duration.ofSeconds(60);
	public static final Duration TOKEN_VALID_FOR = Duration.ofMinutes(30);
	public static final int MAX_FAILED_ATTEMPTS = 5;

	private Long id;

	private String email;

	private VerificationPurpose purpose;

	private String codeHash;

	// 발송마다 새로 만든다. 같은 번호가 다시 뽑혀도 발송끼리 구별한다
	private String sendId;

	private int failedAttempts;

	private Instant codeExpiresAt;

	private Instant sentAt;

	// 맞는 번호를 넣은 시각. 확인 전에는 null이다
	private Instant verifiedAt;

	// 확인 뒤 내준 토큰의 해시와 만료. 확인 전에는 null이다
	private String tokenHash;

	private Instant tokenExpiresAt;

	// 만들 때와 DB에서 되살릴 때 모두 이 검사를 거친다
	private EmailVerification(String email, VerificationPurpose purpose, String codeHash, String sendId,
			int failedAttempts, Instant codeExpiresAt, Instant sentAt, Instant verifiedAt, String tokenHash,
			Instant tokenExpiresAt) {
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
		// 확인 시각·토큰 해시·토큰 만료는 셋 다 있거나 셋 다 없다
		boolean verified = verifiedAt != null;
		if (verified != (tokenHash != null && !tokenHash.isBlank()) || verified != (tokenExpiresAt != null)) {
			throw new InvalidEmailVerificationException("확인 시각과 토큰이 함께 있어야 합니다.");
		}
		this.email = email;
		this.purpose = purpose;
		this.codeHash = codeHash;
		this.sendId = sendId;
		this.failedAttempts = failedAttempts;
		this.codeExpiresAt = codeExpiresAt;
		this.sentAt = sentAt;
		this.verifiedAt = verifiedAt;
		this.tokenHash = tokenHash;
		this.tokenExpiresAt = tokenExpiresAt;
	}

	public static EmailVerification send(String email, VerificationPurpose purpose, String codeHash, Instant now) {
		if (now == null) {
			throw new InvalidEmailVerificationException("보낸 시각이 필요합니다.");
		}
		String normalized = email == null ? null : Member.normalizeEmail(email);
		return new EmailVerification(normalized, purpose, codeHash, UUID.randomUUID().toString(), 0,
				now.plus(CODE_VALID_FOR), now, null, null, null);
	}

	public static EmailVerification restore(Long id, String email, VerificationPurpose purpose, String codeHash,
			String sendId, int failedAttempts, Instant codeExpiresAt, Instant sentAt, Instant verifiedAt,
			String tokenHash, Instant tokenExpiresAt) {
		EmailVerification verification = new EmailVerification(email, purpose, codeHash, sendId, failedAttempts,
				codeExpiresAt, sentAt, verifiedAt, tokenHash, tokenExpiresAt);
		verification.id = id;
		return verification;
	}

	// 같은 줄에 새 번호를 보낸다. 직전 번호와 같은 번호는 보내지 않고, 앞서 받은 확인·토큰도 무효가 된다 (#279-S3)
	public EmailVerification resend(String newCodeHash, Instant now) {
		if (codeHash.equals(newCodeHash)) {
			throw new InvalidEmailVerificationException("직전 번호와 다른 번호를 보내야 합니다.");
		}
		EmailVerification next = send(email, purpose, newCodeHash, now);
		next.id = id;
		return next;
	}

	// 보낸 뒤 60초가 지나야 다시 보낼 수 있다
	public Instant resendableAt() {
		return sentAt.plus(RESEND_AFTER);
	}

	public boolean canResendAt(Instant now) {
		return !now.isBefore(resendableAt());
	}

	// #279-S5~S7 아직 확인하지 않았고, 5번 넘게 틀리지 않았고, 번호가 만료되지 않았을 때만 확인할 수 있다
	public boolean isConfirmableAt(Instant now) {
		return verifiedAt == null && failedAttempts < MAX_FAILED_ATTEMPTS && now.isBefore(codeExpiresAt);
	}

	// 맞는 번호를 넣었다. 가입에 한 번 쓸 토큰을 30분 동안 둔다
	public EmailVerification confirm(String newTokenHash, Instant now) {
		if (!isConfirmableAt(now)) {
			throw new InvalidEmailVerificationException("확인할 수 없는 인증입니다.");
		}
		EmailVerification confirmed = new EmailVerification(email, purpose, codeHash, sendId, failedAttempts,
				codeExpiresAt, sentAt, now, newTokenHash, now.plus(TOKEN_VALID_FOR));
		confirmed.id = id;
		return confirmed;
	}

}