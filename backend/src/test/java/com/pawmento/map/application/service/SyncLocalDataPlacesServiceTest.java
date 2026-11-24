package com.pawmento.map.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.pawmento.map.application.exception.LocalDataSyncFailedException;
import com.pawmento.map.application.port.in.SyncLocalDataPlacesCommand;
import com.pawmento.map.application.port.in.SyncLocalDataPlacesResult;
import com.pawmento.map.application.port.out.AppliedPlaces;
import com.pawmento.map.application.port.out.LocalDataSnapshot;
import com.pawmento.map.domain.LocalDataRecord;
import com.pawmento.map.domain.PlaceCategory;
import com.pawmento.map.domain.PlaceDraft;

// 시나리오는 docs/map/animal-places.md(#172-S1, S4, S5, S20)에 있다. 가져오기의 판단(신뢰할 수 있는 전체 스냅샷인가, 무엇을 몇 건 반영하는가)을
// 가짜 포트로 확인한다. 실제 저장과 한 트랜잭션 반영은 PlaceSyncPersistenceTest가 확인한다
class SyncLocalDataPlacesServiceTest {

	// 대학로동물병원의 좌표
	private static final String X = "199947.178659";
	private static final String Y = "453593.826987";

	private static LocalDataRecord active(String id) {
		return new LocalDataRecord(id, "영업/정상", "병원 " + id, "서울특별시 종로구 1", null, "0222733233", X, Y,
				"2026-11-21 21:31:41");
	}

