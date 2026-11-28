package com.pawmento.member.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import com.pawmento.TestcontainersConfiguration;
import com.pawmento.member.application.exception.EmailVerificationConflictException;
import com.pawmento.member.application.exception.StoredEmailVerificationInvalidException;
import com.pawmento.member.domain.EmailVerification;
import com.pawmento.member.domain.VerificationPurpose;

// 시나리오는 docs/member/email-verification.md에 있다. 저장 모양, 이메일·용도 유일 제약, 다시 받기·지우기의 조건을
// 진짜 PostgreSQL로 확인한다. 어댑터가 문장마다 트랜잭션을 열기 때문에 테스트에는 트랜잭션을 두지 않고, 끝나면 직접 지운다
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "pawmento.auth.jwt-secret=test-only-jwt-secret-0123456789-abcdefghij")
class EmailVerificationPersistenceAdapterTest {

	// DB는 마이크로초까지 저장하므로 비교하기 쉽게 초 단위로 둔다
	private static final Instant SENT_AT = Instant.parse("2026-11-26T09:00:00Z");

	private final String email = "verify-" + UUID.randomUUID() + "@test.com";

	@Autowired
	private EmailVerificationPersistenceAdapter adapter;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private TransactionTemplate transaction;

	@AfterEach
	void cleanUp() {
		jdbcTemplate.update("delete from email_verification where email = ?", email);
	}

	private EmailVerification sent(String codeHash, Instant sentAt) {
		return EmailVerification.send(email, VerificationPurpose.SIGNUP, codeHash, sentAt);
	}

	@Test
	@DisplayName("#279-S1 저장하면 id가 생기고, 이메일·용도로 읽으면 저장한 값이 그대로 나온다")
	void saveAndLoad() {
		EmailVerification saved = adapter.save(sent("hash-1", SENT_AT));

		assertThat(saved.getId()).isNotNull();
		EmailVerification loaded = adapter.load(email, VerificationPurpose.SIGNUP).orElseThrow();
		assertThat(loaded.getId()).isEqualTo(saved.getId());
		assertThat(loaded.getEmail()).isEqualTo(email);
		assertThat(loaded.getCodeHash()).isEqualTo("hash-1");
		assertThat(loaded.getFailedAttempts()).isZero();
		assertThat(loaded.getSentAt()).isEqualTo(SENT_AT);
		assertThat(loaded.getCodeExpiresAt()).isEqualTo(SENT_AT.plus(EmailVerification.CODE_VALID_FOR));
		assertThat(adapter.load(email, VerificationPurpose.PASSWORD_RESET)).isEmpty();
	}

	@Test
	@DisplayName("#279-S3 같은 이메일·용도를 또 저장하면 409 예외이고, 용도가 다르면 따로 저장된다")
	void duplicateEmailAndPurposeConflicts() {
		adapter.save(sent("hash-1", SENT_AT));

		assertThatThrownBy(() -> adapter.save(sent("hash-2", SENT_AT)))
				.isInstanceOf(EmailVerificationConflictException.class);
		adapter.save(EmailVerification.send(email, VerificationPurpose.PASSWORD_RESET, "hash-3", SENT_AT));

		assertThat(adapter.load(email, VerificationPurpose.SIGNUP).orElseThrow().getCodeHash()).isEqualTo("hash-1");
		assertThat(jdbcTemplate.queryForObject("select count(*) from email_verification where email = ?",
				Integer.class, email)).isEqualTo(2);
	}

