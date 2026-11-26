package com.pawmento.member.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.pawmento.member.domain.exception.InvalidEmailVerificationException;

class EmailVerificationTest {
	private static final Instant NOW = Instant.parse("2026-11-26T09:00:00Z");

	@Test
	@DisplayName("#279-S1 보내면 이메일을 소문자로 정리하고, 번호는 5분 뒤 만료되며 틀린 횟수는 0이다")
	void sendNormalizesEmailAndSetsExpiry() {
		EmailVerification verification = EmailVerification.send(" A@Test.COM ", VerificationPurpose.SIGNUP, "hash", NOW);

		assertThat(verification.getId()).isNull();
		assertThat(verification.getEmail()).isEqualTo("a@test.com");
		assertThat(verification.getPurpose()).isEqualTo(VerificationPurpose.SIGNUP);
		assertThat(verification.getCodeHash()).isEqualTo("hash");
		assertThat(verification.getSendId()).isNotBlank();
		assertThat(verification.getFailedAttempts()).isZero();
		assertThat(verification.getSentAt()).isEqualTo(NOW);
		assertThat(verification.getCodeExpiresAt()).isEqualTo(Instant.parse("2026-11-26T09:05:00Z"));
	}

	@Test
	@DisplayName("#279-S4 발송마다 발송 식별자가 달라, 같은 번호 해시로 보내도 발송끼리 구별된다")
	void eachSendHasOwnSendId() {
		EmailVerification first = EmailVerification.send("a@test.com", VerificationPurpose.SIGNUP, "hash", NOW);
		EmailVerification second = EmailVerification.send("a@test.com", VerificationPurpose.SIGNUP, "hash", NOW);

		assertThat(first.getSendId()).isNotEqualTo(second.getSendId());
	}

	@Test
	@DisplayName("#279-S3 다시 받기는 보낸 뒤 60초부터 된다 (59초는 안 되고 60초는 된다)")
	void resendAfterSixtySeconds() {
		EmailVerification verification = EmailVerification.send("a@test.com", VerificationPurpose.SIGNUP, "hash", NOW);

		assertThat(verification.resendableAt()).isEqualTo(NOW.plusSeconds(60));
		assertThat(verification.canResendAt(NOW)).isFalse();
		assertThat(verification.canResendAt(NOW.plusSeconds(59))).isFalse();
		assertThat(verification.canResendAt(NOW.plusSeconds(60))).isTrue();
		assertThat(verification.canResendAt(NOW.plusSeconds(61))).isTrue();
	}

	@Test
	@DisplayName("#279-S3 다시 보내면 같은 줄(id)에 새 번호·새 발송 식별자·새 만료를 두고 틀린 횟수를 0으로 되돌린다")
	void resendReplacesCode() {
		EmailVerification stored = EmailVerification.restore(7L, "a@test.com", VerificationPurpose.SIGNUP, "old-hash",
				"send-1", 3, NOW.plusSeconds(600), NOW);
		Instant later = NOW.plusSeconds(60);

		EmailVerification next = stored.resend("new-hash", later);

		assertThat(next.getId()).isEqualTo(7L);
		assertThat(next.getEmail()).isEqualTo("a@test.com");
		assertThat(next.getPurpose()).isEqualTo(VerificationPurpose.SIGNUP);
		assertThat(next.getCodeHash()).isEqualTo("new-hash");
		assertThat(next.getSendId()).isNotBlank().isNotEqualTo("send-1");
		assertThat(next.getFailedAttempts()).isZero();
		assertThat(next.getSentAt()).isEqualTo(later);
		assertThat(next.getCodeExpiresAt()).isEqualTo(later.plus(EmailVerification.CODE_VALID_FOR));
	}