	private static List<LocalDataRecord> actives(int count) {
		List<LocalDataRecord> rows = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			rows.add(active("id-" + i));
		}
		return rows;
	}

	// 가짜 저장소: 기존 건수를 정해 두고, 반영 호출을 기록한다
	private static class FakeStore {

		int existing;

		AppliedPlaces applied = new AppliedPlaces(0, 0, 0);

		final List<List<PlaceDraft>> calls = new ArrayList<>();

		int count(PlaceCategory category) {
			return existing;
		}

		AppliedPlaces replace(PlaceCategory category, List<PlaceDraft> drafts) {
			calls.add(drafts);
			return applied;
		}

	}

	private static SyncLocalDataPlacesService service(LocalDataSnapshot snapshot, FakeStore store) {
		return new SyncLocalDataPlacesService(category -> snapshot, new StorePlacesPortStub(store));
	}

	private static SyncLocalDataPlacesResult sync(SyncLocalDataPlacesService service) {
		return service.sync(new SyncLocalDataPlacesCommand("HOSPITAL"));
	}

	@Test
	@DisplayName("#172-S1 읽은 행 3, 저장 1, 제외 2(폐업 1, 좌표 없음 1)를 센다")
	void countsReadSavedAndExcluded() {
		var rows = List.of(active("a"),
				new LocalDataRecord("b", "폐업", "폐업병원", "주소", null, null, X, Y, null),
				new LocalDataRecord("c", "영업/정상", "좌표없는병원", "주소", null, null, "", "", null));
		var store = new FakeStore();
		store.applied = new AppliedPlaces(1, 0, 0);

		var result = sync(service(new LocalDataSnapshot(3, 3, rows), store));

		assertThat(result.category()).isEqualTo("HOSPITAL");
		assertThat(result.read()).isEqualTo(3);
		assertThat(result.saved()).isEqualTo(1);
		assertThat(result.excluded()).isEqualTo(2);
		assertThat(result.closed()).isEqualTo(1);
		assertThat(result.noCoordinates()).isEqualTo(1);
		assertThat(result.outOfRange()).isZero();
		assertThat(store.calls).hasSize(1);
		assertThat(store.calls.get(0)).extracting(PlaceDraft::sourceId).containsExactly("a");
	}

	@Test
	@DisplayName("#172-S4 저장소가 알려 준 추가·갱신·삭제 건수를 결과에 담는다")
	void reportsInsertedUpdatedDeleted() {
		var store = new FakeStore();
		store.existing = 3;
		store.applied = new AppliedPlaces(1, 2, 4);

		var result = sync(service(new LocalDataSnapshot(3, 3, actives(3)), store));

		assertThat(result.inserted()).isEqualTo(1);
		assertThat(result.updated()).isEqualTo(2);
		assertThat(result.deleted()).isEqualTo(4);
	}

	@Test
	@DisplayName("#172-S5 거르기 전의 고유 관리번호 수가 totalCount와 다르면 아무것도 반영하지 않고 실패한다")
	void rejectsUniqueCountMismatch() {
		var store = new FakeStore();

		assertThatThrownBy(() -> sync(service(new LocalDataSnapshot(4, 4, actives(3)), store)))
			.isInstanceOfSatisfying(LocalDataSyncFailedException.class,
					e -> assertThat(e.reason()).isEqualTo(LocalDataSyncFailedException.Reason.TOTAL_COUNT_MISMATCH));
		assertThat(store.calls).isEmpty();
	}

	@Test
	@DisplayName("#172-S5 수집하는 동안 totalCount가 바뀌면 아무것도 반영하지 않고 실패한다")
	void rejectsChangedTotalCount() {
		var store = new FakeStore();

		assertThatThrownBy(() -> sync(service(new LocalDataSnapshot(3, 4, actives(3)), store)))
			.isInstanceOfSatisfying(LocalDataSyncFailedException.class,
					e -> assertThat(e.reason()).isEqualTo(LocalDataSyncFailedException.Reason.TOTAL_COUNT_CHANGED));
		assertThat(store.calls).isEmpty();
	}

	@Test
	@DisplayName("#172-S5 필수 항목이 빠진 행이 하나라도 있으면 아무것도 반영하지 않고 실패한다")
	void rejectsMissingFields() {
		var rows = new ArrayList<>(actives(2));
		rows.add(new LocalDataRecord("x", "영업/정상", null, "주소", null, null, X, Y, null));
		var store = new FakeStore();

		assertThatThrownBy(() -> sync(service(new LocalDataSnapshot(3, 3, rows), store)))
			.isInstanceOfSatisfying(LocalDataSyncFailedException.class,
					e -> assertThat(e.reason()).isEqualTo(LocalDataSyncFailedException.Reason.MISSING_FIELDS));
		assertThat(store.calls).isEmpty();
	}

	@Test
	@DisplayName("#172-S5 거른 뒤 저장할 건수가 기존의 절반 미만이면 실패하고, 절반이면 반영한다. 기존이 없으면 건수와 상관없다")
	void rejectsTooFewPlaces() {
		var store = new FakeStore();
		store.existing = 10;

		assertThatThrownBy(() -> sync(service(new LocalDataSnapshot(4, 4, actives(4)), store)))
			.isInstanceOfSatisfying(LocalDataSyncFailedException.class,
					e -> assertThat(e.reason()).isEqualTo(LocalDataSyncFailedException.Reason.TOO_FEW_PLACES));
		assertThat(store.calls).isEmpty();

		assertThat(sync(service(new LocalDataSnapshot(5, 5, actives(5)), store)).saved()).isEqualTo(5);
		assertThat(store.calls).hasSize(1);

		var empty = new FakeStore();
		assertThat(sync(service(new LocalDataSnapshot(1, 1, actives(1)), empty)).saved()).isEqualTo(1);
	}

	@Test
	@DisplayName("#172-S5 폐업 행이 많아도 totalCount와 고유 관리번호가 맞으면 신뢰할 수 있는 스냅샷이고, 모두 제외돼 기존의 절반 미만이면 막는다")
	void closedRowsCountTowardsTheSnapshotButNotTheHalfRule() {
		var closed = new ArrayList<LocalDataRecord>();
		for (int i = 0; i < 4; i++) {
			closed.add(new LocalDataRecord("c-" + i, "폐업", "폐업병원", "주소", null, null, X, Y, null));
		}
		var store = new FakeStore();
		store.existing = 10;

		assertThatThrownBy(() -> sync(service(new LocalDataSnapshot(4, 4, closed), store)))
			.isInstanceOfSatisfying(LocalDataSyncFailedException.class,
					e -> assertThat(e.reason()).isEqualTo(LocalDataSyncFailedException.Reason.TOO_FEW_PLACES));
	}

	@Test
	@DisplayName("#172-S5 중간 페이지 호출이 실패하면 그 실패가 그대로 올라오고 아무것도 반영하지 않는다")
	void fetchFailureStopsEverything() {
		var store = new FakeStore();
		var failing = new SyncLocalDataPlacesService(category -> {
			throw LocalDataSyncFailedException.fetchFailed(new RuntimeException("시간 초과"));
		}, new StorePlacesPortStub(store));

		assertThatThrownBy(() -> sync(failing)).isInstanceOfSatisfying(LocalDataSyncFailedException.class,
				e -> assertThat(e.reason()).isEqualTo(LocalDataSyncFailedException.Reason.FETCH_FAILED));
		assertThat(store.calls).isEmpty();
	}

	@Test
	@DisplayName("#172-S20 페이지가 겹쳐 같은 행이 두 번 와도 관리번호로 합쳐서 세고, 합친 뒤 고유 수가 totalCount와 같을 때만 반영한다")
	void mergesDuplicateRowsFromOverlappingPages() {
		var rows = List.of(active("a"), active("b"), active("b"), active("c"));
		var store = new FakeStore();

		var result = sync(service(new LocalDataSnapshot(3, 3, rows), store));

		assertThat(result.read()).isEqualTo(3);
		assertThat(store.calls.get(0)).extracting(PlaceDraft::sourceId).containsExactly("a", "b", "c");
	}

	@Test
	@DisplayName("#172-S20 관리번호는 앞뒤 공백을 지운 값으로 합쳐서 센다. 공백만 다른 `a`와 `a `를 서로 다른 둘로 세어 totalCount 검사를 통과하지 않는다")
	void mergesIdsThatDifferOnlyByWhitespace() {
		var store = new FakeStore();

		// 하나로 합치면 고유 수가 1이라 totalCount 2와 다르다. 둘로 세어 통과하면 저장소에 같은 관리번호가 두 번 간다
		assertThatThrownBy(() -> sync(service(new LocalDataSnapshot(2, 2, List.of(active("a"), active("a "))), store)))
			.isInstanceOfSatisfying(LocalDataSyncFailedException.class,
					e -> assertThat(e.reason()).isEqualTo(LocalDataSyncFailedException.Reason.TOTAL_COUNT_MISMATCH));
		assertThat(store.calls).isEmpty();

		var result = sync(service(new LocalDataSnapshot(1, 1, List.of(active("a"), active("a "))), store));

		assertThat(result.read()).isEqualTo(1);
		assertThat(store.calls.get(0)).extracting(PlaceDraft::sourceId).containsExactly("a");
	}

	@Test
	@DisplayName("#172-S20 원시 건수는 맞아도 겹침 때문에 고유 관리번호가 모자라면(250건 중 240개) 아무것도 반영하지 않고 실패한다")
	void rejectsWhenOverlapsHideMissingIds() {
		var rows = List.of(active("a"), active("b"), active("b"), active("b"));
		var store = new FakeStore();

		assertThatThrownBy(() -> sync(service(new LocalDataSnapshot(4, 4, rows), store)))
			.isInstanceOfSatisfying(LocalDataSyncFailedException.class,
					e -> assertThat(e.reason()).isEqualTo(LocalDataSyncFailedException.Reason.TOTAL_COUNT_MISMATCH));
		assertThat(store.calls).isEmpty();
	}

	@Test
	@DisplayName("#172-S22 병원과 미용만 가져올 수 있고, 그 밖의 종류는 호출한 쪽의 버그로 거절한다")
	void supportsOnlyHospitalAndGrooming() {
		var store = new FakeStore();
		var service = service(new LocalDataSnapshot(1, 1, actives(1)), store);

		assertThat(service.sync(new SyncLocalDataPlacesCommand("GROOMING")).category()).isEqualTo("GROOMING");
		assertThatThrownBy(() -> service.sync(new SyncLocalDataPlacesCommand("SHELTER")))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> service.sync(new SyncLocalDataPlacesCommand("CAFE")))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> service.sync(new SyncLocalDataPlacesCommand(null)))
			.isInstanceOf(IllegalArgumentException.class);
	}

	// FakeStore를 StorePlacesPort로 쓰는 얇은 어댑터
	private record StorePlacesPortStub(FakeStore store) implements com.pawmento.map.application.port.out.StorePlacesPort {

		@Override
		public int countByCategory(PlaceCategory category) {
			return store.count(category);
		}

		@Override
		public AppliedPlaces replace(PlaceCategory category, List<PlaceDraft> drafts) {
			return store.replace(category, drafts);
		}

	}

}