	@Test
	@DisplayName("#279-S3 다시 받기는 마지막으로 보낸 시각이 기준 이전일 때만 새 번호로 바꾸고 틀린 횟수를 0으로 되돌린다")
	void resendOnlyAfterInterval() {
		adapter.save(sent("hash-1", SENT_AT));
		jdbcTemplate.update("update email_verification set failed_attempts = 3 where email = ?", email);
		Instant now = SENT_AT.plus(EmailVerification.RESEND_AFTER);

		// 기준이 보낸 시각보다 1초 앞이면(아직 60초가 안 지났으면) 바꾸지 않는다
		assertThat(adapter.resend(sent("hash-2", now), SENT_AT.minusSeconds(1))).isFalse();
		EmailVerification unchanged = adapter.load(email, VerificationPurpose.SIGNUP).orElseThrow();
		assertThat(unchanged.getCodeHash()).isEqualTo("hash-1");
		assertThat(unchanged.getFailedAttempts()).isEqualTo(3);

		// 기준이 보낸 시각과 같으면(딱 60초) 바꾼다
		assertThat(adapter.resend(sent("hash-2", now), SENT_AT)).isTrue();
		EmailVerification resent = adapter.load(email, VerificationPurpose.SIGNUP).orElseThrow();
		assertThat(resent.getId()).isEqualTo(unchanged.getId());
		assertThat(resent.getCodeHash()).isEqualTo("hash-2");
		assertThat(resent.getFailedAttempts()).isZero();
		assertThat(resent.getSentAt()).isEqualTo(now);
		assertThat(resent.getCodeExpiresAt()).isEqualTo(now.plus(EmailVerification.CODE_VALID_FOR));
	}

	@Test
	@DisplayName("#279-S3 저장된 줄이 없으면 다시 받기는 아무것도 바꾸지 않는다")
	void resendWithoutRowChangesNothing() {
		assertThat(adapter.resend(sent("hash-1", SENT_AT), SENT_AT)).isFalse();
		assertThat(adapter.load(email, VerificationPurpose.SIGNUP)).isEmpty();
	}

