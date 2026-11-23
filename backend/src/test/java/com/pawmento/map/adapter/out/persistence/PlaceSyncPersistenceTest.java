package com.pawmento.map.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.pawmento.TestcontainersConfiguration;
import com.pawmento.map.application.port.out.AppliedPlaces;
import com.pawmento.map.domain.Open24hBasis;
import com.pawmento.map.domain.PlaceCategory;
import com.pawmento.map.domain.PlaceDraft;

// 시나리오는 docs/map/animal-places.md(#172-S4, S5)에 있다. 한 종류를 통째로 바꾸는 반영(추가·갱신·삭제)이 한 트랜잭션으로 이루어지는지
// 진짜 PostgreSQL로 확인한다. 트랜잭션이 끝나야 확인할 수 있어서 테스트 자체는 트랜잭션으로 감싸지 않고, 이 테스트가 시작·끝날 때
// places를 비운다(다른 테스트가 같은 DB를 써도 순서대로 실행되므로 안전하다)
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "pawmento.auth.jwt-secret=test-only-jwt-secret-0123456789-abcdefghij")
class PlaceSyncPersistenceTest {

	@Autowired
	private PlacePersistenceAdapter adapter;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	@AfterEach
	void clean() {
		jdbcTemplate.update("delete from places");
	}

	private static PlaceDraft draft(PlaceCategory category, String sourceId, String name, String phone) {
		return new PlaceDraft(category, sourceId, name, "서울특별시 종로구 1", phone, 37.58, 127.0, false, null, null, null,
				Instant.parse("2026-11-21T12:00:00Z"));
	}

	private Map<String, Object> row(PlaceCategory category, String sourceId) {
		return jdbcTemplate.queryForMap("select * from places where category = ? and source_id = ?", category.name(),
				sourceId);
	}

	private long count(PlaceCategory category) {
		return jdbcTemplate.queryForObject("select count(*) from places where category = ?", Long.class,
				category.name());
	}

	@Test
	@DisplayName("#172-S4 새 관리번호는 추가하고, 건수를 종류별로 센다")
	void insertsNewPlaces() {
		var applied = adapter.replace(PlaceCategory.HOSPITAL,
				List.of(draft(PlaceCategory.HOSPITAL, "a", "가병원", null), draft(PlaceCategory.HOSPITAL, "b", "나병원", "02-111-2222")));

		assertThat(applied).isEqualTo(new AppliedPlaces(2, 0, 0));
		assertThat(adapter.countByCategory(PlaceCategory.HOSPITAL)).isEqualTo(2);
		assertThat(adapter.countByCategory(PlaceCategory.GROOMING)).isZero();
		assertThat(row(PlaceCategory.HOSPITAL, "b").get("name")).isEqualTo("나병원");
		assertThat(row(PlaceCategory.HOSPITAL, "b").get("phone")).isEqualTo("02-111-2222");
	}

	@Test
	@DisplayName("#172-S4 같은 관리번호로 다시 가져오면 행이 늘지 않고 바뀐 값만 바뀌며, 바뀌지 않은 행은 갱신으로 세지 않는다")
	void updatesChangedFieldsOnly() {
		adapter.replace(PlaceCategory.HOSPITAL,
				List.of(draft(PlaceCategory.HOSPITAL, "a", "가병원", null), draft(PlaceCategory.HOSPITAL, "b", "나병원", null)));
		Object idOfA = row(PlaceCategory.HOSPITAL, "a").get("id");
		Object createdAtOfA = row(PlaceCategory.HOSPITAL, "a").get("created_at");

		var applied = adapter.replace(PlaceCategory.HOSPITAL,
				List.of(draft(PlaceCategory.HOSPITAL, "a", "새 가병원", "02-333-4444"), draft(PlaceCategory.HOSPITAL, "b", "나병원", null)));

		assertThat(applied).isEqualTo(new AppliedPlaces(0, 1, 0));
		assertThat(count(PlaceCategory.HOSPITAL)).isEqualTo(2);
		Map<String, Object> a = row(PlaceCategory.HOSPITAL, "a");
		assertThat(a.get("id")).isEqualTo(idOfA);
		assertThat(a.get("created_at")).isEqualTo(createdAtOfA);
		assertThat(a.get("name")).isEqualTo("새 가병원");
		assertThat(a.get("phone")).isEqualTo("02-333-4444");
		assertThat(a.get("address")).isEqualTo("서울특별시 종로구 1");
	}

