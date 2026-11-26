package com.pawmento.member.adapter.out.persistence;

import java.time.Instant;
import java.util.Optional;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.pawmento.member.application.exception.EmailVerificationConflictException;
import com.pawmento.member.application.port.out.DeleteEmailVerificationPort;
import com.pawmento.member.application.port.out.LoadEmailVerificationPort;
import com.pawmento.member.application.port.out.ResendEmailVerificationPort;
import com.pawmento.member.application.port.out.SaveEmailVerificationPort;
import com.pawmento.member.domain.EmailVerification;
import com.pawmento.member.domain.VerificationPurpose;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
class EmailVerificationPersistenceAdapter implements LoadEmailVerificationPort, SaveEmailVerificationPort,
        ResendEmailVerificationPort, DeleteEmailVerificationPort {

    private final EmailVerificationJpaRepository repository;

    @Override
    public Optional<EmailVerification> load(String email, VerificationPurpose purpose) {
        return repository.findByEmailAndPurpose(email, purpose).map(EmailVerificationJpaEntity::toDomain);
    }

    @Override
    public EmailVerification save(EmailVerification verification) {
        try{
            return repository.saveAndFlush(EmailVerificationJpaEntity.from(verification)).toDomain();
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
        return repository.resend(fresh.getEmail(), fresh.getPurpose(), fresh.getCodeHash(),
                fresh.getCodeExpiresAt(), fresh.getSentAt(), lastSentAtOrBefore) == 1;
    }

    @Override
    @Transactional
    public boolean delete(Long id, String codeHash) {
        return repository.deleteSent(id, codeHash) == 1;
    }
}
