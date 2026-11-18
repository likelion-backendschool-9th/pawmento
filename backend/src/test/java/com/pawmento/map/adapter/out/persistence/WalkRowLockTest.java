package com.pawmento.map.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import com.pawmento.TestcontainersConfiguration;
import com.pawmento.map.domain.Walk;

// 행 잠금은 서로 다른 트랜잭션 둘이 있어야 확인할 수 있다. 그래서 테스트를 트랜잭션으로 감싸지 않고,
// 실제로 커밋하는 트랜잭션 두 개를 스레드로 나눠 돌린다. 산책 id는 테스트가 새로 만든 행만 쓴다
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "pawmento.auth.jwt-secret=test-only-jwt-secret-0123456789-abcdefghij")
class WalkRowLockTest {

	private static final Instant NOW = Instant.parse("2026-11-17T01:00:00Z");

	@Autowired
	private WalkPersistenceAdapter adapter;

	@Autowired
	private TransactionTemplate transactionTemplate;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	@DisplayName("#73-S14 행을 잠그고 읽은 요청이 끝나기 전에는 같은 산책을 잠그고 읽는 다른 요청이 기다리고, 끝난 뒤에는 바뀐 값을 읽는다")
	void secondReaderWaitsAndSeesCommittedChange() throws Exception {
		Long id = transactionTemplate.execute(status -> adapter.save(Walk.start(10L, 1L, NOW)));
		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch firstHoldsLock = new CountDownLatch(1);
		CountDownLatch releaseFirst = new CountDownLatch(1);
		CountDownLatch secondStarted = new CountDownLatch(1);
		AtomicReference<Instant> endedAtSeenBySecond = new AtomicReference<>();
		Instant endedAt = NOW.plusSeconds(60);
		try {
			// 첫 요청: 잠그고 읽은 뒤 신호를 보내고 기다렸다가, 산책을 끝내고 커밋한다
			Future<?> first = pool.submit(() -> transactionTemplate.executeWithoutResult(status -> {
				Walk walk = adapter.loadByIdForUpdate(id).orElseThrow();
				firstHoldsLock.countDown();
				await(releaseFirst);
				walk.end(endedAt);
				adapter.save(walk);
			}));
			assertThat(firstHoldsLock.await(10, TimeUnit.SECONDS)).isTrue();

			// 둘째 요청: 같은 산책을 잠그고 읽으려 한다. 첫 요청이 끝나기 전에는 돌아오지 못해야 한다
			Future<?> second = pool.submit(() -> transactionTemplate.executeWithoutResult(status -> {
				secondStarted.countDown();
				Walk walk = adapter.loadByIdForUpdate(id).orElseThrow();
				endedAtSeenBySecond.set(walk.getEndedAt());
			}));
			assertThat(secondStarted.await(10, TimeUnit.SECONDS)).isTrue();
			Thread.sleep(500);
			assertThat(second.isDone()).as("첫 요청이 잠금을 쥐고 있는 동안 둘째 요청은 기다려야 한다").isFalse();

			releaseFirst.countDown();
			first.get(10, TimeUnit.SECONDS);
			second.get(10, TimeUnit.SECONDS);

			assertThat(endedAtSeenBySecond.get()).as("둘째 요청은 첫 요청이 커밋한 값을 읽어야 한다").isEqualTo(endedAt);
		}
		finally {
			releaseFirst.countDown();
			pool.shutdownNow();
			jdbcTemplate.update("delete from walks where id = ?", id);
		}
	}

	private static void await(CountDownLatch latch) {
		try {
			latch.await(10, TimeUnit.SECONDS);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

}
