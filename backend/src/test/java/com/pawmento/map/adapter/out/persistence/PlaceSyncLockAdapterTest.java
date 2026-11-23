package com.pawmento.map.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.pawmento.TestcontainersConfiguration;
import com.pawmento.map.application.port.out.PlaceSyncLockPort;
import com.pawmento.map.domain.PlaceCategory;

// 시나리오는 docs/map/animal-places.md(#172-S22)에 있다. 가져오기를 한 종류에 하나씩만 돌리는 잠금(PostgreSQL 어드바이저리 락)을 진짜 DB로 확인한다
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = { "pawmento.auth.jwt-secret=test-only-jwt-secret-0123456789-abcdefghij",
		"pawmento.media.worker.enabled=false", "pawmento.map.local-data.sync.enabled=false" })
class PlaceSyncLockAdapterTest {

	@Autowired
	private PlaceSyncLockPort locks;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	@DisplayName("#172-S22 한 종류의 잠금을 잡고 있는 동안 같은 종류는 잡을 수 없고, 놓으면 다시 잡을 수 있다")
	void sameCategoryIsExclusive() {
		try (var first = locks.tryAcquire(PlaceCategory.HOSPITAL).orElseThrow()) {
			assertThat(locks.tryAcquire(PlaceCategory.HOSPITAL)).isEmpty();
		}

		var again = locks.tryAcquire(PlaceCategory.HOSPITAL);
		assertThat(again).isPresent();
		again.get().close();
	}

	@Test
	@DisplayName("#172-S22 종류가 다르면 서로 막지 않는다(병원을 가져오는 동안 미용도 가져올 수 있다)")
	void differentCategoriesDoNotBlockEachOther() {
		try (var hospital = locks.tryAcquire(PlaceCategory.HOSPITAL).orElseThrow();
				var grooming = locks.tryAcquire(PlaceCategory.GROOMING).orElseThrow()) {
			assertThat(hospital).isNotNull();
			assertThat(grooming).isNotNull();
		}
	}

	@Test
	@DisplayName("#172-S22 잠금을 놓으면 DB 연결도 풀에 돌려준다. 안 돌려주면 몇 번 하다 연결이 바닥난다")
	void releasingGivesTheConnectionBack() {
		// 기본 연결 풀(10개)보다 많이 반복해도 막히지 않아야 한다
		for (int i = 0; i < 30; i++) {
			var lock = locks.tryAcquire(PlaceCategory.GROOMING);
			assertThat(lock).isPresent();
			lock.get().close();
		}
		assertThat(jdbcTemplate.queryForObject("select 1", Integer.class)).isEqualTo(1);
	}

	@Test
	@DisplayName("#172-S22 잠금을 놓지 않고 닫기만 해도(두 번 닫기 포함) 오류 없이 한 번만 놓인다")
	void closingTwiceIsHarmless() {
		var lock = locks.tryAcquire(PlaceCategory.HOSPITAL).orElseThrow();
		lock.close();
		lock.close();

		var again = locks.tryAcquire(PlaceCategory.HOSPITAL);
		assertThat(again).isPresent();
		again.get().close();
	}

}
