package com.pawmento.map.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;

import javax.sql.DataSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.pawmento.TestcontainersConfiguration;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.pawmento.map.application.exception.PlaceSyncLockLostException;
import com.pawmento.map.application.port.out.PlaceSyncLockPort;
import com.pawmento.map.domain.PlaceCategory;

// 시나리오는 docs/map/animal-places.md(#172-S22)에 있다. 가져오기를 한 종류에 하나씩만 돌리는 잠금(PostgreSQL 어드바이저리 락)을 진짜 DB로 확인한다
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = { "pawmento.auth.jwt-secret=test-only-jwt-secret-0123456789-abcdefghij",
		"pawmento.media.worker.enabled=false", "pawmento.map.public-data.sync.enabled=false" })
class PlaceSyncLockAdapterTest {

	@Autowired
	private PlaceSyncLockPort locks;

	@Autowired
	private DataSource dataSource;

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

	@Test
	@DisplayName("#172-S22 잠금을 쥐고 있는 동안 requireHeld는 통과하고, 연결이 끊겨 잠금을 잃었으면 저장 전에 예외로 막는다. 닫아도 오류 없이 다시 잡을 수 있다")
	void lostLockIsDetectedBeforeStoring() {
		var lock = locks.tryAcquire(PlaceCategory.HOSPITAL).orElseThrow();
		lock.requireHeld();

		// 잠금을 쥔 서버 연결을 DB에서 끊는다(네트워크 단절·DB 재시작과 같은 상황)
		Integer pid = jdbcTemplate.queryForObject("""
				select pid from pg_locks
				where locktype = 'advisory' and granted and classid = ?::oid and objsubid = 2 and objid = ?::oid
				""", Integer.class, 0x504C4353, PlaceCategory.HOSPITAL.ordinal());
		jdbcTemplate.queryForObject("select pg_terminate_backend(?)", Boolean.class, pid);

		assertThatThrownBy(lock::requireHeld).isInstanceOf(PlaceSyncLockLostException.class);
		lock.close();

		// 끊긴 연결의 잠금은 DB가 연결 종료를 알아챈 뒤에 풀어서, 느린 환경에서는 잠깐 기다려야 한다
		awaitAcquirable(PlaceCategory.HOSPITAL);
	}

	@Test
	@DisplayName("#172-S22 연결은 살아 있는데 잠금 해제가 실패하면 연결을 끊어 잠금을 풀고, 풀 자리도 돌려준다(반복해도 풀이 바닥나지 않는다)")
	void failedUnlockReleasesTheLockAndThePoolSlot() throws Exception {
		HikariDataSource base = dataSource.unwrap(HikariDataSource.class);
		HikariConfig config = new HikariConfig();
		config.setJdbcUrl(base.getJdbcUrl());
		config.setUsername(base.getUsername());
		config.setPassword(base.getPassword());
		config.setMaximumPoolSize(2);
		config.setConnectionTimeout(3000);
		try (HikariDataSource pool = new HikariDataSource(config)) {
			PlaceSyncLockAdapter failing = new PlaceSyncLockAdapter(failingUnlock(pool));

			// 풀(2개)보다 많이 반복해도 막히지 않아야 한다. 풀 자리를 하나씩 잃으면 세 번째에서 연결을 못 얻는다
			for (int i = 0; i < 5; i++) {
				failing.tryAcquire(PlaceCategory.GROOMING).orElseThrow().close();
				assertThat(pool.getHikariPoolMXBean().getActiveConnections()).as("%d번째 뒤", i + 1).isZero();
			}
			// 끊긴 연결의 잠금은 DB가 풀어서 다른 곳이 잡을 수 있다(풀리는 데 잠깐 걸릴 수 있다)
			awaitAcquirable(PlaceCategory.GROOMING);
		}
	}

	// 잠금이 풀릴 때까지 최대 10초 기다렸다가 잡아 본다. 연결을 강제로 끊으면 DB가 끊김을 알아채고 잠금을 푸는 일이 비동기라서
	// 끊은 직후에 바로 잡으면 가끔 실패한다(CI의 느린 서버에서 드러남). 끝까지 못 잡으면 진짜 문제라 실패한다
	private void awaitAcquirable(PlaceCategory category) {
		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
			var lock = locks.tryAcquire(category);
			assertThat(lock).as("%s 잠금", category).isPresent();
			lock.get().close();
		});
	}

	// 잠금 해제 질의만 연결이 살아 있는 채로 실패하게 만든 풀
	private static DataSource failingUnlock(HikariDataSource pool) {
		return (DataSource) Proxy.newProxyInstance(DataSource.class.getClassLoader(), new Class<?>[] { DataSource.class },
				(proxy, method, args) -> {
					try {
						Object result = method.invoke(pool, args);
						if (method.getName().equals("getConnection") && result instanceof Connection real) {
							return wrap(real);
						}
						return result;
					}
					catch (InvocationTargetException e) {
						throw e.getCause();
					}
				});
	}

	private static Connection wrap(Connection real) {
		return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[] { Connection.class },
				(proxy, method, args) -> {
					if (method.getName().equals("prepareStatement") && ((String) args[0]).contains("pg_advisory_unlock")) {
						throw new SQLException("잠금 해제 실패(테스트)", "XX000");
					}
					try {
						return method.invoke(real, args);
					}
					catch (InvocationTargetException e) {
						throw e.getCause();
					}
				});
	}

}
