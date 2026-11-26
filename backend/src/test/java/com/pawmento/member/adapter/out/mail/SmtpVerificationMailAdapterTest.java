package com.pawmento.member.adapter.out.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import com.pawmento.member.application.exception.VerificationMailUnavailableException;
import com.pawmento.member.domain.VerificationPurpose;

class SmtpVerificationMailAdapterTest {

	// 한국 시각 18:10
	private static final Instant EXPIRES_AT = Instant.parse("2026-11-26T09:10:00Z");

	private final JavaMailSender mailSender = mock(JavaMailSender.class);

	private final SmtpVerificationMailAdapter adapter = new SmtpVerificationMailAdapter(mailSender, "team@pawmento.dev");

	@Test
	@DisplayName("#279-S1 받는 사람에게 용도가 적힌 제목과, 번호·한국 시각 마감이 담긴 본문으로 보낸다")
	void sendsCodeMail() {
		adapter.send("a@test.com", VerificationPurpose.SIGNUP, "048213", EXPIRES_AT);

		ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
		verify(mailSender).send(sent.capture());
		SimpleMailMessage message = sent.getValue();
		assertThat(message.getFrom()).isEqualTo("team@pawmento.dev");
		assertThat(message.getTo()).containsExactly("a@test.com");
		assertThat(message.getSubject()).isEqualTo("[PawMento] 회원가입 인증번호");
		assertThat(message.getText()).contains("인증번호: 048213").contains("18:10까지");
	}

	@Test
	@DisplayName("#279-S1 용도마다 제목이 다르다 (#280 비밀번호 재설정·설정에서 쓴다)")
	void subjectFollowsPurpose() {
		adapter.send("a@test.com", VerificationPurpose.PASSWORD_RESET, "111111", EXPIRES_AT);
		adapter.send("a@test.com", VerificationPurpose.SET_PASSWORD, "222222", EXPIRES_AT);

		ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
		verify(mailSender, times(2)).send(sent.capture());
		assertThat(sent.getAllValues()).extracting(SimpleMailMessage::getSubject)
				.containsExactly("[PawMento] 비밀번호 재설정 인증번호", "[PawMento] 비밀번호 설정 인증번호");
	}

	@Test
	@DisplayName("#279-S4 메일 서버 연결·인증에 실패하면 503용 예외로 바꾸고 원인을 남긴다")
	void mailFailureBecomesUnavailable() {
		MailSendException connection = new MailSendException("connection refused");
		doThrow(connection).when(mailSender).send(any(SimpleMailMessage.class));

		assertThatThrownBy(() -> adapter.send("a@test.com", VerificationPurpose.SIGNUP, "123456", EXPIRES_AT))
				.isInstanceOf(VerificationMailUnavailableException.class)
				.hasCause(connection);

		doThrow(new MailAuthenticationException("bad password")).when(mailSender).send(any(SimpleMailMessage.class));
		assertThatThrownBy(() -> adapter.send("a@test.com", VerificationPurpose.SIGNUP, "123456", EXPIRES_AT))
				.isInstanceOf(VerificationMailUnavailableException.class);
	}
}
