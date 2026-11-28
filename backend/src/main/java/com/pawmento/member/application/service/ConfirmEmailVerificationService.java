package com.pawmento.member.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Service;

import com.pawmento.member.application.exception.InvalidVerificationCodeException;
import com.pawmento.member.application.port.in.ConfirmEmailVerificationCommand;
import com.pawmento.member.application.port.in.ConfirmEmailVerificationResult;
import com.pawmento.member.application.port.in.ConfirmEmailVerificationUseCase;
import com.pawmento.member.application.port.out.ConfirmEmailVerificationPort;
import com.pawmento.member.application.port.out.LoadEmailVerificationPort;
import com.pawmento.member.application.port.out.RecordEmailVerificationFailurePort;
import com.pawmento.member.domain.EmailVerification;
import com.pawmento.member.domain.Member;
import com.pawmento.member.domain.VerificationPurpose;

import jakarta.validation.constraints.Email;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ConfirmEmailVerificationService implements ConfirmEmailVerificationUseCase {

    private final LoadEmailVerificationPort loadPort;
    private final RecordEmailVerificationFailurePort failurePort;
    private final ConfirmEmailVerificationPort confirmPort;
    private final VerificationCodes codes;
    private final Clock clock;

    @Override
    public ConfirmEmailVerificationResult confirm(ConfirmEmailVerificationCommand command) {
        VerificationPurpose purpose = VerificationPurposes.open(command.purpose());
        String email = Member.normalizeEmail(command.email());
        Instant now = Instant.now(clock);
        // #279-S5~S7 보낸 적 없거나, 만료·5번 초과·이미 확인이면 모두 같은 400이다
        EmailVerification verification = loadPort.load(email, purpose)
                .filter(current -> current.isConfirmableAt(now))
                .orElseThrow(InvalidVerificationCodeException::new);

        if (command.code() == null || !matches(codes.hash(command.code()), verification.getCodeHash())) {
            // #279-S6 틀린 번호는 한 번 센다. 다섯 번에서 멈추는 것은 저장 쿼리가 지킨다
            failurePort.recordFailure(verification.getId(), verification.getSendId());
            throw new InvalidVerificationCodeException();
        }

        String token = codes.newToken();
        EmailVerification confirmed = verification.confirm(codes.hash(token), now);
        // 읽은 뒤 다시 받기·다섯 번째 실패·다른 확인이 있었으면 저장되지 않는다
        if (!confirmPort.confirm(confirmed)) {
            throw new InvalidVerificationCodeException();
        }
            return new ConfirmEmailVerificationResult(token, confirmed.getTokenExpiresAt(),
                Duration.between(now, confirmed.getTokenExpiresAt()).toSeconds());
    }

    private static boolean matches(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