	@Test
	@DisplayName("#279-S3 직전 번호와 같은 번호로는 다시 보낼 수 없다 (이전 번호 무효)")
	void resendRejectsSameCode() {
		EmailVerification stored = EmailVerification.restore(7L, "a@test.com", VerificationPurpose.SIGNUP, "hash",
				"send-1", 0, NOW.plusSeconds(600), NOW);

		assertThatThrownBy(() -> stored.resend("hash", NOW.plusSeconds(60)))
				.isInstanceOf(InvalidEmailVerificationException.class);
	}

	@Test
	@DisplayName("#279-S1 이메일·용도·번호 해시·보낸 시각이 없으면 회원 도메인 예외로 거절한다")
	void sendRejectsMissingValues() {
		for (String email : new String[] { null, "", "  " }) {
			assertThatThrownBy(() -> EmailVerification.send(email, VerificationPurpose.SIGNUP, "hash", NOW))
					.isInstanceOf(InvalidEmailVerificationException.class);
		}
		assertThatThrownBy(() -> EmailVerification.send("a@test.com", null, "hash", NOW))
				.isInstanceOf(InvalidEmailVerificationException.class);
		for (String codeHash : new String[] { null, "", "  " }) {
			assertThatThrownBy(() -> EmailVerification.send("a@test.com", VerificationPurpose.SIGNUP, codeHash, NOW))
					.isInstanceOf(InvalidEmailVerificationException.class);
		}
		assertThatThrownBy(() -> EmailVerification.send("a@test.com", VerificationPurpose.SIGNUP, "hash", null))
				.isInstanceOf(InvalidEmailVerificationException.class);
	}

	@Test
	@DisplayName("#279-S3 DB에서 읽은 값은 그대로 되살리고, 다시 받기 시각도 저장된 보낸 시각으로 계산한다")
	void restoreKeepsStoredValues() {
		Instant sentAt = NOW.minusSeconds(30);
		EmailVerification verification = EmailVerification.restore(7L, "a@test.com", VerificationPurpose.PASSWORD_RESET,
				"hash", "send-1", 3, sentAt.plusSeconds(600), sentAt);

		assertThat(verification.getId()).isEqualTo(7L);
		assertThat(verification.getPurpose()).isEqualTo(VerificationPurpose.PASSWORD_RESET);
		assertThat(verification.getSendId()).isEqualTo("send-1");
		assertThat(verification.getFailedAttempts()).isEqualTo(3);
		assertThat(verification.getCodeExpiresAt()).isEqualTo(sentAt.plusSeconds(600));
		assertThat(verification.canResendAt(NOW)).isFalse();
		assertThat(verification.canResendAt(NOW.plusSeconds(30))).isTrue();
	}

	@Test
	@DisplayName("#279-S3 되살릴 때도 만들 때와 같은 규칙을 검사한다 (음수 횟수, 빈 발송 식별자, 빈 시각)")
	void restoreRejectsBrokenValues() {
		Instant expires = NOW.plusSeconds(600);
		assertThatThrownBy(() -> EmailVerification.restore(7L, "a@test.com", VerificationPurpose.SIGNUP, "hash",
				"send-1", -1, expires, NOW)).isInstanceOf(InvalidEmailVerificationException.class);
		assertThatThrownBy(() -> EmailVerification.restore(7L, "a@test.com", VerificationPurpose.SIGNUP, "hash",
				" ", 0, expires, NOW)).isInstanceOf(InvalidEmailVerificationException.class);
		assertThatThrownBy(() -> EmailVerification.restore(7L, "a@test.com", VerificationPurpose.SIGNUP, "hash",
				"send-1", 0, expires, null)).isInstanceOf(InvalidEmailVerificationException.class);
		assertThatThrownBy(() -> EmailVerification.restore(7L, "a@test.com", VerificationPurpose.SIGNUP, "hash",
				"send-1", 0, null, NOW)).isInstanceOf(InvalidEmailVerificationException.class);
		assertThatThrownBy(() -> EmailVerification.restore(7L, "", VerificationPurpose.SIGNUP, "hash",
				"send-1", 0, expires, NOW)).isInstanceOf(InvalidEmailVerificationException.class);
	}
}
