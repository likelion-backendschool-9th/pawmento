package com.pawmento.member.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.pawmento.member.application.exception.DuplicateEmailException;
import com.pawmento.member.application.exception.EmailVerificationConflictException;
import com.pawmento.member.application.exception.UnsupportedVerificationPurposeException;
import com.pawmento.member.application.exception.VerificationMailUnavailableException;
import com.pawmento.member.application.port.in.SendEmailVerificationCommand;
import com.pawmento.member.application.port.in.SendEmailVerificationResult;
import com.pawmento.member.application.port.out.DeleteEmailVerificationPort;
import com.pawmento.member.application.port.out.ExistsMemberPort;
import com.pawmento.member.application.port.out.LoadEmailVerificationPort;
import com.pawmento.member.application.port.out.ResendEmailVerificationPort;
import com.pawmento.member.application.port.out.SaveEmailVerificationPort;
import com.pawmento.member.application.port.out.SendVerificationMailPort;
import com.pawmento.member.domain.EmailVerification;
import com.pawmento.member.domain.VerificationPurpose;

class SendEmailVerificationServiceTest {
	private static final Instant NOW = Instant.parse("2026-11-26T09:00:00Z");
	private static final String EMAIL = "a@test.com";

	private final ExistsMemberPort members = mock(ExistsMemberPort.class);
	private final LoadEmailVerificationPort loadPort = mock(LoadEmailVerificationPort.class);
	private final SaveEmailVerificationPort savePort = mock(SaveEmailVerificationPort.class);
	private final ResendEmailVerificationPort resendPort = mock(ResendEmailVerificationPort.class);
	private final DeleteEmailVerificationPort deletePort = mock(DeleteEmailVerificationPort.class);
	private final SendVerificationMailPort mailPort = mock(SendVerificationMailPort.class);

	private VerificationCodes codes;