	@Test
	@DisplayName("#172-S4 이번 목록에 없는 같은 종류만 지우고, 다른 종류는 건드리지 않는다")
	void deletesMissingOfTheSameCategoryOnly() {
		adapter.replace(PlaceCategory.HOSPITAL,
				List.of(draft(PlaceCategory.HOSPITAL, "a", "가병원", null), draft(PlaceCategory.HOSPITAL, "b", "나병원", null)));
		adapter.replace(PlaceCategory.GROOMING, List.of(draft(PlaceCategory.GROOMING, "a", "가미용", null)));

		var applied = adapter.replace(PlaceCategory.HOSPITAL, List.of(draft(PlaceCategory.HOSPITAL, "a", "가병원", null)));

		assertThat(applied).isEqualTo(new AppliedPlaces(0, 0, 1));
		assertThat(count(PlaceCategory.HOSPITAL)).isEqualTo(1);
		// 같은 관리번호 "a"가 미용에도 있어도 종류가 달라 그대로다
		assertThat(count(PlaceCategory.GROOMING)).isEqualTo(1);
	}

	@Test
	@DisplayName("#172-S4 영업 상태·24시 근거·출처 시각도 저장하고 읽는다")
	void storesAllFields() {
		adapter.replace(PlaceCategory.HOSPITAL,
				List.of(new PlaceDraft(PlaceCategory.HOSPITAL, "z", "24시 병원", "주소", "02-111-2222", 37.5, 127.1, true,
						Open24hBasis.NAME, null, null, Instant.parse("2026-11-21T12:31:41Z"))));

		Map<String, Object> row = row(PlaceCategory.HOSPITAL, "z");
		assertThat(row.get("open24h")).isEqualTo(true);
		assertThat(row.get("open24h_basis")).isEqualTo("NAME");
		assertThat(row.get("latitude")).isEqualTo(37.5);
		assertThat(((java.sql.Timestamp) row.get("source_updated_at")).toInstant())
			.isEqualTo(Instant.parse("2026-11-21T12:31:41Z"));
	}

	@Test
	@DisplayName("#172-S5 반영 도중 하나라도 실패하면 추가·갱신·삭제가 하나도 적용되지 않는다")
	void failureRollsBackEverything() {
		adapter.replace(PlaceCategory.HOSPITAL, List.of(draft(PlaceCategory.HOSPITAL, "keep", "그대로", null),
				draft(PlaceCategory.HOSPITAL, "gone", "삭제될 병원", null)));
		// keep을 갱신하고 gone을 지우고 새 행을 넣는데, 새 행의 이름이 null이라 저장소가 거절한다
		List<PlaceDraft> broken = List.of(draft(PlaceCategory.HOSPITAL, "keep", "바뀐 이름", null),
				draft(PlaceCategory.HOSPITAL, "new", null, null));

		assertThatThrownBy(() -> adapter.replace(PlaceCategory.HOSPITAL, broken)).isInstanceOf(RuntimeException.class);

		assertThat(count(PlaceCategory.HOSPITAL)).isEqualTo(2);
		assertThat(row(PlaceCategory.HOSPITAL, "keep").get("name")).isEqualTo("그대로");
		assertThat(row(PlaceCategory.HOSPITAL, "gone").get("name")).isEqualTo("삭제될 병원");
	}

}
