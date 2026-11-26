package com.pawmento.map.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.pawmento.map.application.exception.PublicDataSyncFailedException;
import com.pawmento.map.application.exception.PlaceSyncAdminDeniedException;
import com.pawmento.map.application.exception.PlaceSyncLockLostException;
import com.pawmento.map.application.port.in.SyncPublicDataPlacesCommand;
import com.pawmento.map.application.port.in.SyncPublicDataPlacesResult;
import com.pawmento.map.application.port.out.AppliedPlaces;
import com.pawmento.map.application.port.out.CheckPlaceSyncAdminPort;
import com.pawmento.map.application.port.out.LocalDataSnapshot;
import com.pawmento.map.application.port.out.ShelterSnapshot;
import com.pawmento.map.application.port.out.PlaceSyncLockPort;
import com.pawmento.map.domain.LocalDataRecord;
import com.pawmento.map.domain.PlaceCategory;
import com.pawmento.map.domain.PlaceDraft;
import com.pawmento.map.domain.ShelterRecord;

// 시나리오는 docs/map/animal-places.md(#172-S1, S4, S5, S20)에 있다. 가져오기의 판단(신뢰할 수 있는 전체 스냅샷인가, 무엇을 몇 건 반영하는가)을
// 가짜 포트로 확인한다. 실제 저장과 한 트랜잭션 반영은 PlaceSyncPersistenceTest가 확인한다
class SyncPublicDataPlacesServiceTest {

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

	private static SyncPublicDataPlacesService service(LocalDataSnapshot snapshot, FakeStore store) {
		return newService(category -> snapshot, new StorePlacesPortStub(store), new FakeLocks(), new FakeAdmin());
	}

	private static SyncPublicDataPlacesResult sync(SyncPublicDataPlacesService service) {
		return service.sync(new SyncPublicDataPlacesCommand("HOSPITAL"));
	}

	// 병원·미용만 확인하는 시험은 보호센터 포트를 쓰지 않는다. 쓰면 바로 실패한다
	private static SyncPublicDataPlacesService newService(com.pawmento.map.application.port.out.LoadLocalDataPort loader,
			com.pawmento.map.application.port.out.StorePlacesPort store, PlaceSyncLockPort locks,
			CheckPlaceSyncAdminPort admin) {
		return newService(loader, () -> {
			throw new AssertionError("보호센터를 부르면 안 되는 시험입니다");
		}, store, locks, admin);
	}

	private static SyncPublicDataPlacesService newService(com.pawmento.map.application.port.out.LoadLocalDataPort loader,
			com.pawmento.map.application.port.out.LoadShelterDataPort shelters,
			com.pawmento.map.application.port.out.StorePlacesPort store, PlaceSyncLockPort locks,
			CheckPlaceSyncAdminPort admin) {
		return new SyncPublicDataPlacesService(loader, shelters, store, locks, admin);
	}

	private static ShelterRecord shelter(String id) {
		return new ShelterRecord(id, "센터 " + id, "서울특별시 종로구 1", "02-733-9006", "37.57", "126.98", "09:00", "18:00",
				"토요일+일요일", "2026-01-02");
	}

	private static SyncPublicDataPlacesService shelterService(List<ShelterRecord> rows, FakeStore store) {
		return newService(category -> {
			throw new AssertionError("병원·미용을 부르면 안 되는 시험입니다");
		}, () -> new ShelterSnapshot(500, rows), new StorePlacesPortStub(store), new FakeLocks(), new FakeAdmin());
	}

	@Test
	@DisplayName("#172-S8 보호센터 3곳(좌표 있음 2, 위도·경도가 빈 곳 1)을 받으면 읽은 3, 저장 2, 제외 1(좌표 없음)을 센다")
	void shelterCountsReadSavedAndExcluded() {
		var store = new FakeStore();
		var rows = List.of(shelter("a"), shelter("b"),
				new ShelterRecord("c", "좌표 없는 센터", "주소", null, "", "", "09:00", "18:00", null, null));

		var result = shelterService(rows, store).sync(new SyncPublicDataPlacesCommand("SHELTER"));

		assertThat(result.category()).isEqualTo("SHELTER");
		assertThat(result.read()).isEqualTo(3);
		assertThat(result.saved()).isEqualTo(2);
		assertThat(result.excluded()).isEqualTo(1);
		assertThat(result.noCoordinates()).isEqualTo(1);
		assertThat(result.closed()).isZero();
		assertThat(store.calls.get(0)).extracting(PlaceDraft::sourceId).containsExactly("a", "b");
		assertThat(store.calls.get(0)).allSatisfy(draft -> assertThat(draft.category()).isEqualTo(PlaceCategory.SHELTER));
	}

