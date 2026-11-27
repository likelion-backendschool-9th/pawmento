package com.pawmento.member.adapter.out.mail;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import com.pawmento.member.application.exception.VerificationMailUnavailableException;
import com.pawmento.member.application.port.out.SendVerificationMailPort;
import com.pawmento.member.domain.VerificationPurpose;

// 인증번호를 글 메일로 보낸다. 번호는 메일 본문에만 있고 로그에는 남기지 않는다
@Component
class SmtpVerificationMailAdapter implements SendVerificationMailPort {

	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm")
			.withZone(ZoneId.of("Asia/Seoul"));

	private final JavaMailSender mailSender;

	private final String from;

	SmtpVerificationMailAdapter(JavaMailSender mailSender, @Value("${pawmento.mail.from}") String from) {
		this.mailSender = mailSender;
		this.from = from;
	}

	@Override
	public void send(String email, VerificationPurpose purpose, String code, Instant expiresAt) {
		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(from);
		message.setTo(email);
		message.setSubject("[PawMento] " + title(purpose) + " 인증번호");
		message.setText("""
				PawMento %s 인증번호입니다.

				인증번호: %s

				%s까지 입력해 주세요. 본인이 요청하지 않았다면 이 메일은 무시해도 됩니다.
				""".formatted(title(purpose), code, TIME.format(expiresAt)));
		try {
			mailSender.send(message);
		}
        catch (MailSendException e) {
            if (e.getFailedMessages().isEmpty()) {
                return;
            }
			throw new VerificationMailUnavailableException(e);
		}
        catch (MailException e) {
            throw new VerificationMailUnavailableException(e);
        }
	}

	private static String title(VerificationPurpose purpose) {
		return switch (purpose) {
			case SIGNUP -> "회원가입";
			case PASSWORD_RESET -> "비밀번호 재설정";
			case SET_PASSWORD -> "비밀번호 설정";
		};
	}

}