	@Test
	@DisplayName("#279-S3 같은 이메일의 다시 받기가 동시에 와도 한 번만 바뀌고, 남은 번호는 이긴 쪽의 번호다")
	void concurrentResendChangesOnce() throws Exception {
		adapter.save(sent("hash-0", SENT_AT));
		Instant now = SENT_AT.plus(EmailVerification.RESEND_AFTER);
		int requests = 8;
		ExecutorService pool = Executors.newFixedThreadPool(requests);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<Boolean>> results = new ArrayList<>();
			for (int i = 0; i < requests; i++) {
				String codeHash = "hash-" + (i + 1);
				results.add(pool.submit(() -> {
					start.await();
					return adapter.resend(sent(codeHash, now), now.minus(EmailVerification.RESEND_AFTER));
				}));
			}
			start.countDown();

			int changed = 0;
			for (Future<Boolean> result : results) {
				if (result.get(30, TimeUnit.SECONDS)) {
					changed++;
				}
			}
			assertThat(changed).isEqualTo(1);
		}
		finally {
			pool.shutdownNow();
		}
		assertThat(adapter.load(email, VerificationPurpose.SIGNUP).orElseThrow().getCodeHash())
				.isNotEqualTo("hash-0").startsWith("hash-");
	}

	@Test
	@DisplayName("#279-S4 메일 실패 뒤 지우기는 같은 id와 발송 식별자일 때만 지운다")
	void deleteOnlyMatchingSend() {
		EmailVerification saved = adapter.save(sent("hash-1", SENT_AT));

		assertThat(adapter.delete(saved.getId(), "other-send")).isFalse();
		assertThat(adapter.load(email, VerificationPurpose.SIGNUP)).isPresent();

		assertThat(adapter.delete(saved.getId(), saved.getSendId())).isTrue();
		assertThat(adapter.load(email, VerificationPurpose.SIGNUP)).isEmpty();
	}

	@Test
	@DisplayName("#279-S4 다른 요청이 다시 받기로 바꾼 뒤라면, 앞 발송의 지우기는 새 발송을 지우지 않는다")
	void deleteDoesNotRemoveNewerSend() {
		EmailVerification first = adapter.save(sent("hash-1", SENT_AT));
		Instant now = SENT_AT.plus(EmailVerification.RESEND_AFTER);
		EmailVerification second = sent("hash-2", now);
		assertThat(adapter.resend(second, SENT_AT)).isTrue();

		assertThat(adapter.delete(first.getId(), first.getSendId())).isFalse();
		EmailVerification remaining = adapter.load(email, VerificationPurpose.SIGNUP).orElseThrow();
		assertThat(remaining.getSendId()).isEqualTo(second.getSendId());
		assertThat(remaining.getCodeHash()).isEqualTo("hash-2");
	}

	@Test
	@DisplayName("#279-S3 갱신하는 순간 저장된 번호와 같은 번호로는 다시 받기를 하지 않고, 기존 발송이 그대로 남는다")
	void resendRejectsSameStoredCode() {
		EmailVerification first = adapter.save(sent("hash-1", SENT_AT));
		Instant now = SENT_AT.plus(EmailVerification.RESEND_AFTER);

		// 60초는 지났지만 새 번호의 해시가 저장된 해시와 같다 (서비스가 읽은 뒤 다른 요청이 같은 번호로 바꾼 경우)
		assertThat(adapter.resend(sent("hash-1", now), SENT_AT)).isFalse();

		EmailVerification kept = adapter.load(email, VerificationPurpose.SIGNUP).orElseThrow();
		assertThat(kept.getSendId()).isEqualTo(first.getSendId());
		assertThat(kept.getSentAt()).isEqualTo(SENT_AT);
		assertThat(kept.getCodeExpiresAt()).isEqualTo(first.getCodeExpiresAt());
	}

	@Test
	@DisplayName("#279-S3 저장된 줄이 도메인 규칙에 맞지 않으면 요청 오류가 아니라 저장 데이터 오류로 알린다")
	void brokenStoredRowIsStoredDataError() {
		adapter.save(sent("hash-1", SENT_AT));
		jdbcTemplate.update("update email_verification set failed_attempts = -1 where email = ?", email);

		assertThatThrownBy(() -> adapter.load(email, VerificationPurpose.SIGNUP))
				.isInstanceOf(StoredEmailVerificationInvalidException.class);
	}

	@Test
	@DisplayName("#279-S5 확인 시각과 토큰은 저장한 그대로 읽히고, 확인 전에는 셋 다 비어 있다")
	void tokenFieldsRoundTrip() {
		adapter.save(sent("hash-1", SENT_AT));
		EmailVerification before = adapter.load(email, VerificationPurpose.SIGNUP).orElseThrow();
		assertThat(before.getVerifiedAt()).isNull();
		assertThat(before.getTokenHash()).isNull();
		assertThat(before.getTokenExpiresAt()).isNull();

		Instant verifiedAt = SENT_AT.plusSeconds(30);
		jdbcTemplate.update("""
				update email_verification set verified_at = ?, token_hash = 'token-hash', token_expires_at = ?
				where email = ?
				""", java.sql.Timestamp.from(verifiedAt),
				java.sql.Timestamp.from(verifiedAt.plus(EmailVerification.TOKEN_VALID_FOR)), email);

		EmailVerification after = adapter.load(email, VerificationPurpose.SIGNUP).orElseThrow();
		assertThat(after.getVerifiedAt()).isEqualTo(verifiedAt);
		assertThat(after.getTokenHash()).isEqualTo("token-hash");
		assertThat(after.getTokenExpiresAt()).isEqualTo(verifiedAt.plus(EmailVerification.TOKEN_VALID_FOR));
	}

	@Test
	@DisplayName("#279-S3 확인한 뒤 다시 받으면 DB에서도 확인 시각과 토큰이 지워진다")
	void resendClearsStoredConfirmation() {
		adapter.save(sent("hash-1", SENT_AT));
		jdbcTemplate.update("""
				update email_verification set verified_at = ?, token_hash = 'token-hash', token_expires_at = ?
				where email = ?
				""", java.sql.Timestamp.from(SENT_AT.plusSeconds(10)),
				java.sql.Timestamp.from(SENT_AT.plus(EmailVerification.TOKEN_VALID_FOR)), email);
		Instant now = SENT_AT.plus(EmailVerification.RESEND_AFTER);

		assertThat(adapter.resend(sent("hash-2", now), SENT_AT)).isTrue();

		EmailVerification resent = adapter.load(email, VerificationPurpose.SIGNUP).orElseThrow();
		assertThat(resent.getCodeHash()).isEqualTo("hash-2");
		assertThat(resent.getVerifiedAt()).isNull();
		assertThat(resent.getTokenHash()).isNull();
		assertThat(resent.getTokenExpiresAt()).isNull();
	}

	@Test
	@DisplayName("#279-S5 확인 시각은 있는데 토큰이 없는 깨진 줄은 저장 데이터 오류로 알린다")
	void brokenTokenRowIsStoredDataError() {
		adapter.save(sent("hash-1", SENT_AT));
		jdbcTemplate.update("update email_verification set verified_at = now() where email = ?", email);

		assertThatThrownBy(() -> adapter.load(email, VerificationPurpose.SIGNUP))
				.isInstanceOf(StoredEmailVerificationInvalidException.class);
	}

	private int failedAttempts() {
		return jdbcTemplate.queryForObject("select failed_attempts from email_verification where email = ?",
				Integer.class, email);
	}

	@Test
	@DisplayName("#279-S6 틀린 번호는 같은 발송일 때만 한 번씩 세고, 다섯 번에서 멈춘다")
	void recordFailureCountsUpToFive() {
		EmailVerification saved = adapter.save(sent("hash-1", SENT_AT));

		assertThat(adapter.recordFailure(saved.getId(), "other-send")).isFalse();
		assertThat(failedAttempts()).isZero();

		for (int i = 0; i < EmailVerification.MAX_FAILED_ATTEMPTS; i++) {
			assertThat(adapter.recordFailure(saved.getId(), saved.getSendId())).isTrue();
		}
		assertThat(adapter.recordFailure(saved.getId(), saved.getSendId())).isFalse();
		assertThat(failedAttempts()).isEqualTo(EmailVerification.MAX_FAILED_ATTEMPTS);
	}

	@Test
	@DisplayName("#279-S6 틀린 번호 확인이 동시에 10번 와도 실패는 다섯 번까지만 센다")
	void concurrentFailuresStopAtFive() throws Exception {
		EmailVerification saved = adapter.save(sent("hash-1", SENT_AT));
		int requests = 10;
		ExecutorService pool = Executors.newFixedThreadPool(requests);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<Boolean>> results = new ArrayList<>();
			for (int i = 0; i < requests; i++) {
				results.add(pool.submit(() -> {
					start.await();
					return adapter.recordFailure(saved.getId(), saved.getSendId());
				}));
			}
			start.countDown();

			int counted = 0;
			for (Future<Boolean> result : results) {
				if (result.get(30, TimeUnit.SECONDS)) {
					counted++;
				}
			}
			assertThat(counted).isEqualTo(EmailVerification.MAX_FAILED_ATTEMPTS);
		}
		finally {
			pool.shutdownNow();
		}
		assertThat(failedAttempts()).isEqualTo(EmailVerification.MAX_FAILED_ATTEMPTS);
	}

	// 확인 저장은 DB의 실제 현재 시각(clock_timestamp)으로 만료를 보므로, 확인 테스트는 지금을 기준으로 보낸다
	private static Instant now() {
		return Instant.now().truncatedTo(ChronoUnit.MILLIS);
	}

	@Test
	@DisplayName("#279-S5 확인할 수 있으면 확인 시각과 토큰을 저장하고, 그 뒤에는 틀린 횟수를 세지 않는다")
	void confirmStoresToken() {
		Instant sentAt = now();
		EmailVerification saved = adapter.save(sent("hash-1", sentAt));
		Instant verifiedAt = sentAt.plusSeconds(1);
		EmailVerification confirmed = saved.confirm("token-hash", verifiedAt);

		assertThat(adapter.confirm(confirmed)).isTrue();

		EmailVerification loaded = adapter.load(email, VerificationPurpose.SIGNUP).orElseThrow();
		assertThat(loaded.getVerifiedAt()).isEqualTo(verifiedAt);
		assertThat(loaded.getTokenHash()).isEqualTo("token-hash");
		assertThat(loaded.getTokenExpiresAt()).isEqualTo(verifiedAt.plus(EmailVerification.TOKEN_VALID_FOR));
		assertThat(adapter.recordFailure(saved.getId(), saved.getSendId())).isFalse();
	}

	@Test
	@DisplayName("#279-S7 이미 확인한 인증은 다시 확인되지 않고 처음 토큰이 남는다")
	void confirmOnlyOnce() {
		Instant sentAt = now();
		EmailVerification saved = adapter.save(sent("hash-1", sentAt));
		assertThat(adapter.confirm(saved.confirm("token-1", sentAt.plusSeconds(1)))).isTrue();

		assertThat(adapter.confirm(saved.confirm("token-2", sentAt.plusSeconds(2)))).isFalse();
		assertThat(adapter.load(email, VerificationPurpose.SIGNUP).orElseThrow().getTokenHash()).isEqualTo("token-1");
	}

	@Test
	@DisplayName("#279-S5 만료 직전에 시작한 확인도 저장하는 순간 DB 시각으로 만료됐으면 저장되지 않는다")
	void confirmRejectsExpiredAtStore() {
		Instant sentAt = now();
		EmailVerification saved = adapter.save(sent("hash-1", sentAt));
		// 서비스는 만료 전에 읽고 판단했다(도메인 검사 통과)
		EmailVerification confirmed = saved.confirm("token-hash", sentAt.plusSeconds(1));
		// 조회·줄 잠금 대기 사이에 만료 시각이 지났다
		jdbcTemplate.update("update email_verification set code_expires_at = clock_timestamp() - interval '1 millisecond'"
				+ " where email = ?", email);

		assertThat(adapter.confirm(confirmed)).isFalse();
		assertThat(adapter.load(email, VerificationPurpose.SIGNUP).orElseThrow().getVerifiedAt()).isNull();
	}

	@Test
	@DisplayName("#279-S5 서비스가 넘긴 확인 시각이 아니라 저장 순간의 DB 시각으로 만료를 본다")
	void confirmIgnoresStaleVerifiedAt() {
		Instant sentAt = now();
		EmailVerification saved = adapter.save(sent("hash-1", sentAt));
		jdbcTemplate.update("update email_verification set code_expires_at = clock_timestamp() + interval '1 minute'"
				+ " where email = ?", email);
		// 넘긴 확인 시각은 아주 옛날이어도 지금 DB 시각으로 만료 전이면 저장된다
		EmailVerification stale = EmailVerification.restore(saved.getId(), email, VerificationPurpose.SIGNUP, "hash-1",
				saved.getSendId(), 0, saved.getCodeExpiresAt(), sentAt, Instant.parse("2000-01-01T00:00:00Z"),
				"token-hash", Instant.parse("2000-01-01T00:30:00Z"));

		assertThat(adapter.confirm(stale)).isTrue();
	}

	@Test
	@DisplayName("#279-S6 다섯 번 틀린 뒤에는 저장하는 순간에도 확인되지 않는다")
	void confirmRejectsAfterFiveFailures() {
		Instant sentAt = now();
		EmailVerification saved = adapter.save(sent("hash-1", sentAt));
		EmailVerification confirmed = saved.confirm("token-hash", sentAt.plusSeconds(1));
		for (int i = 0; i < EmailVerification.MAX_FAILED_ATTEMPTS; i++) {
			adapter.recordFailure(saved.getId(), saved.getSendId());
		}

		assertThat(adapter.confirm(confirmed)).isFalse();
		assertThat(adapter.load(email, VerificationPurpose.SIGNUP).orElseThrow().getVerifiedAt()).isNull();
	}

	@Test
	@DisplayName("#279-S3 확인하는 사이 다시 받기로 발송이 바뀌었으면 예전 번호의 확인은 저장되지 않는다")
	void confirmRejectsAfterResend() {
		Instant sentAt = now();
		EmailVerification first = adapter.save(sent("hash-1", sentAt));
		EmailVerification confirmed = first.confirm("token-hash", sentAt.plusSeconds(1));
		assertThat(adapter.resend(sent("hash-2", sentAt.plusSeconds(60)), sentAt)).isTrue();

		assertThat(adapter.confirm(confirmed)).isFalse();
		EmailVerification loaded = adapter.load(email, VerificationPurpose.SIGNUP).orElseThrow();
		assertThat(loaded.getCodeHash()).isEqualTo("hash-2");
		assertThat(loaded.getVerifiedAt()).isNull();
		assertThat(adapter.recordFailure(first.getId(), first.getSendId())).isFalse();
	}

	@Test
	@DisplayName("#279-S6 바깥 트랜잭션이 롤백돼도 틀린 횟수는 따로 커밋되어 남는다")
	void failureSurvivesOuterRollback() {
		EmailVerification saved = adapter.save(sent("hash-1", now()));

		transaction.executeWithoutResult(status -> {
			assertThat(adapter.recordFailure(saved.getId(), saved.getSendId())).isTrue();
			status.setRollbackOnly();
		});

		assertThat(failedAttempts()).isEqualTo(1);
	}

	private int waitingForLock() {
		return jdbcTemplate.queryForObject("""
				select count(*) from pg_stat_activity
				where datname = current_database() and wait_event_type = 'Lock' and query ilike '%email_verification%'
				""", Integer.class);
	}

	@Test
	@DisplayName("#279-S5 확인이 줄 잠금을 기다리는 사이 만료되면, 상대가 줄을 바꾸지 않고 놓아도 저장되지 않는다")
	void confirmChecksExpiryAfterWaitingForLock() throws Exception {
		Instant sentAt = now();
		EmailVerification saved = adapter.save(sent("hash-1", sentAt));
		// 서비스는 만료 전에 읽고 판단했다
		EmailVerification confirmed = saved.confirm("token-hash", sentAt.plusSeconds(1));
		jdbcTemplate.update("update email_verification set code_expires_at = clock_timestamp() + interval '2 seconds'"
				+ " where email = ?", email);
		CountDownLatch locked = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			// 다른 트랜잭션이 줄을 잠그기만 하고, 나중에 바꾸지 않은 채 롤백한다
			Future<?> holder = pool.submit(() -> transaction.executeWithoutResult(status -> {
				jdbcTemplate.queryForList("select id from email_verification where email = ? for update", email);
				locked.countDown();
				try {
					release.await(30, TimeUnit.SECONDS);
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				status.setRollbackOnly();
			}));
			assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

			Future<Boolean> confirming = pool.submit(() -> adapter.confirm(confirmed));
			// 확인이 잠금 대기에 들어간 것을 본 뒤, 만료 시각(2초)이 지날 때까지 잠금을 들고 있는다
			Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> waitingForLock() > 0);
			Thread.sleep(2_500);
			release.countDown();
			holder.get(10, TimeUnit.SECONDS);

			assertThat(confirming.get(10, TimeUnit.SECONDS)).isFalse();
		}
		finally {
			release.countDown();
			pool.shutdownNow();
		}
		assertThat(adapter.load(email, VerificationPurpose.SIGNUP).orElseThrow().getVerifiedAt()).isNull();
	}

	@Test
	@DisplayName("#279-S5 잠금을 기다린 뒤에도 만료 전이면 확인이 저장된다")
	void confirmSucceedsAfterWaitingForLockBeforeExpiry() throws Exception {
		Instant sentAt = now();
		EmailVerification saved = adapter.save(sent("hash-1", sentAt));
		EmailVerification confirmed = saved.confirm("token-hash", sentAt.plusSeconds(1));
		jdbcTemplate.update("update email_verification set code_expires_at = clock_timestamp() + interval '1 minute'"
				+ " where email = ?", email);
		CountDownLatch locked = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			Future<?> holder = pool.submit(() -> transaction.executeWithoutResult(status -> {
				jdbcTemplate.queryForList("select id from email_verification where email = ? for update", email);
				locked.countDown();
				try {
					release.await(30, TimeUnit.SECONDS);
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				status.setRollbackOnly();
			}));
			assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

			Future<Boolean> confirming = pool.submit(() -> adapter.confirm(confirmed));
			Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> waitingForLock() > 0);
			release.countDown();
			holder.get(10, TimeUnit.SECONDS);

			assertThat(confirming.get(10, TimeUnit.SECONDS)).isTrue();
		}
		finally {
			release.countDown();
			pool.shutdownNow();
		}
	}
}