	@Test
	@DisplayName("#172-S9 totalCount가 없어도 받은 건수가 요청한 수(500)보다 적으면 끝까지 받은 것으로 보고 반영한다")
	void shelterIsCompleteWhenFewerRowsThanRequested() {
		var store = new FakeStore();

		var result = shelterService(List.of(shelter("a")), store).sync(new SyncPublicDataPlacesCommand("SHELTER"));

		assertThat(result.saved()).isEqualTo(1);
	}

	@Test
	@DisplayName("#172-S9 받은 행이 하나도 없으면(서비스 오류) 센터가 모두 사라졌다고 보지 않고 아무것도 바꾸지 않은 채 실패한다")
	void shelterRejectsEmptyResponse() {
		var store = new FakeStore();
		store.existing = 339;

		assertThatThrownBy(() -> shelterService(List.of(), store).sync(new SyncPublicDataPlacesCommand("SHELTER")))
			.isInstanceOfSatisfying(PublicDataSyncFailedException.class,
					e -> assertThat(e.reason()).isEqualTo(PublicDataSyncFailedException.Reason.EMPTY_SNAPSHOT));
		assertThat(store.calls).isEmpty();
	}

	@Test
	@DisplayName("#172-S9 요청한 만큼 꽉 차게 왔으면(뒤에 더 있을 수 있음) 반영하지 않고 실패한다")
	void shelterRejectsFullPage() {
		var store = new FakeStore();
		var rows = new ArrayList<ShelterRecord>();
		for (int i = 0; i < 500; i++) {
			rows.add(shelter("id-" + i));
		}

		assertThatThrownBy(() -> shelterService(rows, store).sync(new SyncPublicDataPlacesCommand("SHELTER")))
			.isInstanceOfSatisfying(PublicDataSyncFailedException.class,
					e -> assertThat(e.reason()).isEqualTo(PublicDataSyncFailedException.Reason.POSSIBLY_TRUNCATED));
		assertThat(store.calls).isEmpty();
	}

	@Test
	@DisplayName("#172-S9 등록번호가 겹치거나 필수 항목(등록번호·이름)이 빠진 행이 있으면 아무것도 반영하지 않고 실패한다")
	void shelterRejectsDuplicateIdsAndMissingFields() {
		var store = new FakeStore();

		assertThatThrownBy(() -> shelterService(List.of(shelter("a"), shelter("a")), store)
			.sync(new SyncPublicDataPlacesCommand("SHELTER")))
			.isInstanceOfSatisfying(PublicDataSyncFailedException.class,
					e -> assertThat(e.reason()).isEqualTo(PublicDataSyncFailedException.Reason.DUPLICATE_IDS));
		var noName = new ShelterRecord("x", null, "주소", null, "37.5", "127.0", null, null, null, null);
		assertThatThrownBy(() -> shelterService(List.of(shelter("a"), noName), store)
			.sync(new SyncPublicDataPlacesCommand("SHELTER")))
			.isInstanceOfSatisfying(PublicDataSyncFailedException.class,
					e -> assertThat(e.reason()).isEqualTo(PublicDataSyncFailedException.Reason.MISSING_FIELDS));
		assertThat(store.calls).isEmpty();
	}

	@Test
	@DisplayName("#172-S9 위도·경도 항목이 아예 없는 센터는 실패시키지 않고 좌표 없음으로 제외하며, 모든 센터가 좌표가 없으면 지도를 비우지 않고 다시 시도하지 않는 이유(MISSING_FIELDS)로 실패한다")
	void shelterWithoutCoordinateFieldsIsExcludedNotRejected() {
		var store = new FakeStore();
		var noCoordinates = new ShelterRecord("x", "좌표 항목 없는 센터", "주소", null, null, null, null, null, null, null);

		var result = shelterService(List.of(shelter("a"), noCoordinates), store)
			.sync(new SyncPublicDataPlacesCommand("SHELTER"));

		assertThat(result.read()).isEqualTo(2);
		assertThat(result.saved()).isEqualTo(1);
		assertThat(result.noCoordinates()).isEqualTo(1);

		assertThatThrownBy(() -> shelterService(List.of(noCoordinates), new FakeStore())
			.sync(new SyncPublicDataPlacesCommand("SHELTER")))
			.isInstanceOfSatisfying(PublicDataSyncFailedException.class,
					e -> {
						assertThat(e.reason()).isEqualTo(PublicDataSyncFailedException.Reason.MISSING_FIELDS);
						assertThat(e.retryable()).isFalse();
					});
	}

