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

// 시나리오는 docs/map/record-location.md에 있다. 영역·경계·다른 반려동물과 회원·500개 제한·잘못 저장된 행은 MapPinScenarioTest가,
// 핀 삭제는 DiaryLocationScenarioTest가 확인한다. 여기에는 저장소가 지키는 규칙(유일 제약, 기본값, 인덱스, JPA 컨텍스트)만 남긴다.
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
