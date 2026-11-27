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
			set v.codeHash = :codeHash, v.sendId = :sendId, v.failedAttempts = 0,
				v.codeExpiresAt = :codeExpiresAt, v.sentAt = :sentAt,
                v.verifiedAt = null, v.tokenHash = null, v.tokenExpiresAt = null
            where v.email = :email and v.purpose = :purpose and v.sentAt <= :lastSentAtOrBefore
				and v.codeHash <> :codeHash
			""")
	int resend(@Param("email") String email, @Param("purpose") VerificationPurpose purpose,
			@Param("codeHash") String codeHash, @Param("sendId") String sendId,
			@Param("codeExpiresAt") Instant codeExpiresAt, @Param("sentAt") Instant sentAt,
			@Param("lastSentAtOrBefore") Instant lastSentAtOrBefore);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("delete from EmailVerificationJpaEntity v where v.id = :id and v.sendId = :sendId")
	int deleteSent(@Param("id") Long id, @Param("sendId") String sendId);

    // 틀린 횟수를 한 문장으로 올린다. 동시에 여러 번 틀려도 max에서 멈춘다 (#279-S6)
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			update EmailVerificationJpaEntity v
			set v.failedAttempts = v.failedAttempts + 1
			where v.id = :id and v.sendId = :sendId and v.verifiedAt is null and v.failedAttempts < :max
			""")
	int recordFailure(@Param("id") Long id, @Param("sendId") String sendId, @Param("max") int max);

	// 확인할 수 있는 조건을 저장하는 순간에 다시 본다. 그 사이 다시 받기·5번째 실패·다른 확인이 있었으면 0줄이다
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			update EmailVerificationJpaEntity v
			set v.verifiedAt = :verifiedAt, v.tokenHash = :tokenHash, v.tokenExpiresAt = :tokenExpiresAt
			where v.id = :id and v.sendId = :sendId and v.verifiedAt is null
				and v.failedAttempts < :max and v.codeExpiresAt > :verifiedAt
			""")
	int confirm(@Param("id") Long id, @Param("sendId") String sendId, @Param("verifiedAt") Instant verifiedAt,
			@Param("tokenHash") String tokenHash, @Param("tokenExpiresAt") Instant tokenExpiresAt,
			@Param("max") int max);
}