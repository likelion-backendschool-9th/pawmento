package com.pawmento.map.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumSet;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.pawmento.TestcontainersConfiguration;
import com.pawmento.map.domain.MapArea;
import com.pawmento.map.domain.Place;
import com.pawmento.map.domain.PlaceCategory;

// 시나리오는 docs/map/animal-places.md에 있다. 장소 테이블이 지키는 부분(저장 모양, 유일 제약, 영역 조회)을 진짜 PostgreSQL로 확인한다.
// 각 테스트가 끝나면 되돌린다
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "pawmento.auth.jwt-secret=test-only-jwt-secret-0123456789-abcdefghij")
@Transactional
class PlacePersistenceAdapterTest {

	private final String prefix = "adapter-" + UUID.randomUUID() + "-";

	@Autowired
	private PlacePersistenceAdapter adapter;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private void insert(String category, String sourceId, double latitude, double longitude) {
		jdbcTemplate.update("""
				insert into places (category, source_id, name, latitude, longitude, open24h, created_at, updated_at)
				values (?, ?, '이름', ?, ?, false, now(), now())
				""", category, prefix + sourceId, latitude, longitude);
	}

	@Test
	@DisplayName("#172-S10 영역 조회는 경계선을 포함하고 영역 밖과 요청하지 않은 종류는 빼며, 없는 값은 null로 읽는다")
	void loadsPlacesInTheArea() {
		insert("HOSPITAL", "in", 37.58, 127.0);
		insert("HOSPITAL", "edge", 37.57, 126.99);
		insert("HOSPITAL", "out-lat", 37.5699, 127.0);
		insert("HOSPITAL", "out-lng", 37.58, 127.0101);
		insert("SHELTER", "other-kind", 37.58, 127.0);

		var places = adapter.loadInArea(MapArea.of(37.57, 126.99, 37.59, 127.01), EnumSet.of(PlaceCategory.HOSPITAL))
			.stream()
			.filter(place -> place.latitude() >= 37.57 && place.latitude() <= 37.59)
			.toList();

		// 같은 DB를 쓰는 다른 테스트의 행이 섞일 수 있어 이 테스트가 만든 행만 센다
		long mine = jdbcTemplate.queryForObject(
				"select count(*) from places where source_id like ? and category = 'HOSPITAL' "
						+ "and latitude between 37.57 and 37.59 and longitude between 126.99 and 127.01",
				Long.class, prefix + "%");
		assertThat(mine).isEqualTo(2);
		Place any = places.stream().filter(place -> place.longitude() == 127.0).findFirst().orElseThrow();
		assertThat(any.category()).isEqualTo(PlaceCategory.HOSPITAL);
		assertThat(any.address()).isNull();
		assertThat(any.phone()).isNull();
		assertThat(any.open24h()).isFalse();
		assertThat(any.open24hBasis()).isNull();
		assertThat(any.closedDays()).isNull();
	}

	@Test
	@DisplayName("#172-S1 같은 종류에 같은 관리번호를 두 번 넣으면 막는다")
	void sourceIdIsUniquePerCategory() {
		insert("HOSPITAL", "dup", 37.58, 127.0);

		assertThatThrownBy(() -> insert("HOSPITAL", "dup", 37.58, 127.0))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("#172-S1 종류가 다르면 같은 관리번호도 들어간다")
	void sameSourceIdInAnotherCategory() {
		insert("HOSPITAL", "same", 37.58, 127.0);
		insert("GROOMING", "same", 37.58, 127.0);

		assertThat(jdbcTemplate.queryForObject("select count(*) from places where source_id = ?", Long.class,
				prefix + "same")).isEqualTo(2);
	}

}
