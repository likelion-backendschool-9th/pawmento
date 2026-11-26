package com.pawmento.member.adapter.out.persistence;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.pawmento.member.domain.VerificationPurpose;

interface EmailVerificationJpaRepository extends JpaRepository<EmailVerificationJpaEntity, Long> {
	Optional<EmailVerificationJpaEntity> findByEmailAndPurpose(String email, VerificationPurpose purpose);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update EmailVerificationJpaEntity v
			set v.codeHash = :codeHash, v.failedAttempts = 0, v.codeExpiresAt = :codeExpiresAt, v.sentAt = :sentAt
			where v.email = :email and v.purpose = :purpose and v.sentAt <= :lastSentAtOrBefore
            """)
    int resend(@Param("email") String email, @Param("purpose") VerificationPurpose purpose,
            @Param("codeHash") String codeHash, @Param("codeExpiresAt") Instant codeExpiresAt,
            @Param("sentAt") Instant sentAt, @Param("lastSentAtOrBefore") Instant lastSentAtOrBefore);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from EmailVerificationJpaEntity v where v.id = :id and v.codeHash = :codeHash")
    int deleteSent(@Param("id") Long id, @Param("codeHash") String codeHash);
}