	private static List<ShelterRecord> shelters(int count) {
		var rows = new ArrayList<ShelterRecord>();
		for (int i = 0; i < count; i++) {
			rows.add(shelter("s-" + i));
		}
		return rows;
	}

	@Test
	@DisplayName("#172-S9 보호센터는 저장할 건수가 기존의 90% 이상일 때만 반영한다. 기존 336곳이면 303곳은 반영하고, 302곳이나 일부만 온 200곳은 지도를 비우지 않고 실패한다")
	void shelterKeepsAtLeastNinetyPercent() {
		var store = new FakeStore();
		store.existing = 336;

		assertThat(shelterService(shelters(303), store).sync(new SyncPublicDataPlacesCommand("SHELTER")).saved())
			.isEqualTo(303);
		for (int count : new int[] { 302, 200 }) {
			assertThatThrownBy(() -> shelterService(shelters(count), store).sync(new SyncPublicDataPlacesCommand("SHELTER")))
				.isInstanceOfSatisfying(PublicDataSyncFailedException.class,
						e -> assertThat(e.reason()).isEqualTo(PublicDataSyncFailedException.Reason.TOO_FEW_PLACES));
		}
		// 반영은 303곳일 때 한 번뿐이다
		assertThat(store.calls).hasSize(1);
	}

	@Test
	@DisplayName("#172-S9 병원·미용은 totalCount 검사가 따로 있어서 기존의 절반 이상이면 반영한다(보호센터의 90% 기준은 적용하지 않는다)")
	void permitsStillUseHalf() {
		var store = new FakeStore();
		store.existing = 10;

		var result = sync(service(new LocalDataSnapshot(5, 5, actives(5)), store));

		assertThat(result.saved()).isEqualTo(5);
	}

