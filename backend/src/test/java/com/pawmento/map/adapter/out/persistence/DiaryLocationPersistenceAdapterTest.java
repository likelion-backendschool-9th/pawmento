package com.pawmento.map.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.pawmento.TestcontainersConfiguration;
import com.pawmento.map.domain.DiaryLocationState;
import com.pawmento.map.domain.DiaryLocationValue;
import com.pawmento.map.domain.MapArea;
import com.pawmento.map.domain.DiaryLocation;
import com.pawmento.map.domain.LocationSource;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

// 시나리오는 docs/map/record-location.md에 있다. API를 만들기 전이라 저장소가 지키는 부분만 확인한다.
// 진짜 PostgreSQL에서 확인하고, 각 테스트가 끝나면 되돌린다
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "pawmento.auth.jwt-secret=test-only-jwt-secret-0123456789-abcdefghij")
@Transactional
class DiaryLocationPersistenceAdapterTest {

	private static final long MEMBER = 1L;
	private static final long PET = 10L;

	@Autowired
	private DiaryLocationPersistenceAdapter adapter;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@PersistenceContext
	private EntityManager entityManager;

	// 같은 트랜잭션의 영속성 컨텍스트에서 읽으면 DB가 아니라 메모리에 있는 엔티티가 돌아온다.
	// 저장한 뒤에는 DB에 반영하고 컨텍스트를 비워서, 다음 조회가 실제 행을 읽게 한다
	private void flushAndClear() {
		entityManager.flush();
		entityManager.clear();
	}

	private Long insert(DiaryLocation location) {
		adapter.lock(location.getDiaryId());
		adapter.write(location.getDiaryId(), location.getPetId(), location.getCreatedBy(), location.getSourceRevision(),
			new DiaryLocationValue(location.getLatitude(), location.getLongitude(), location.getPlaceName()));
		return adapter.read(location.getDiaryId()).orElseThrow().rowId();
	}

	private Long save(long diaryId, long petId, long memberId, double latitude, double longitude) {
		Long id = insert(DiaryLocation.create(diaryId, petId, memberId, latitude, longitude, null));
		flushAndClear();
		return id;
	}

	@Test
	@DisplayName("#19-S1 위치를 저장하고 기록 번호로 다시 읽으면 같은 값이다")
	void saveAndLoad() {
		Long id = insert(DiaryLocation.create(100L, PET, MEMBER, 37.4979, 127.0276, "강남역"));
		flushAndClear();

		DiaryLocationState loaded = adapter.read(100L).orElseThrow();

		assertThat(id).isNotNull();
		assertThat(loaded.rowId()).isEqualTo(id);
		assertThat(loaded.diaryId()).isEqualTo(100L);
		assertThat(loaded.petId()).isEqualTo(PET);
		assertThat(loaded.createdBy()).isEqualTo(MEMBER);
		assertThat(loaded.latitude()).isEqualTo(37.4979);
		assertThat(loaded.longitude()).isEqualTo(127.0276);
		assertThat(loaded.placeName()).isEqualTo("강남역");
	}

	@Test
	@DisplayName("#19-S1 위치가 없는 기록은 빈 값을 받는다")
	void loadMissing() {
		assertThat(adapter.read(999L)).isEmpty();
	}

