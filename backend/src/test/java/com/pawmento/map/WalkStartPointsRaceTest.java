package com.pawmento.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.pawmento.TestcontainersConfiguration;
import com.pawmento.map.application.exception.WalkAlreadyInProgressException;
import com.pawmento.map.application.port.in.AddWalkPointsCommand;
import com.pawmento.map.application.port.in.AddWalkPointsUseCase;
import com.pawmento.map.application.port.in.StartWalkCommand;
import com.pawmento.map.application.port.in.StartWalkUseCase;
import com.pawmento.map.application.port.in.EndWalkCommand;
import com.pawmento.map.application.port.in.EndWalkUseCase;
import com.pawmento.map.application.port.out.LockPetWalksPort;
import com.pawmento.map.application.port.out.SaveWalkPort;
import com.pawmento.map.domain.Walk;
import com.pawmento.map.domain.exception.WalkNotInProgressException;
import com.pawmento.pet.application.port.in.CreatePetCommand;
import com.pawmento.pet.application.port.in.CreatePetUseCase;

// 산책 시작과 좌표 쌓기(활동 시각 갱신)가 중단 판정의 5분 경계에서 겹쳐도 같은 반려동물의 진행 중인 산책이 둘이 되지 않는지,
// 제어할 수 있는 시계와 래치로 순서를 고정해 확인한다. UseCase를 직접 불러 두 트랜잭션이 실제로 겹치게 한다
@Import({ TestcontainersConfiguration.class, WalkStartPointsRaceTest.ClockConfig.class })
@SpringBootTest(properties = "pawmento.auth.jwt-secret=test-only-jwt-secret-0123456789-abcdefghij")
class WalkStartPointsRaceTest {

	private static final long MEMBER = 9101L;

	private static final Instant L = Instant.parse("2026-11-17T01:00:00Z");

	@TestConfiguration
	static class ClockConfig {

		@Bean
		@Primary
		TestClock raceClock() {
			return new TestClock();
		}

	}

	static final class TestClock extends Clock {

		private final AtomicReference<Instant> now = new AtomicReference<>(L);

		void set(Instant instant) {
			now.set(instant);
		}

		@Override
		public ZoneId getZone() {
			return ZoneId.of("Asia/Seoul");
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now.get();
		}

	}

	@Autowired
	private TestClock clock;

	@Autowired
	private StartWalkUseCase startWalk;

	@Autowired
	private AddWalkPointsUseCase addPoints;

	@Autowired
	private CreatePetUseCase pets;

	@Autowired
	private JdbcTemplate jdbc;

	// 좌표를 쌓는 요청이 활동 시각을 저장한 직후(아직 커밋 전)에 멈추게 하려고 저장 포트를 감시한다
	@MockitoSpyBean
	private SaveWalkPort saveWalkPort;

	// 소유자 확인 뒤 반려동물 잠금을 잡기 직전에 멈추게 하려고 잠금 포트도 감시한다
	@MockitoSpyBean
	private LockPetWalksPort lockPetWalksPort;

	@Autowired
	private EndWalkUseCase endWalk;

	@Test
	@DisplayName("#73-S13 활동 시각을 갱신하는 좌표 요청이 커밋하기 전에 5분 경계를 넘어 새 산책을 시작해도, 같은 반려동물의 진행 중인 산책은 하나뿐이다")
	void startAtBoundaryWhileActivityUpdateIsUncommitted() throws Exception {
		long pet = pets.create(new CreatePetCommand(MEMBER, "콩이", "DOG", null, null)).petId();
		clock.set(L);
		long oldWalk = startWalk.start(new StartWalkCommand(MEMBER, pet)).walkId();

		CountDownLatch activitySaved = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		// 기존 산책(id가 있는 산책)의 저장만 멈춘다. 새 산책을 만드는 저장(id 없음)은 그대로 지나간다
		doAnswer(invocation -> {
			Object result = invocation.callRealMethod();
			activitySaved.countDown();
			assertThat(release.await(15, TimeUnit.SECONDS)).isTrue();
			return result;
		}).when(saveWalkPort).save(argThat((Walk walk) -> walk != null && walk.getId() != null));

		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			// A: L+4분59.9초에 좌표를 받아 활동 시각을 L+4:59.9로 갱신하지만 커밋은 아직 하지 않는다
			clock.set(L.plusSeconds(299).plusMillis(900));
			Future<?> a = pool.submit(() -> addPoints.add(new AddWalkPointsCommand(MEMBER, oldWalk,
					List.of(new AddWalkPointsCommand.Point(37.5, 127.0, L.plusSeconds(299))))));
			assertThat(activitySaved.await(10, TimeUnit.SECONDS)).isTrue();

			// B: L+5분0.1초에 같은 반려동물의 새 산책을 시작한다. A의 커밋 전 값(L)만 보면 기존 산책이 중단된 것으로 보인다
			clock.set(L.plusSeconds(300).plusMillis(100));
			Future<?> b = pool.submit(() -> startWalk.start(new StartWalkCommand(MEMBER, pet)));
			waitUntilBlockedOnAdvisoryLockOrDone(b);

			release.countDown();
			a.get(15, TimeUnit.SECONDS);
			// A가 커밋한 뒤 B는 갱신된 활동 시각을 보고 이미 진행 중인 산책이 있다고 거절해야 한다
			assertThatThrownBy(() -> b.get(15, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class)
				.hasCauseInstanceOf(WalkAlreadyInProgressException.class);
		}
		finally {
			release.countDown();
			pool.shutdownNow();
			reset(saveWalkPort);
		}

		assertThat(activeWalkCount(pet, L.plusSeconds(300).plusMillis(200))).isEqualTo(1);
		assertThat(walkCount(pet)).isEqualTo(1);
	}

