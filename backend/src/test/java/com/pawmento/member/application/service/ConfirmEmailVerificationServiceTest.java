package com.pawmento.member.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.pawmento.member.application.exception.InvalidVerificationCodeException;
import com.pawmento.member.application.exception.UnsupportedVerificationPurposeException;
import com.pawmento.member.application.port.in.ConfirmEmailVerificationCommand;
import com.pawmento.member.application.port.in.ConfirmEmailVerificationResult;
import com.pawmento.member.application.port.out.ConfirmEmailVerificationPort;
import com.pawmento.member.application.port.out.LoadEmailVerificationPort;
import com.pawmento.member.application.port.out.RecordEmailVerificationFailurePort;
import com.pawmento.member.domain.EmailVerification;
import com.pawmento.member.domain.VerificationPurpose;

class ConfirmEmailVerificationServiceTest {
	private static final Instant SENT_AT = Instant.parse("2026-11-26T09:00:00Z");
	private static final Instant NOW = SENT_AT.plusSeconds(120);
	private static final String EMAIL = "a@test.com";

	private final LoadEmailVerificationPort loadPort = mock(LoadEmailVerificationPort.class);
	private final RecordEmailVerificationFailurePort failurePort = mock(RecordEmailVerificationFailurePort.class);
	private final ConfirmEmailVerificationPort confirmPort = mock(ConfirmEmailVerificationPort.class);
	private final VerificationCodes codes = new VerificationCodes("test-secret");

	private final ConfirmEmailVerificationService service = new ConfirmEmailVerificationService(loadPort, failurePort,
			confirmPort, codes, Clock.fixed(NOW, ZoneOffset.UTC));

	private EmailVerification stored(String code, int failedAttempts) {
		return EmailVerification.restore(7L, EMAIL, VerificationPurpose.SIGNUP, codes.hash(code), "send-1",
				failedAttempts, SENT_AT.plus(EmailVerification.CODE_VALID_FOR), SENT_AT, null, null, null);
	}

	private static ConfirmEmailVerificationCommand confirm(String email, String code) {
		return new ConfirmEmailVerificationCommand(email, "SIGNUP", code);
	}

	@Test
	@DisplayName("#279-S5 맞는 번호면 토큰 원문을 한 번 돌려주고, 저장에는 그 토큰의 해시와 30분 만료를 넘긴다")
	void rightCodeReturnsToken() {
		when(loadPort.load(EMAIL, VerificationPurpose.SIGNUP)).thenReturn(Optional.of(stored("123456", 0)));
		when(confirmPort.confirm(any())).thenReturn(true);

		ConfirmEmailVerificationResult result = service.confirm(confirm(" A@Test.com ", " 123456 "));

		ArgumentCaptor<EmailVerification> confirmed = ArgumentCaptor.forClass(EmailVerification.class);
		verify(confirmPort).confirm(confirmed.capture());
		assertThat(result.verificationToken()).hasSize(43);
		assertThat(confirmed.getValue().getTokenHash()).isEqualTo(codes.hash(result.verificationToken()))
				.isNotEqualTo(result.verificationToken());
		assertThat(confirmed.getValue().getVerifiedAt()).isEqualTo(NOW);
		assertThat(confirmed.getValue().getSendId()).isEqualTo("send-1");
		assertThat(result.expiresAt()).isEqualTo(NOW.plus(EmailVerification.TOKEN_VALID_FOR));
		assertThat(result.expiresInSeconds()).isEqualTo(EmailVerification.TOKEN_VALID_FOR.toSeconds());
		verifyNoInteractions(failurePort);
	}

	@Test
	@DisplayName("#279-S5 틀린 번호면 그 발송의 틀린 횟수를 한 번 세고 400이며, 확인을 저장하지 않는다")
	void wrongCodeRecordsFailure() {
		when(loadPort.load(EMAIL, VerificationPurpose.SIGNUP)).thenReturn(Optional.of(stored("123456", 2)));

		assertThatThrownBy(() -> service.confirm(confirm(EMAIL, "654321")))
				.isInstanceOf(InvalidVerificationCodeException.class)
				.hasMessage("인증번호가 맞지 않거나 만료됐어요.");

		verify(failurePort).recordFailure(7L, "send-1");
		verify(confirmPort, never()).confirm(any());
	}

	@Test
	@DisplayName("#279-S5 번호가 비어 오면 틀린 번호로 센다")
	void missingCodeRecordsFailure() {
		when(loadPort.load(EMAIL, VerificationPurpose.SIGNUP)).thenReturn(Optional.of(stored("123456", 0)));

		assertThatThrownBy(() -> service.confirm(confirm(EMAIL, null)))
				.isInstanceOf(InvalidVerificationCodeException.class);

		verify(failurePort).recordFailure(7L, "send-1");
		verify(confirmPort, never()).confirm(any());
	}

