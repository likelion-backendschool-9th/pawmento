package com.pawmento.member.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.pawmento.member.application.exception.DuplicateEmailException;
import com.pawmento.member.application.exception.EmailVerificationConflictException;
import com.pawmento.member.application.exception.VerificationMailUnavailableException;
import com.pawmento.member.application.port.in.SendEmailVerificationCommand;
import com.pawmento.member.application.port.in.SendEmailVerificationResult;
import com.pawmento.member.application.port.in.SendEmailVerificationUseCase;
import com.pawmento.member.application.port.out.DeleteEmailVerificationPort;
import com.pawmento.member.application.port.out.ExistsMemberPort;
import com.pawmento.member.application.port.out.LoadEmailVerificationPort;
import com.pawmento.member.application.port.out.ResendEmailVerificationPort;
import com.pawmento.member.application.port.out.SaveEmailVerificationPort;
import com.pawmento.member.application.port.out.SendVerificationMailPort;
import com.pawmento.member.domain.EmailVerification;
import com.pawmento.member.domain.Member;
import com.pawmento.member.domain.VerificationPurpose;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SendEmailVerificationService implements SendEmailVerificationUseCase {

    private final ExistsMemberPort existsMemberPort;
    private final LoadEmailVerificationPort loadPort;
    private final SaveEmailVerificationPort savePort;
    private final ResendEmailVerificationPort resendPort;
    private final DeleteEmailVerificationPort deletePort;
    private final SendVerificationMailPort mailPort;
    private final VerificationCodes codes;
    private final Clock clock;
    
    @Override
    public SendEmailVerificationResult send(SendEmailVerificationCommand command) {
        VerificationPurpose purpose = VerificationPurposes.open(command.purpose());
        String email = Member.normalizeEmail(command.email());
        if (existsMemberPort.existsByEmail(email)) {
            throw new DuplicateEmailException();
        }

        Instant now = Instant.now(clock);
		Optional<EmailVerification> previous = loadPort.load(email, purpose);

		String code;
		EmailVerification sent;
		if (previous.isEmpty()) {
			// 처음 보낸다. 동시에 처음 보내기가 두 번 오면 유일 제약으로 한쪽이 409다
			code = codes.newCode();
			sent = savePort.save(EmailVerification.send(email, purpose, codes.hash(code), now));
		}
		else {
			// #279-S3 60초 안이면 거절하고, 지났으면 직전 번호와 다른 번호로 바꾼다
			EmailVerification last = previous.get();
			if (!last.canResendAt(now)) {
				throw new EmailVerificationConflictException();
			}
			code = codes.newCodeOtherThan(last.getCodeHash());
			sent = last.resend(codes.hash(code), now);
			if (!resendPort.resend(sent, now.minus(EmailVerification.RESEND_AFTER))) {
				throw new EmailVerificationConflictException();
			}
		}

		try {
			mailPort.send(email, purpose, code, sent.getCodeExpiresAt());
		}
		catch (VerificationMailUnavailableException e) {
                // #279-S4 보내지 못한 번호는 지워서 바로 다시 요청할 수 있게 한다
                deletePort.delete(sent.getId(), sent.getSendId());
                throw e;
            }
            return new SendEmailVerificationResult(email, sent.getCodeExpiresAt(), sent.resendableAt());
        }
    }
