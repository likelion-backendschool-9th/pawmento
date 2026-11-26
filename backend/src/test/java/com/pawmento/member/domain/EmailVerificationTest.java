package com.pawmento.member.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EmailVerificationTest {
	private static final Instant NOW = Instant.parse("2026-11-26T09:00:00Z");

	@Test
	@DisplayName("#279-S1 보내면 이메일을 소문자로 정리하고, 번호는 10분 뒤 만료되며 틀린 횟수는 0이다")
	void sendNormalizesEmailAndSetsExpiry() {
		EmailVerification verification = EmailVerification.send(" A@Test.COM ", VerificationPurpose.SIGNUP, "hash", NOW);

		assertThat(verification.getId()).isNull();
		assertThat(verification.getEmail()).isEqualTo("a@test.com");
		assertThat(verification.getPurpose()).isEqualTo(VerificationPurpose.SIGNUP);
		assertThat(verification.getCodeHash()).isEqualTo("hash");
		assertThat(verification.getFailedAttempts()).isZero();
		assertThat(verification.getSentAt()).isEqualTo(NOW);
		assertThat(verification.getCodeExpiresAt()).isEqualTo(Instant.parse("2026-11-26T09:10:00Z"));
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
	@DisplayName("#279-S1 이메일·용도·번호 해시가 없으면 만들지 않는다")
	void sendRejectsMissingValues() {
		for (String email : new String[] { null, "", "  " }) {
			assertThatThrownBy(() -> EmailVerification.send(email, VerificationPurpose.SIGNUP, "hash", NOW))
					.isInstanceOf(IllegalArgumentException.class);
		}
		assertThatThrownBy(() -> EmailVerification.send("a@test.com", null, "hash", NOW))
				.isInstanceOf(IllegalArgumentException.class);
		for (String codeHash : new String[] { null, "", "  " }) {
			assertThatThrownBy(() -> EmailVerification.send("a@test.com", VerificationPurpose.SIGNUP, codeHash, NOW))
					.isInstanceOf(IllegalArgumentException.class);
		}
	}

	@Test
	@DisplayName("#279-S3 DB에서 읽은 값은 그대로 되살리고, 다시 받기 시각도 저장된 보낸 시각으로 계산한다")
	void restoreKeepsStoredValues() {
		Instant sentAt = NOW.minusSeconds(30);
		EmailVerification verification = EmailVerification.restore(7L, "a@test.com", VerificationPurpose.PASSWORD_RESET,
				"hash", 3, sentAt.plusSeconds(600), sentAt);

		assertThat(verification.getId()).isEqualTo(7L);
		assertThat(verification.getPurpose()).isEqualTo(VerificationPurpose.PASSWORD_RESET);
		assertThat(verification.getFailedAttempts()).isEqualTo(3);
		assertThat(verification.getCodeExpiresAt()).isEqualTo(sentAt.plusSeconds(600));
		assertThat(verification.canResendAt(NOW)).isFalse();
		assertThat(verification.canResendAt(NOW.plusSeconds(30))).isTrue();
	}
}
