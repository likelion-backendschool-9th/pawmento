package com.pawmento.map.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.test.util.ReflectionTestUtils;

import com.pawmento.map.domain.PlaceCategory;
import com.pawmento.map.domain.PlaceDraft;

// 시나리오는 docs/map/place-search.md(#195-S19)에 있다. 한 행을 채우지 못해도 나머지 행은 계속 채우고 끝까지 나아가는지를
// 가짜 저장소로 확인한다(DB 없이 도는 단위 테스트)
class PlaceSearchKeyBackfillFailureTest {

	private final PlaceJpaRepository repository = mock(PlaceJpaRepository.class);

	// 다시 시도 간격을 5ms로 줄여, 실제로 전용 스레드에서 예약되어 도는 것을 기다려 확인한다
	private final PlaceSearchKeyBackfill backfill = new PlaceSearchKeyBackfill(repository, 5);

	@AfterEach
	void stopRetrying() {
		backfill.stopRetrying();
	}

	private static PlaceJpaEntity entity(long id, String name) {
		PlaceJpaEntity entity = PlaceJpaEntity.from(new PlaceDraft(PlaceCategory.HOSPITAL, "src-" + id, name, "서울 1", null,
				37.58, 127.0, false, null, null, null, null));
		ReflectionTestUtils.setField(entity, "id", id);
		return entity;
	}

