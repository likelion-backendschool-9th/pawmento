package com.pawmento.map.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

// 시나리오는 docs/map/animal-places.md에 있다. 종류와 관리번호의 유일 제약을 진짜 PostgreSQL로 확인한다. 영역 조회는 PlaceListScenarioTest가 확인한다.
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
