package com.pawmento.map.adapter.in.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.pawmento.map.application.exception.PublicDataSyncFailedException;
import com.pawmento.map.application.exception.PlacePersistenceUnavailableException;
import com.pawmento.map.application.exception.PlaceSyncAlreadyRunningException;
import com.pawmento.map.application.port.in.SyncPublicDataPlacesCommand;
import com.pawmento.map.application.port.in.SyncPublicDataPlacesResult;
import com.pawmento.map.application.port.in.SyncPublicDataPlacesUseCase;

// 시나리오는 docs/map/animal-places.md(#172-S22 자동 실행)에 있다. 매일 자동 실행의 규칙(두 종류를 차례로, 실패하면 시간을 두고 다시, 포기)을
// 가짜 실행기로 확인한다. 실제 스레드와 시각 계산은 쓰지 않는다
class PlaceSyncSchedulerTest {

	private static final Duration RETRY_DELAY = Duration.ofHours(3);

	// 바로 실행하고, 나중에 할 일은 모아 두었다가 테스트가 직접 꺼내 실행한다
	private static class ManualRunner implements PlaceSyncRunner {

		final List<Runnable> later = new ArrayList<>();

		final List<Duration> delays = new ArrayList<>();

		@Override
		public void runNow(Runnable task) {
			task.run();
		}

		@Override
		public void runLater(Runnable task, Duration delay) {
			later.add(task);
			delays.add(delay);
		}

		void runNextLater() {
			later.remove(0).run();
		}

	}

	private static class FakeUseCase implements SyncPublicDataPlacesUseCase {

		final List<String> calls = new ArrayList<>();

		java.util.function.Function<String, RuntimeException> failure = category -> null;

		@Override
		public SyncPublicDataPlacesResult sync(SyncPublicDataPlacesCommand command) {
			calls.add(command.category());
			RuntimeException error = failure.apply(command.category());
			if (error != null) {
				throw error;
			}
			return new SyncPublicDataPlacesResult(command.category(), 10, 8, 2, 1, 1, 0, 8, 0, 0);
		}

	}

	private static RuntimeException fetchFailure() {
		return PublicDataSyncFailedException.fetchFailed(new RuntimeException("시간 초과"));
	}

	@Test
	@DisplayName("#172-S22 매일 실행은 병원과 미용을 차례로 가져오고, 시스템 요청이라 요청한 회원이 없다")
	void runsBothCategoriesInOrder() {
		var useCase = new FakeUseCase();
		var runner = new ManualRunner();

		new PlaceSyncScheduler(useCase, runner, RETRY_DELAY, 3).trigger();

		assertThat(useCase.calls).containsExactly("HOSPITAL", "GROOMING");
		assertThat(runner.later).isEmpty();
	}

	@Test
	@DisplayName("#172-S22 한 종류가 실패해도 다른 종류는 가져오고, 실패한 종류만 정한 시간 뒤에 다시 시도한다")
	void failedCategoryIsRetriedWithoutBlockingTheOther() {
		var useCase = new FakeUseCase();
		useCase.failure = category -> "HOSPITAL".equals(category) ? fetchFailure() : null;
		var runner = new ManualRunner();

		new PlaceSyncScheduler(useCase, runner, RETRY_DELAY, 3).trigger();

		assertThat(useCase.calls).containsExactly("HOSPITAL", "GROOMING");
		assertThat(runner.delays).containsExactly(RETRY_DELAY);

		// 다시 시도할 때는 성공한다
		useCase.failure = category -> null;
		runner.runNextLater();
		assertThat(useCase.calls).containsExactly("HOSPITAL", "GROOMING", "HOSPITAL");
		assertThat(runner.later).isEmpty();
	}

	@Test
	@DisplayName("#172-S22 계속 실패하면 정한 횟수만 다시 시도하고 포기한다(처음 1번 + 다시 3번)")
	void givesUpAfterMaxRetries() {
		var useCase = new FakeUseCase();
		useCase.failure = category -> "HOSPITAL".equals(category) ? fetchFailure() : null;
		var runner = new ManualRunner();

		new PlaceSyncScheduler(useCase, runner, RETRY_DELAY, 3).trigger();
		while (!runner.later.isEmpty()) {
			runner.runNextLater();
		}

		assertThat(useCase.calls.stream().filter("HOSPITAL"::equals).count()).isEqualTo(4);
		assertThat(runner.delays).hasSize(3).containsOnly(RETRY_DELAY);
	}