	@Test
	@DisplayName("#195-S19 한 행을 채우지 못해도 나머지 행은 계속 채우고, 채운 수와 못 채운 수를 알려 준다")
	void keepsFillingTheOtherRowsWhenOneFails() {
		when(repository.findTop500ByNameKeyIsNullAndIdGreaterThanOrderByIdAsc(0L))
			.thenReturn(List.of(entity(1, "하나"), entity(2, "둘"), entity(3, "셋")));
		when(repository.findTop500ByNameKeyIsNullAndIdGreaterThanOrderByIdAsc(3L)).thenReturn(List.of());
		when(repository.fillSearchKeys(eq(1L), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
			.thenReturn(1);
		when(repository.fillSearchKeys(eq(2L), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
			.thenThrow(new IllegalStateException("값이 너무 깁니다"));
		when(repository.fillSearchKeys(eq(3L), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
			.thenReturn(1);

		PlaceSearchKeyBackfill.Result result = backfill.backfill();

		assertThat(result).isEqualTo(new PlaceSearchKeyBackfill.Result(2, 1));
		// 실패한 행 뒤의 행까지 시도했다
		verify(repository).fillSearchKeys(eq(3L), org.mockito.ArgumentMatchers.anyString(),
				org.mockito.ArgumentMatchers.any());
	}

	@Test
	@DisplayName("#195-S19 못 채운 행이 계속 비어 있어도 마지막 id 뒤부터 읽어 끝까지 나아가고, 같은 행을 다시 읽지 않는다")
	void movesForwardEvenIfRowsStayEmpty() {
		when(repository.findTop500ByNameKeyIsNullAndIdGreaterThanOrderByIdAsc(0L))
			.thenReturn(List.of(entity(10, "가"), entity(11, "나")));
		when(repository.findTop500ByNameKeyIsNullAndIdGreaterThanOrderByIdAsc(11L)).thenReturn(List.of(entity(12, "다")));
		when(repository.findTop500ByNameKeyIsNullAndIdGreaterThanOrderByIdAsc(12L)).thenReturn(List.of());
		when(repository.fillSearchKeys(anyLong(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
			.thenThrow(new IllegalStateException("실패"));

		PlaceSearchKeyBackfill.Result result = backfill.backfill();

		// 모두 실패해도 무한히 반복하지 않고 세 행을 한 번씩만 시도하고 끝난다
		assertThat(result).isEqualTo(new PlaceSearchKeyBackfill.Result(0, 3));
		verify(repository).findTop500ByNameKeyIsNullAndIdGreaterThanOrderByIdAsc(0L);
		verify(repository).findTop500ByNameKeyIsNullAndIdGreaterThanOrderByIdAsc(11L);
		verify(repository).findTop500ByNameKeyIsNullAndIdGreaterThanOrderByIdAsc(12L);
	}

	@Test
	@DisplayName("#195-S19 채우기가 통째로 실패해도(예: DB 연결) 서버 시작은 막지 않는다")
	void doesNotBlockStartupWhenEverythingFails() {
		when(repository.findTop500ByNameKeyIsNullAndIdGreaterThanOrderByIdAsc(0L))
			.thenThrow(new IllegalStateException("DB에 연결할 수 없습니다"));

		assertThatCode(() -> backfill.run(new DefaultApplicationArguments())).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("#195-S19 시작할 때 조회가 실패해도 DB가 돌아오면 서버를 다시 켜지 않고, 공용 예약 스레드 밖의 전용 스레드가 다시 시도해 채운다")
	void retriesOnItsOwnThreadAfterStartupFailureOnceTheDatabaseRecovers() {
		when(repository.findTop500ByNameKeyIsNullAndIdGreaterThanOrderByIdAsc(0L))
			.thenThrow(new IllegalStateException("DB에 연결할 수 없습니다"))
			.thenReturn(List.of(entity(1, "하나")))
			.thenReturn(List.of());
		when(repository.findTop500ByNameKeyIsNullAndIdGreaterThanOrderByIdAsc(1L)).thenReturn(List.of());
		when(repository.fillSearchKeys(eq(1L), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
			.thenReturn(1);

		backfill.run(new DefaultApplicationArguments());
		// 시작할 때는 채우지 못했고, 호출한 쪽을 붙잡지 않고 바로 돌아온다
		verify(repository, times(0)).fillSearchKeys(anyLong(), org.mockito.ArgumentMatchers.anyString(),
				org.mockito.ArgumentMatchers.any());

		// 정해진 시간 안에 전용 스레드가 다시 시도해 채운다
		verify(repository, timeout(5000)).fillSearchKeys(eq(1L), org.mockito.ArgumentMatchers.anyString(),
				org.mockito.ArgumentMatchers.any());
		// 다 채웠으면 더 시도하지 않는다(시작 1번 + 다시 시도 1번)
		verify(repository, after(300).times(2)).findTop500ByNameKeyIsNullAndIdGreaterThanOrderByIdAsc(0L);
	}

	@Test
	@DisplayName("#195-S19 계속 실패해도 다시 시도는 정해진 횟수까지만 한다")
	void stopsRetryingAfterTheLimit() {
		when(repository.findTop500ByNameKeyIsNullAndIdGreaterThanOrderByIdAsc(0L))
			.thenThrow(new IllegalStateException("DB에 연결할 수 없습니다"));

		backfill.run(new DefaultApplicationArguments());

		// 시작할 때 한 번 + 다시 시도 12번에서 멈춘다
		verify(repository, timeout(5000).times(1 + PlaceSearchKeyBackfill.MAX_RETRIES))
			.findTop500ByNameKeyIsNullAndIdGreaterThanOrderByIdAsc(0L);
		verify(repository, after(300).times(1 + PlaceSearchKeyBackfill.MAX_RETRIES))
			.findTop500ByNameKeyIsNullAndIdGreaterThanOrderByIdAsc(0L);
	}

	@Test
	@DisplayName("#195-S19 시작할 때 다 채웠으면 다시 시도하는 일이 없다")
	void doesNotRetryWhenStartupSucceeded() {
		when(repository.findTop500ByNameKeyIsNullAndIdGreaterThanOrderByIdAsc(0L)).thenReturn(List.of());

		backfill.run(new DefaultApplicationArguments());

		verify(repository, after(300).times(1)).findTop500ByNameKeyIsNullAndIdGreaterThanOrderByIdAsc(0L);
	}

	@Test
	@DisplayName("#195-S19 다시 시도는 호출한 스레드가 아니라 이름이 붙은 전용 스레드에서 돈다")
	void retriesOnTheDedicatedThread() throws Exception {
		java.util.concurrent.CompletableFuture<String> threadName = new java.util.concurrent.CompletableFuture<>();
		when(repository.findTop500ByNameKeyIsNullAndIdGreaterThanOrderByIdAsc(0L)).thenAnswer(invocation -> {
			if (!Thread.currentThread().getName().equals("place-search-key-backfill-retry")) {
				throw new IllegalStateException("DB에 연결할 수 없습니다");
			}
			threadName.complete(Thread.currentThread().getName());
			return List.of();
		});

		backfill.run(new DefaultApplicationArguments());

		assertThat(threadName.get(5, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo("place-search-key-backfill-retry");
	}

}
