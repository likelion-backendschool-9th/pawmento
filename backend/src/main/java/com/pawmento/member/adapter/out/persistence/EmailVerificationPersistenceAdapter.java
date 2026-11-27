package com.pawmento.member.adapter.out.persistence;

import java.time.Instant;
import java.util.Optional;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import com.pawmento.member.application.exception.EmailVerificationConflictException;
import com.pawmento.member.application.exception.StoredEmailVerificationInvalidException;
import com.pawmento.member.application.port.out.DeleteEmailVerificationPort;
import com.pawmento.member.application.port.out.LoadEmailVerificationPort;
import com.pawmento.member.application.port.out.ResendEmailVerificationPort;
import com.pawmento.member.application.port.out.SaveEmailVerificationPort;
import com.pawmento.member.application.port.out.ConfirmEmailVerificationPort;
import com.pawmento.member.application.port.out.RecordEmailVerificationFailurePort;
import com.pawmento.member.application.port.out.UseEmailVerificationTokenPort;
import com.pawmento.member.domain.EmailVerification;
import com.pawmento.member.domain.VerificationPurpose;
import com.pawmento.member.domain.exception.InvalidEmailVerificationException;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
class EmailVerificationPersistenceAdapter implements LoadEmailVerificationPort, SaveEmailVerificationPort,
        ResendEmailVerificationPort, DeleteEmailVerificationPort, RecordEmailVerificationFailurePort,
        ConfirmEmailVerificationPort, UseEmailVerificationTokenPort {

    private final EmailVerificationJpaRepository repository;

    @Override
    public Optional<EmailVerification> load(String email, VerificationPurpose purpose) {
        return repository.findByEmailAndPurpose(email, purpose).map(EmailVerificationPersistenceAdapter::toDomain);
    }

    @Override
    public EmailVerification save(EmailVerification verification) {
        try{
            return toDomain(repository.saveAndFlush(EmailVerificationJpaEntity.from(verification)));
        }
        catch (DataIntegrityViolationException e) {
            if (e.getCause() instanceof ConstraintViolationException violation && EmailVerificationJpaEntity.EMAIL_PURPOSE_UNIQUE_CONSTRAINT
                    .equalsIgnoreCase(violation.getConstraintName())) {
                throw new EmailVerificationConflictException();
            }
            throw e;
        }
    }

    @Override
    @Transactional
    public boolean resend(EmailVerification fresh, Instant lastSentAtOrBefore) {
        return repository.resend(fresh.getEmail(), fresh.getPurpose(), fresh.getCodeHash(), fresh.getSendId(),
                fresh.getCodeExpiresAt(), fresh.getSentAt(), lastSentAtOrBefore) == 1;
    }

    @Override
    @Transactional
    public boolean delete(Long id, String sendId) {
        return repository.deleteSent(id, sendId) == 1;
    }

    // 틀린 횟수는 400 응답과 함께 롤백되면 안 된다. 바깥에 트랜잭션이 있어도 따로 열어 바로 커밋한다 (#279-S6)
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recordFailure(Long id, String sendId) {
        return repository.recordFailure(id, sendId, EmailVerification.MAX_FAILED_ATTEMPTS) == 1;
    }

    // 먼저 줄을 잠그고, 잠금을 얻은 뒤에 확인 문장이 DB 시각으로 만료를 본다.
    // UPDATE 하나로만 하면 조건을 먼저 본 뒤 잠금을 기다리고, 상대가 줄을 바꾸지 않고 놓으면 조건을 다시 보지 않는다 (#279-S5)
    @Override
    @Transactional
    public boolean confirm(EmailVerification confirmed) {
        if (repository.findForConfirm(confirmed.getId()).isEmpty()) {
            return false;
        }
        return repository.confirm(confirmed.getId(), confirmed.getSendId(), confirmed.getVerifiedAt(),
                confirmed.getTokenHash(), confirmed.getTokenExpiresAt(), EmailVerification.MAX_FAILED_ATTEMPTS) == 1;
    }

    // 가입 트랜잭션 안에서만 부른다. 가입이 롤백되면 지운 줄도 되살아난다 (#279-S10)
	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public boolean use(String email, VerificationPurpose purpose, String tokenHash, Instant now) {
		return repository.useToken(email, purpose, tokenHash, now) == 1;
	}

    // 저장된 값이 규칙에 맞지 않으면 요청 오류(400)가 아니라 서버 데이터 문제(500)로 바꾼다
    private static EmailVerification toDomain(EmailVerificationJpaEntity entity) {
        try {
            return entity.toDomain();
        }
        catch (InvalidEmailVerificationException e) {
            throw new StoredEmailVerificationInvalidException(e);
        }
    }
}