	@Test
	@DisplayName("#172-S22 저장소 장애도 다시 시도한다")
	void storageOutageIsRetried() {
		var useCase = new FakeUseCase();
		useCase.failure = category -> new PlacePersistenceUnavailableException(new RuntimeException("연결 끊김"));
		var runner = new ManualRunner();

		new PlaceSyncScheduler(useCase, runner, RETRY_DELAY, 1).trigger();

		assertThat(runner.later).hasSize(2);
	}

	@Test
	@DisplayName("#172-S22 다시 시도해도 같은 실패(필수 항목 누락, 인증 거절)는 다시 시도하지 않고 다른 종류는 이어서 가져온다")
	void permanentFailuresAreNotRetried() {
		var useCase = new FakeUseCase();
		useCase.failure = category -> "HOSPITAL".equals(category)
				? PublicDataSyncFailedException.fetchRejected(new RuntimeException("인증키 거절"))
				: PublicDataSyncFailedException.rejected(PublicDataSyncFailedException.Reason.MISSING_FIELDS);
		var runner = new ManualRunner();

		new PlaceSyncScheduler(useCase, runner, RETRY_DELAY, 3).trigger();

		assertThat(useCase.calls).containsExactly("HOSPITAL", "GROOMING");
		assertThat(runner.later).isEmpty();
	}

	@Test
	@DisplayName("#172-S9 보호센터 응답이 잘렸을 수 있거나(받은 수 = 요청한 수) 등록번호가 겹치면 다시 시도해도 같아서 다시 시도하지 않는다")
	void shelterShapeProblemsAreNotRetried() {
		for (var reason : List.of(PublicDataSyncFailedException.Reason.POSSIBLY_TRUNCATED,
				PublicDataSyncFailedException.Reason.DUPLICATE_IDS)) {
			assertThat(PublicDataSyncFailedException.rejected(reason).retryable()).as("%s", reason).isFalse();
		}
		assertThat(PublicDataSyncFailedException.rejected(PublicDataSyncFailedException.Reason.EMPTY_SNAPSHOT).retryable())
			.isTrue();
	}

	@Test
	@DisplayName("#172-S22 이미 다른 실행이 돌고 있으면(관리자가 수동으로 돌리는 중) 건너뛰고 다시 시도하지 않는다")
	void alreadyRunningIsSkippedWithoutRetry() {
		var useCase = new FakeUseCase();
		useCase.failure = category -> new PlaceSyncAlreadyRunningException();
		var runner = new ManualRunner();

		new PlaceSyncScheduler(useCase, runner, RETRY_DELAY, 3).trigger();

		assertThat(useCase.calls).containsExactly("HOSPITAL", "GROOMING");
		assertThat(runner.later).isEmpty();
	}

	@Test
	@DisplayName("#172-S22 프로그램 오류 같은 예상 밖의 예외는 다시 시도해도 같아서 다시 시도하지 않고, 다른 종류는 이어서 가져온다")
	void unexpectedErrorIsNotRetried() {
		var useCase = new FakeUseCase();
		useCase.failure = category -> "HOSPITAL".equals(category) ? new IllegalStateException("버그") : null;
		var runner = new ManualRunner();

		new PlaceSyncScheduler(useCase, runner, RETRY_DELAY, 3).trigger();

		assertThat(useCase.calls).containsExactly("HOSPITAL", "GROOMING");
		assertThat(runner.later).isEmpty();
	}

	@Test
	@DisplayName("#172-S8 보호센터는 주 1회 따로 실행하고 병원·미용은 부르지 않으며, 실패하면 같은 규칙으로 다시 시도한다")
	void sheltersRunWeeklyOnTheirOwn() {
		var useCase = new FakeUseCase();
		useCase.failure = category -> fetchFailure();
		var runner = new ManualRunner();
		var scheduler = new PlaceSyncScheduler(useCase, runner, RETRY_DELAY, 1);

		scheduler.triggerShelters();

		assertThat(useCase.calls).containsExactly("SHELTER");
		assertThat(runner.delays).containsExactly(RETRY_DELAY);

		useCase.failure = category -> null;
		runner.runNextLater();
		assertThat(useCase.calls).containsExactly("SHELTER", "SHELTER");
		assertThat(runner.later).isEmpty();
	}

}