	@Test
	@DisplayName("#73-S2 좌표 요청이 소유자를 확인한 뒤 잠그기 전에 다른 요청이 산책을 끝내면, 좌표 요청은 409이고 끝낸 시각과 좌표가 그대로다")
	void walkEndedBetweenOwnerCheckAndLock() throws Exception {
		long pet = pets.create(new CreatePetCommand(MEMBER, "나비", "CAT", null, null)).petId();
		clock.set(L);
		long walk = startWalk.start(new StartWalkCommand(MEMBER, pet)).walkId();

		CountDownLatch ownerChecked = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		// 소유자 확인 뒤 반려동물 잠금을 잡으려는 순간에 멈춘다. 이 시점에 산책을 읽어 두었다면 오래된 상태가 남는다
		doAnswer(invocation -> {
			ownerChecked.countDown();
			assertThat(release.await(15, TimeUnit.SECONDS)).isTrue();
			return invocation.callRealMethod();
		}).when(lockPetWalksPort).lock(pet);

		ExecutorService pool = Executors.newSingleThreadExecutor();
		try {
			clock.set(L.plusSeconds(30));
			Future<?> a = pool.submit(() -> addPoints.add(new AddWalkPointsCommand(MEMBER, walk,
					List.of(new AddWalkPointsCommand.Point(37.5, 127.0, L.plusSeconds(29))))));
			assertThat(ownerChecked.await(10, TimeUnit.SECONDS)).isTrue();

			// 다른 요청이 산책을 끝내고 커밋한다(끝내기는 반려동물 잠금을 잡지 않아 막히지 않는다)
			reset(lockPetWalksPort);
			endWalk.end(new EndWalkCommand(MEMBER, walk));
			Instant endedAt = jdbc.queryForObject("select ended_at from walks where id = ?", Timestamp.class, walk)
				.toInstant();
			release.countDown();

			assertThatThrownBy(() -> a.get(15, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class)
				.hasCauseInstanceOf(WalkNotInProgressException.class);
			// 오래된 상태로 저장했다면 끝낸 시각이 지워진다
			assertThat(jdbc.queryForObject("select ended_at from walks where id = ?", Timestamp.class, walk)
				.toInstant()).isEqualTo(endedAt);
			assertThat(jdbc.queryForObject("select count(*) from walk_points where walk_id = ?", Integer.class, walk))
				.isZero();
		}
		finally {
			release.countDown();
			pool.shutdownNow();
			reset(lockPetWalksPort);
		}
	}

	@Test
	@DisplayName("#73-S13 새 산책이 먼저 시작된 뒤에 온 좌표 요청은 중단된 이전 산책에 좌표를 쌓지 못하고 409다")
	void pointsAfterNewWalkStartedAreRejected() {
		long pet = pets.create(new CreatePetCommand(MEMBER, "보리", "DOG", null, null)).petId();
		clock.set(L);
		long oldWalk = startWalk.start(new StartWalkCommand(MEMBER, pet)).walkId();

		// 마지막 활동으로부터 5분이 지나 이전 산책은 중단이고, 새 산책을 시작한다
		clock.set(L.plusSeconds(300).plusMillis(100));
		startWalk.start(new StartWalkCommand(MEMBER, pet));
		clock.set(L.plusSeconds(300).plusMillis(300));

		assertThatThrownBy(() -> addPoints.add(new AddWalkPointsCommand(MEMBER, oldWalk,
				List.of(new AddWalkPointsCommand.Point(37.5, 127.0, L.plusSeconds(301))))))
			.isInstanceOf(WalkNotInProgressException.class);

		assertThat(activeWalkCount(pet, L.plusSeconds(300).plusMillis(400))).isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from walk_points where walk_id = ?", Integer.class, oldWalk))
			.isZero();
	}

	// 잠금을 기다리는 쪽이 보이거나 B가 끝날 때까지 기다린다. 고치기 전에는 B가 기다리지 않고 바로 끝난다
	private void waitUntilBlockedOnAdvisoryLockOrDone(Future<?> b) throws InterruptedException {
		long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
		while (System.nanoTime() < deadline && !b.isDone()) {
			Integer waiting = jdbc.queryForObject(
					"select count(*) from pg_locks where locktype = 'advisory' and not granted", Integer.class);
			if (waiting != null && waiting > 0) {
				return;
			}
			Thread.sleep(20);
		}
	}

	// 기준 시각 기준으로 끝나지 않았고 마지막 활동이 5분 안인 산책의 수
	private int activeWalkCount(long petId, Instant at) {
		return jdbc.queryForObject(
				"select count(*) from walks where pet_id = ? and ended_at is null and last_activity_at >= ?",
				Integer.class, petId, Timestamp.from(at.minus(Walk.INTERRUPTED_AFTER)));
	}

	private int walkCount(long petId) {
		return jdbc.queryForObject("select count(*) from walks where pet_id = ?", Integer.class, petId);
	}

}