	@Test
	@DisplayName("#172-S22 보호센터도 종류별 잠금을 받기 전에 잡고 저장한 뒤에 놓는다")
	void shelterHoldsTheLock() {
		var locks = new FakeLocks();
		var store = new FakeStore();
		var service = newService(category -> {
			throw new AssertionError("병원·미용을 부르면 안 되는 시험입니다");
		}, () -> {
			locks.events.add("load SHELTER");
			return new ShelterSnapshot(500, List.of(shelter("a")));
		}, new StorePlacesPortStub(store), locks, new FakeAdmin());

		service.sync(new SyncPublicDataPlacesCommand("SHELTER"));

		assertThat(locks.events).containsExactly("acquire SHELTER", "load SHELTER", "release SHELTER");
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
	@DisplayName("#172-S5 거르기 전의 고유 관리번호 수가 totalCount와 다르면 아무것도 반영하지 않고 실패한다")
	void rejectsUniqueCountMismatch() {
		var store = new FakeStore();

		assertThatThrownBy(() -> sync(service(new LocalDataSnapshot(4, 4, actives(3)), store)))
			.isInstanceOfSatisfying(PublicDataSyncFailedException.class,
					e -> assertThat(e.reason()).isEqualTo(PublicDataSyncFailedException.Reason.TOTAL_COUNT_MISMATCH));
		assertThat(store.calls).isEmpty();
	}

	@Test
	@DisplayName("#172-S5 수집하는 동안 totalCount가 바뀌면 아무것도 반영하지 않고 실패한다")
	void rejectsChangedTotalCount() {
		var store = new FakeStore();

		assertThatThrownBy(() -> sync(service(new LocalDataSnapshot(3, 4, actives(3)), store)))
			.isInstanceOfSatisfying(PublicDataSyncFailedException.class,
					e -> assertThat(e.reason()).isEqualTo(PublicDataSyncFailedException.Reason.TOTAL_COUNT_CHANGED));
		assertThat(store.calls).isEmpty();
	}

	@Test
	@DisplayName("#172-S5 필수 항목이 빠진 행이 하나라도 있으면 아무것도 반영하지 않고 실패한다")
	void rejectsMissingFields() {
		var rows = new ArrayList<>(actives(2));
		rows.add(new LocalDataRecord("x", "영업/정상", null, "주소", null, null, X, Y, null));
		var store = new FakeStore();

		assertThatThrownBy(() -> sync(service(new LocalDataSnapshot(3, 3, rows), store)))
			.isInstanceOfSatisfying(PublicDataSyncFailedException.class,
					e -> assertThat(e.reason()).isEqualTo(PublicDataSyncFailedException.Reason.MISSING_FIELDS));
		assertThat(store.calls).isEmpty();
	}

	@Test
	@DisplayName("#172-S5 거른 뒤 저장할 건수가 기존의 절반 미만이면 실패하고, 절반이면 반영한다. 기존이 없으면 건수와 상관없다")
	void rejectsTooFewPlaces() {
		var store = new FakeStore();
		store.existing = 10;

		assertThatThrownBy(() -> sync(service(new LocalDataSnapshot(4, 4, actives(4)), store)))
			.isInstanceOfSatisfying(PublicDataSyncFailedException.class,
					e -> assertThat(e.reason()).isEqualTo(PublicDataSyncFailedException.Reason.TOO_FEW_PLACES));
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
			.isInstanceOfSatisfying(PublicDataSyncFailedException.class,
					e -> assertThat(e.reason()).isEqualTo(PublicDataSyncFailedException.Reason.TOO_FEW_PLACES));
	}

	@Test
	@DisplayName("#172-S5 중간 페이지 호출이 실패하면 그 실패가 그대로 올라오고 아무것도 반영하지 않는다")
	void fetchFailureStopsEverything() {
		var store = new FakeStore();
		var failing = newService(category -> {
			throw PublicDataSyncFailedException.fetchFailed(new RuntimeException("시간 초과"));
		}, new StorePlacesPortStub(store), new FakeLocks(), new FakeAdmin());

		assertThatThrownBy(() -> sync(failing)).isInstanceOfSatisfying(PublicDataSyncFailedException.class,
				e -> assertThat(e.reason()).isEqualTo(PublicDataSyncFailedException.Reason.FETCH_FAILED));
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
			.isInstanceOfSatisfying(PublicDataSyncFailedException.class,
					e -> assertThat(e.reason()).isEqualTo(PublicDataSyncFailedException.Reason.TOTAL_COUNT_MISMATCH));
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
			.isInstanceOfSatisfying(PublicDataSyncFailedException.class,
					e -> assertThat(e.reason()).isEqualTo(PublicDataSyncFailedException.Reason.TOTAL_COUNT_MISMATCH));
		assertThat(store.calls).isEmpty();
	}

	@Test
	@DisplayName("#172-S22 병원·미용·보호센터만 가져올 수 있고, 그 밖의 종류(소문자, 없는 종류, null)는 호출한 쪽의 버그로 거절한다")
	void supportsOnlyKnownCategories() {
		var store = new FakeStore();
		var service = service(new LocalDataSnapshot(1, 1, actives(1)), store);

		assertThat(service.sync(new SyncPublicDataPlacesCommand("GROOMING")).category()).isEqualTo("GROOMING");
		assertThatThrownBy(() -> service.sync(new SyncPublicDataPlacesCommand("hospital")))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> service.sync(new SyncPublicDataPlacesCommand("CAFE")))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> service.sync(new SyncPublicDataPlacesCommand(null)))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("#172-S22 잠금은 받기 전에 잡고 저장한 뒤에 놓으며, 종류마다 따로 잡는다")
	void holdsTheLockAroundTheWholeRun() {
		var locks = new FakeLocks();
		var events = locks.events;
		var store = new FakeStore();
		var service = newService(category -> {
			events.add("load " + category);
			return new LocalDataSnapshot(1, 1, actives(1));
		}, new StorePlacesPortStub(store) {
			@Override
			public AppliedPlaces replace(PlaceCategory category, List<PlaceDraft> drafts) {
				events.add("replace " + category);
				return super.replace(category, drafts);
			}
		}, locks, new FakeAdmin());

		service.sync(new SyncPublicDataPlacesCommand("GROOMING"));

		assertThat(events).containsExactly("acquire GROOMING", "load GROOMING", "replace GROOMING", "release GROOMING");
	}