	@Test
	@DisplayName("#279-S5 보낸 적 없는 이메일이면 같은 400이고 아무것도 저장하지 않는다")
	void unknownEmailIsSameError() {
		when(loadPort.load(EMAIL, VerificationPurpose.SIGNUP)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.confirm(confirm(EMAIL, "123456")))
				.isInstanceOf(InvalidVerificationCodeException.class)
				.hasMessage("인증번호가 맞지 않거나 만료됐어요.");

		verifyNoInteractions(failurePort, confirmPort);
	}

	@Test
	@DisplayName("#279-S5 만료된 번호는 맞아도 같은 400이고, 틀린 횟수를 세지 않는다")
	void expiredCodeIsSameError() {
		EmailVerification expired = EmailVerification.restore(7L, EMAIL, VerificationPurpose.SIGNUP,
				codes.hash("123456"), "send-1", 0, NOW, SENT_AT, null, null, null);
		when(loadPort.load(EMAIL, VerificationPurpose.SIGNUP)).thenReturn(Optional.of(expired));

		assertThatThrownBy(() -> service.confirm(confirm(EMAIL, "123456")))
				.isInstanceOf(InvalidVerificationCodeException.class);

		verifyNoInteractions(failurePort, confirmPort);
	}

	@Test
	@DisplayName("#279-S6 다섯 번 틀린 뒤에는 맞는 번호도 400이고, 더 세지 않는다")
	void fiveFailuresBlockRightCode() {
		when(loadPort.load(EMAIL, VerificationPurpose.SIGNUP))
				.thenReturn(Optional.of(stored("123456", EmailVerification.MAX_FAILED_ATTEMPTS)));

		assertThatThrownBy(() -> service.confirm(confirm(EMAIL, "123456")))
				.isInstanceOf(InvalidVerificationCodeException.class);

		verifyNoInteractions(failurePort, confirmPort);
	}

	@Test
	@DisplayName("#279-S7 이미 확인한 인증에 같은 번호를 다시 넣으면 400이다")
	void alreadyConfirmedIsSameError() {
		EmailVerification confirmed = stored("123456", 0).confirm(codes.hash("token"), SENT_AT.plusSeconds(30));
		when(loadPort.load(EMAIL, VerificationPurpose.SIGNUP)).thenReturn(Optional.of(confirmed));

		assertThatThrownBy(() -> service.confirm(confirm(EMAIL, "123456")))
				.isInstanceOf(InvalidVerificationCodeException.class);

		verifyNoInteractions(failurePort, confirmPort);
	}

	@Test
	@DisplayName("#279-S6 읽은 뒤 다시 받기·다섯 번째 실패·다른 확인으로 저장이 거절되면 400이고 토큰을 주지 않는다")
	void lostConfirmRaceIsSameError() {
		when(loadPort.load(EMAIL, VerificationPurpose.SIGNUP)).thenReturn(Optional.of(stored("123456", 4)));
		when(confirmPort.confirm(any())).thenReturn(false);

		assertThatThrownBy(() -> service.confirm(confirm(EMAIL, "123456")))
				.isInstanceOf(InvalidVerificationCodeException.class);

		verifyNoInteractions(failurePort);
	}

	@Test
	@DisplayName("#279-S5 이번에는 가입 인증만 확인한다. 다른 용도는 400이고 아무것도 읽지 않는다")
	void otherPurposesAreRejected() {
		for (String purpose : new String[] { "PASSWORD_RESET", "SET_PASSWORD", "", null }) {
			assertThatThrownBy(() -> service.confirm(new ConfirmEmailVerificationCommand(EMAIL, purpose, "123456")))
					.isInstanceOf(UnsupportedVerificationPurposeException.class);
		}
		verifyNoInteractions(loadPort, failurePort, confirmPort);
	}

	@Test
	@DisplayName("#279-S12 확인 저장에 5초가 걸리면 토큰의 남은 초는 저장이 끝난 시각 기준으로 1795초다")
	void tokenSecondsLeftCountFromAfterStore() {
		MovingClock clock = new MovingClock(NOW);
		ConfirmEmailVerificationService service = new ConfirmEmailVerificationService(loadPort, failurePort,
				confirmPort, codes, clock);
		when(loadPort.load(EMAIL, VerificationPurpose.SIGNUP)).thenReturn(Optional.of(stored("123456", 0)));
		when(confirmPort.confirm(any())).thenAnswer(call -> {
			// 줄 잠금을 기다리는 동안 5초가 흐른다
			clock.advance(Duration.ofSeconds(5));
			return true;
		});

		ConfirmEmailVerificationResult result = service.confirm(confirm(EMAIL, "123456"));

		assertThat(result.expiresAt()).isEqualTo(NOW.plus(EmailVerification.TOKEN_VALID_FOR));
		assertThat(result.expiresInSeconds()).isEqualTo(1795);
	}
}