	private SendEmailVerificationService service(Integer... drawn) {
		codes = new VerificationCodes("test-secret", scripted(drawn));
		return new SendEmailVerificationService(members, loadPort, savePort, resendPort, deletePort, mailPort, codes,
				Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private EmailVerification stored(String code, Instant sentAt) {
		return EmailVerification.restore(7L, EMAIL, VerificationPurpose.SIGNUP, codes.hash(code), "send-old", 2,
				sentAt.plus(EmailVerification.CODE_VALID_FOR), sentAt, null, null, null);
	}

	private static SendEmailVerificationCommand signup(String email) {
		return new SendEmailVerificationCommand(email, "SIGNUP");
	}

	@Test
	@DisplayName("#279-S1 처음 보내면 소문자 이메일로 번호 해시를 저장하고, 원문 번호는 메일로만 보낸다")
	void firstSendSavesHashAndMailsCode() {
		SendEmailVerificationService service = service(123_456);
		when(loadPort.load(EMAIL, VerificationPurpose.SIGNUP)).thenReturn(Optional.empty());
		when(savePort.save(any())).thenAnswer(call -> {
			EmailVerification v = call.getArgument(0);
			return EmailVerification.restore(1L, v.getEmail(), v.getPurpose(), v.getCodeHash(), v.getSendId(),
					v.getFailedAttempts(), v.getCodeExpiresAt(), v.getSentAt(), null, null, null);
		});

		SendEmailVerificationResult result = service.send(signup(" A@Test.com "));

		ArgumentCaptor<EmailVerification> saved = ArgumentCaptor.forClass(EmailVerification.class);
		verify(savePort).save(saved.capture());
		assertThat(saved.getValue().getEmail()).isEqualTo(EMAIL);
		assertThat(saved.getValue().getCodeHash()).isEqualTo(codes.hash("123456")).isNotEqualTo("123456");
		verify(members).existsByEmail(EMAIL);
		verify(mailPort).send(EMAIL, VerificationPurpose.SIGNUP, "123456", NOW.plus(EmailVerification.CODE_VALID_FOR));
		verify(resendPort, never()).resend(any(), any());
		assertThat(result.email()).isEqualTo(EMAIL);
		assertThat(result.expiresAt()).isEqualTo(NOW.plus(EmailVerification.CODE_VALID_FOR));
		assertThat(result.retryAfter()).isEqualTo(NOW.plus(EmailVerification.RESEND_AFTER));
		// 화면이 기기 시계와 상관없이 카운트다운하도록 남은 초도 준다 (#279-S12)
		assertThat(result.expiresInSeconds()).isEqualTo(EmailVerification.CODE_VALID_FOR.toSeconds());
		assertThat(result.retryAfterSeconds()).isEqualTo(EmailVerification.RESEND_AFTER.toSeconds());
	}

	@Test
	@DisplayName("#279-S2 이미 가입된 이메일이면 409이고 저장하지도 메일을 보내지도 않는다")
	void registeredEmailIsRejected() {
		SendEmailVerificationService service = service(123_456);
		when(members.existsByEmail(EMAIL)).thenReturn(true);

		assertThatThrownBy(() -> service.send(signup(EMAIL))).isInstanceOf(DuplicateEmailException.class);

		verifyNoInteractions(loadPort, savePort, resendPort, deletePort, mailPort);
	}

	@Test
	@DisplayName("#279-S1 이번에는 가입 인증만 받는다. 다른 용도나 빈 용도는 400이고 아무것도 하지 않는다")
	void otherPurposesAreRejected() {
		SendEmailVerificationService service = service(123_456);

		for (String purpose : new String[] { "PASSWORD_RESET", "SET_PASSWORD", "signup", "", null }) {
			assertThatThrownBy(() -> service.send(new SendEmailVerificationCommand(EMAIL, purpose)))
					.isInstanceOf(UnsupportedVerificationPurposeException.class);
		}
		verifyNoInteractions(members, loadPort, savePort, resendPort, deletePort, mailPort);
	}

	@Test
	@DisplayName("#279-S3 보낸 지 60초가 안 됐으면 409이고 번호를 바꾸지도 메일을 보내지도 않는다")
	void resendWithinIntervalIsRejected() {
		SendEmailVerificationService service = service(654_321);
		when(loadPort.load(EMAIL, VerificationPurpose.SIGNUP)).thenReturn(Optional.of(stored("123456", NOW.minusSeconds(59))));

		assertThatThrownBy(() -> service.send(signup(EMAIL))).isInstanceOf(EmailVerificationConflictException.class);

		verify(resendPort, never()).resend(any(), any());
		verify(savePort, never()).save(any());
		verifyNoInteractions(mailPort);
	}

	@Test
	@DisplayName("#279-S3 60초가 지났으면 같은 줄에 직전과 다른 번호를 저장하고 그 번호를 보낸다")
	void resendAfterIntervalReplacesCode() {
		// 직전 번호 123456이 또 뽑히면 다시 뽑아 654321을 쓴다
		SendEmailVerificationService service = service(123_456, 123_456, 654_321);
		when(loadPort.load(EMAIL, VerificationPurpose.SIGNUP)).thenReturn(Optional.of(stored("123456", NOW.minusSeconds(60))));
		when(resendPort.resend(any(), any())).thenReturn(true);

		SendEmailVerificationResult result = service.send(signup(EMAIL));

		ArgumentCaptor<EmailVerification> fresh = ArgumentCaptor.forClass(EmailVerification.class);
		verify(resendPort).resend(fresh.capture(), eq(NOW.minus(EmailVerification.RESEND_AFTER)));
		assertThat(fresh.getValue().getId()).isEqualTo(7L);
		assertThat(fresh.getValue().getCodeHash()).isEqualTo(codes.hash("654321"));
		assertThat(fresh.getValue().getSendId()).isNotEqualTo("send-old");
		assertThat(fresh.getValue().getFailedAttempts()).isZero();
		verify(mailPort).send(EMAIL, VerificationPurpose.SIGNUP, "654321", NOW.plus(EmailVerification.CODE_VALID_FOR));
		verify(savePort, never()).save(any());
		assertThat(result.retryAfter()).isEqualTo(NOW.plus(EmailVerification.RESEND_AFTER));
		assertThat(result.expiresInSeconds()).isEqualTo(300);
		assertThat(result.retryAfterSeconds()).isEqualTo(60);
	}

	@Test
	@DisplayName("#279-S3 다시 받기가 동시에 와서 다른 요청이 먼저 바꿨으면 409이고 메일을 보내지 않는다")
	void lostResendRaceIsRejected() {
		SendEmailVerificationService service = service(654_321);
		when(loadPort.load(EMAIL, VerificationPurpose.SIGNUP)).thenReturn(Optional.of(stored("123456", NOW.minusSeconds(60))));
		when(resendPort.resend(any(), any())).thenReturn(false);

		assertThatThrownBy(() -> service.send(signup(EMAIL))).isInstanceOf(EmailVerificationConflictException.class);

		verifyNoInteractions(mailPort, deletePort);
	}

	@Test
	@DisplayName("#279-S3 처음 보내기가 동시에 와서 저장이 유일 제약에 걸리면 409이고 메일을 보내지 않는다")
	void lostFirstSendRaceIsRejected() {
		SendEmailVerificationService service = service(123_456);
		when(loadPort.load(EMAIL, VerificationPurpose.SIGNUP)).thenReturn(Optional.empty());
		when(savePort.save(any())).thenThrow(new EmailVerificationConflictException());

		assertThatThrownBy(() -> service.send(signup(EMAIL))).isInstanceOf(EmailVerificationConflictException.class);

		verifyNoInteractions(mailPort, deletePort);
	}

	@Test
	@DisplayName("#279-S4 메일을 보내지 못하면 방금 저장한 발송만 지우고 503 예외를 그대로 던진다")
	void mailFailureDeletesThisSend() {
		SendEmailVerificationService service = service(123_456);
		when(loadPort.load(EMAIL, VerificationPurpose.SIGNUP)).thenReturn(Optional.empty());
		when(savePort.save(any())).thenAnswer(call -> {
			EmailVerification v = call.getArgument(0);
			return EmailVerification.restore(1L, v.getEmail(), v.getPurpose(), v.getCodeHash(), v.getSendId(),
					v.getFailedAttempts(), v.getCodeExpiresAt(), v.getSentAt(), null, null, null);
		});
		VerificationMailUnavailableException failure = new VerificationMailUnavailableException(new RuntimeException("smtp"));
		doThrow(failure).when(mailPort).send(anyString(), any(), anyString(), any());

		assertThatThrownBy(() -> service.send(signup(EMAIL))).isSameAs(failure);

		ArgumentCaptor<EmailVerification> saved = ArgumentCaptor.forClass(EmailVerification.class);
		verify(savePort).save(saved.capture());
		verify(deletePort).delete(1L, saved.getValue().getSendId());
	}

	@Test
	@DisplayName("#279-S4 다시 받기에서 메일을 보내지 못하면 새 발송의 식별자로 지운다")
	void mailFailureOnResendDeletesNewSend() {
		SendEmailVerificationService service = service(654_321);
		when(loadPort.load(EMAIL, VerificationPurpose.SIGNUP)).thenReturn(Optional.of(stored("123456", NOW.minusSeconds(60))));
		when(resendPort.resend(any(), any())).thenReturn(true);
		doThrow(new VerificationMailUnavailableException(new RuntimeException("smtp")))
				.when(mailPort).send(anyString(), any(), anyString(), any());

		assertThatThrownBy(() -> service.send(signup(EMAIL))).isInstanceOf(VerificationMailUnavailableException.class);

		ArgumentCaptor<EmailVerification> fresh = ArgumentCaptor.forClass(EmailVerification.class);
		verify(resendPort).resend(fresh.capture(), any());
		verify(deletePort).delete(7L, fresh.getValue().getSendId());
		verify(deletePort, never()).delete(7L, "send-old");
	}

	// 정해 둔 순서대로 번호를 뽑는 가짜 난수
	private static RandomGenerator scripted(Integer... values) {
		Iterator<Integer> next = List.of(values).iterator();
		return new RandomGenerator() {
			@Override
			public long nextLong() {
				throw new UnsupportedOperationException();
			}

			@Override
			public int nextInt(int bound) {
				return next.next();
			}
		};
	}
}