	@Test
	@DisplayName("#19-S1 같은 기록에 다시 저장하면 새로 쌓이지 않고 바뀐다")
	void saveAgainUpdates() {
		Long id = save(100L, PET, MEMBER, 37, 127);
		adapter.lock(100L);
		adapter.write(100L, PET, MEMBER, 1, new DiaryLocationValue(35.1796, 129.0756, "부산"));
		flushAndClear();
		var loaded = adapter.read(100L).orElseThrow();
		assertThat(loaded.rowId()).isEqualTo(id);
		assertThat(loaded.placeName()).isEqualTo("부산");
		assertThat(loaded.latitude()).isEqualTo(35.1796);
		assertThat(loaded.longitude()).isEqualTo(129.0756);
		assertThat(loaded.revision()).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject("select count(*) from diary_location", Long.class)).isEqualTo(1L);
	}

	@Test
	@DisplayName("#44-S13 새 위치를 저장하면 출처 MANUAL·revision 0으로 저장되고 다시 읽어도 같다. 새 열은 NOT NULL이고 기본값이 있다")
	void sourceColumnsAreStoredWithDefaults() {
		insert(DiaryLocation.create(300L, PET, MEMBER, 37.5, 127.0, "강남역"));
		flushAndClear();

		DiaryLocationState loaded = adapter.read(300L).orElseThrow();
		Map<String, Object> row = jdbcTemplate.queryForMap(
				"select source, source_asset_id, source_revision from diary_location where diary_id = 300");
		assertThat(loaded.source()).isEqualTo(LocationSource.MANUAL);
		assertThat(loaded.sourceAssetId()).isNull();
		assertThat(loaded.revision()).isZero();
		assertThat(row.get("source")).isEqualTo("MANUAL");
		assertThat(row.get("source_asset_id")).isNull();
		assertThat(((Number) row.get("source_revision")).longValue()).isZero();

		List<Map<String, Object>> columns = jdbcTemplate.queryForList("""
				select column_name, is_nullable, column_default
				from information_schema.columns
				where table_name = 'diary_location' and column_name in ('source', 'source_asset_id', 'source_revision')
				""");
		assertThat(columns).extracting(column -> column.get("column_name"))
			.containsExactlyInAnyOrder("source", "source_asset_id", "source_revision");
		for (Map<String, Object> column : columns) {
			String name = (String) column.get("column_name");
			// 출처 자산만 비어 있을 수 있다. 출처와 revision은 NOT NULL이고 기존 행을 채울 기본값이 있다
			assertThat(column.get("is_nullable")).as(name).isEqualTo("source_asset_id".equals(name) ? "YES" : "NO");
			assertThat(column.get("column_default") != null).as(name).isEqualTo(!"source_asset_id".equals(name));
		}
	}

	@Test
	@DisplayName("#19-S1 같은 기록의 중복 행은 DB 유일 제약이 막는다")
	void uniquePerRecord() {
		save(100L, PET, MEMBER, 37, 127);
		assertThatThrownBy(() -> jdbcTemplate.update("""
			insert into diary_location(diary_id,pet_id,created_by,latitude,longitude,created_at,updated_at)
			values(100,10,1,36,128,now(),now())
			""")).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("#19-S4 위치를 지우면 읽히지 않고, 지울 위치가 없어도 오류가 아니다")
	void delete() {
		save(100L, PET, MEMBER, 37.0, 127.0);

		adapter.remove(100L);
		adapter.remove(100L);

		assertThat(adapter.read(100L)).isEmpty();
	}

	@Test
	@DisplayName("#19-S4 위치를 지우면 그 기록의 핀이 영역 조회에서 사라진다")
	void deletedPinDisappears() {
		save(100L, PET, MEMBER, 37.5, 127.0);
		MapArea area = MapArea.of(37.0, 126.0, 38.0, 128.0);
		assertThat(adapter.loadInArea(MEMBER, PET, area, 10)).hasSize(1);

		adapter.remove(100L);

		assertThat(adapter.loadInArea(MEMBER, PET, area, 10)).isEmpty();
	}

	@Test
	@DisplayName("#19-S5 영역 안의 위치만 조회되고 영역 밖은 나오지 않는다")
	void onlyInsideArea() {
		save(1L, PET, MEMBER, 37.5, 127.0); // 안
		save(2L, PET, MEMBER, 36.9, 127.0); // 위도 아래
		save(3L, PET, MEMBER, 38.1, 127.0); // 위도 위
		save(4L, PET, MEMBER, 37.5, 125.9); // 경도 왼쪽
		save(5L, PET, MEMBER, 37.5, 128.1); // 경도 오른쪽

		List<DiaryLocation> found = adapter.loadInArea(MEMBER, PET, MapArea.of(37.0, 126.0, 38.0, 128.0), 10);

		assertThat(found).extracting(DiaryLocation::getDiaryId).containsExactly(1L);
	}

	@Test
	@DisplayName("#19-S5 영역의 경계선 위에 있는 위치도 조회된다")
	void boundaryIsInclusive() {
		save(1L, PET, MEMBER, 37.0, 126.0);
		save(2L, PET, MEMBER, 38.0, 128.0);

		List<DiaryLocation> found = adapter.loadInArea(MEMBER, PET, MapArea.of(37.0, 126.0, 38.0, 128.0), 10);

		assertThat(found).extracting(DiaryLocation::getDiaryId).containsExactly(1L, 2L);
	}

	@Test
	@DisplayName("#19-S5 같은 영역이어도 다른 반려동물의 위치는 나오지 않는다")
	void otherPetExcluded() {
		save(1L, PET, MEMBER, 37.5, 127.0);
		save(2L, PET + 1, MEMBER, 37.5, 127.0);

		List<DiaryLocation> found = adapter.loadInArea(MEMBER, PET, MapArea.of(37.0, 126.0, 38.0, 128.0), 10);

		assertThat(found).extracting(DiaryLocation::getDiaryId).containsExactly(1L);
	}

	@Test
	@DisplayName("#19-S7 다른 회원이 만든 위치는 같은 영역·반려동물이어도 나오지 않는다")
	void otherMemberExcluded() {
		save(1L, PET, MEMBER, 37.5, 127.0);
		save(2L, PET, MEMBER + 1, 37.5, 127.0);

		List<DiaryLocation> found = adapter.loadInArea(MEMBER, PET, MapArea.of(37.0, 126.0, 38.0, 128.0), 10);

		assertThat(found).extracting(DiaryLocation::getDiaryId).containsExactly(1L);
	}

	@Test
	@DisplayName("#19-S6 limit보다 많으면 limit개만 오래된 순으로 돌려준다")
	void limitsResults() {
		for (long diaryId = 1; diaryId <= 5; diaryId++) {
			save(diaryId, PET, MEMBER, 37.5, 127.0);
		}

		List<DiaryLocation> found = adapter.loadInArea(MEMBER, PET, MapArea.of(37.0, 126.0, 38.0, 128.0), 3);

		assertThat(found).extracting(DiaryLocation::getDiaryId).containsExactly(1L, 2L, 3L);
	}

	@Test
	@DisplayName("#19-S6 limit이 1보다 작으면 거부한다")
	void rejectsInvalidLimit() {
		MapArea area = MapArea.of(37.0, 126.0, 38.0, 128.0);

		assertThatThrownBy(() -> adapter.loadInArea(MEMBER, PET, area, 0)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("#19-S7 회원, 반려동물, 영역 중 하나라도 없으면 빈 목록이 아니라 오류로 거부한다")
	void rejectsMissingArguments() {
		MapArea area = MapArea.of(37.0, 126.0, 38.0, 128.0);

		assertThatThrownBy(() -> adapter.loadInArea(null, PET, area, 10)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> adapter.loadInArea(MEMBER, null, area, 10)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> adapter.loadInArea(MEMBER, PET, null, 10)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("#19-S5 DB에는 들어가지만 Java 길이로 255자를 넘는 장소 이름의 행은 400이 아니라 저장 데이터 오류(StoredLocationInvalidException, 원인 보존)로 읽힌다")
	void storedRowBreakingDomainRuleIsStorageError() {
		// 이모지 128개: PostgreSQL varchar(255)는 문자 수로 128이라 들어가고, Java String.length()는 256이다
		String emojiName = "\uD83D\uDC36".repeat(128);
		jdbcTemplate.update("""
				insert into diary_location (diary_id, pet_id, created_by, latitude, longitude, place_name, created_at, updated_at)
				values (500, ?, ?, 37.5, 127.0, ?, now(), now())
				""", PET, MEMBER, emojiName);
		flushAndClear();
		MapArea area = MapArea.of(37.0, 126.0, 38.0, 128.0);

		assertThat(adapter.read(500L).orElseThrow().placeName()).isEqualTo(emojiName);
		assertThatThrownBy(() -> adapter.loadInArea(MEMBER, PET, area, 10))
			.isInstanceOf(com.pawmento.map.application.exception.StoredLocationInvalidException.class);
	}

	@Test
	@DisplayName("#19-S5 핀 조회용 인덱스와 기록별 유일 제약이 DB에 만들어진다")
	void schemaHasIndexAndConstraint() {
		List<String> indexes = jdbcTemplate.queryForList(
				"select indexname from pg_indexes where tablename = 'diary_location'", String.class);

		assertThat(indexes).contains(DiaryLocationJpaEntity.PIN_LOOKUP_INDEX, "uk_diary_location_diary");
	}

	@Test
	@DisplayName("#44-S6 지도 입력 포트가 JPA 저장·조회와 같은 엔티티를 수정하고 삭제한다")
	void mutationAndPinQueriesShareJpaContext() {
		save(700L, PET, MEMBER, 37, 127);
		assertThat(adapter.read(700L).orElseThrow().revision()).isZero();
		assertThat(adapter.lock(700L)).isPresent();
		adapter.write(
				700L,
				PET,
				MEMBER,
				3,
				new com.pawmento.map.domain.DiaryLocationValue(38, 128, "새 공원"));
		var loaded = adapter.read(700L).orElseThrow();
		assertThat(loaded.revision()).isEqualTo(3);
		assertThat(loaded.placeName()).isEqualTo("새 공원");
		assertThat(adapter.loadInArea(MEMBER, PET, MapArea.of(37.5, 127.5, 38.5, 128.5), 10))
				.extracting(DiaryLocation::getSourceRevision)
				.containsExactly(3L);
		adapter.remove(700L);
		assertThat(adapter.read(700L)).isEmpty();
		assertThat(adapter.loadInArea(MEMBER, PET, MapArea.of(37.5, 127.5, 38.5, 128.5), 10))
				.isEmpty();
	}

	@Test
	@DisplayName("#44-S6 잠금 후에는 미리 읽어 둔 엔티티도 최신 DB 관측 상태로 갱신한다")
	void lockRefreshesPreviouslyReadEntity() {
		save(701L, PET, MEMBER, 37, 127);
		assertThat(adapter.read(701L).orElseThrow().revision()).isZero();
		jdbcTemplate.update("update diary_location set source_revision=5 where diary_id=701");
		assertThat(adapter.lock(701L).orElseThrow().revision()).isEqualTo(5);
		assertThat(adapter.read(701L).orElseThrow().revision()).isEqualTo(5);
	}
}