	@Test
	@DisplayName("#172-S22 받기가 실패해도 잠금을 놓는다. 안 놓으면 다음 실행이 영영 막힌다")
	void releasesTheLockWhenTheRunFails() {
		var locks = new FakeLocks();
		var service = newService(category -> {
			throw PublicDataSyncFailedException.fetchFailed(new RuntimeException("시간 초과"));
		}, new StorePlacesPortStub(new FakeStore()), locks, new FakeAdmin());

		assertThatThrownBy(() -> sync(service)).isInstanceOf(PublicDataSyncFailedException.class);

		assertThat(locks.events).containsExactly("acquire HOSPITAL", "release HOSPITAL");
	}

	@Test
	@DisplayName("#172-S22 관리자가 요청하면 현재 관리자인지 확인하고, 아니면 받지도 잠그지도 않는다. 시스템(자동 실행)은 확인하지 않는다")
	void checksAdminOnlyForManualRuns() {
		var admin = new FakeAdmin();
		var locks = new FakeLocks();
		var store = new FakeStore();
		boolean[] loaded = { false };
		var service = newService(category -> {
			loaded[0] = true;
			return new LocalDataSnapshot(1, 1, actives(1));
		}, new StorePlacesPortStub(store), locks, admin);

		service.sync(new SyncPublicDataPlacesCommand("HOSPITAL"));
		assertThat(admin.checked).isEmpty();

		service.sync(new SyncPublicDataPlacesCommand(7L, "HOSPITAL"));
		assertThat(admin.checked).containsExactly(7L);

		admin.allow = false;
		loaded[0] = false;
		locks.events.clear();
		assertThatThrownBy(() -> service.sync(new SyncPublicDataPlacesCommand(8L, "HOSPITAL")))
			.isInstanceOf(PlaceSyncAdminDeniedException.class);
		assertThat(loaded[0]).isFalse();
		assertThat(locks.events).isEmpty();
	}

	@Test
	@DisplayName("#172-S22 받는 동안 잠금을 잃었으면 저장하지 않고, 잠금은 그래도 정리한다")
	void doesNotStoreWhenTheLockWasLost() {
		var locks = new FakeLocks();
		locks.lost = true;
		var store = new FakeStore();
		var service = new SyncPublicDataPlacesService(category -> new LocalDataSnapshot(1, 1, actives(1)),
				() -> null, new StorePlacesPortStub(store), locks, new FakeAdmin());

		assertThatThrownBy(() -> sync(service)).isInstanceOf(PlaceSyncLockLostException.class);

		assertThat(store.calls).isEmpty();
		assertThat(locks.events).containsExactly("acquire HOSPITAL", "release HOSPITAL");
	}

	// 가짜 잠금: 잡고 놓은 순서를 기록한다. available=false면 이미 다른 곳이 돌리는 중이다
	private static class FakeLocks implements PlaceSyncLockPort {

		boolean available = true;

		boolean lost = false;

		final List<String> events = new ArrayList<>();

		@Override
		public Optional<SyncLock> tryAcquire(PlaceCategory category) {
			events.add("acquire " + category);
			if (!available) {
				return Optional.empty();
			}
			return Optional.of(new SyncLock() {
				@Override
				public void requireHeld() {
					if (lost) {
						throw new PlaceSyncLockLostException(new RuntimeException("연결 끊김"));
					}
				}

				@Override
				public void close() {
					events.add("release " + category);
				}
			});
		}

	}

	private static class FakeAdmin implements CheckPlaceSyncAdminPort {

		final List<Long> checked = new ArrayList<>();

		boolean allow = true;

		@Override
		public void requireAdmin(Long memberId) {
			checked.add(memberId);
			if (!allow) {
				throw new PlaceSyncAdminDeniedException();
			}
		}

	}

	// FakeStore를 StorePlacesPort로 쓰는 얇은 어댑터
	private static class StorePlacesPortStub implements com.pawmento.map.application.port.out.StorePlacesPort {

		private final FakeStore store;

		StorePlacesPortStub(FakeStore store) {
			this.store = store;
		}

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
