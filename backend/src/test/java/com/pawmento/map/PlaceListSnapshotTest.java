package com.pawmento.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.pawmento.TestcontainersConfiguration;
import com.pawmento.map.application.port.in.ListPlacesQuery;
import com.pawmento.map.application.port.in.ListPlacesResult;
import com.pawmento.map.application.port.in.ListPlacesUseCase;
import com.pawmento.map.application.port.out.LoadPlaceDataAsOfPort;

// 시나리오는 docs/map/animal-places.md(#172-S21)에 있다. 장소 목록과 가져온 시각을 같은 읽기 스냅샷에서 읽는지 진짜 PostgreSQL로 확인한다.
// 두 조회 사이에 가져오기가 커밋되는 상황은 가져온 시각을 읽기 직전에 다른 연결로 값을 바꿔 만든다. 이 테스트가 만든 행은 끝나면 지운다
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "pawmento.auth.jwt-secret=test-only-jwt-secret-0123456789-abcdefghij")
class PlaceListSnapshotTest {

	private static final Instant OLD = Instant.parse("2026-11-21T13:00:00Z");

	private static final Instant NEW = Instant.parse("2026-11-24T13:00:00Z");

	private final String prefix = "snap-" + UUID.randomUUID() + "-";

	@Autowired
	private ListPlacesUseCase listPlaces;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@MockitoSpyBean
	private LoadPlaceDataAsOfPort dataAsOfPort;

	@BeforeEach
	void setUp() {
		jdbcTemplate.update("delete from place_sync_runs");
		jdbcTemplate.update("insert into place_sync_runs (category, last_synced_at) values ('HOSPITAL', ?)",
				Timestamp.from(OLD));
		jdbcTemplate.update("""
				insert into places (category, source_id, name, address, latitude, longitude, open24h, created_at, updated_at)
				values ('HOSPITAL', ?, '스냅샷 병원', '서울 종로구 1', 37.5801, 127.0001, false, now(), now())
				""", prefix + "h1");
	}

	@AfterEach
	void tearDown() {
		Mockito.reset(dataAsOfPort);
		jdbcTemplate.update("delete from places where source_id like ?", prefix + "%");
		jdbcTemplate.update("delete from place_sync_runs");
	}

	private ListPlacesResult list() {
		return listPlaces.list(new ListPlacesQuery(new BigDecimal("37.57"), new BigDecimal("126.99"),
				new BigDecimal("37.59"), new BigDecimal("127.01"), Set.of("HOSPITAL")));
	}

	private void commitAnotherImportOnAnotherConnection() throws InterruptedException {
		AtomicReference<Throwable> failure = new AtomicReference<>();
		Thread importer = new Thread(() -> {
			try {
				jdbcTemplate.update("""
						insert into places (category, source_id, name, address, latitude, longitude, open24h, created_at, updated_at)
						values ('HOSPITAL', ?, '그 사이 새로 들어온 병원', '서울 종로구 2', 37.5802, 127.0002, false, now(), now())
						""", prefix + "h2");
				jdbcTemplate.update("update place_sync_runs set last_synced_at = ? where category = 'HOSPITAL'",
						Timestamp.from(NEW));
			}
			catch (Throwable e) {
				failure.set(e);
			}
		});
		importer.start();
		importer.join();
		assertThat(failure.get()).as("다른 연결의 커밋").isNull();
	}

	@Test
	@DisplayName("#172-S21 장소를 읽은 뒤 가져온 시각을 읽기 전에 가져오기가 커밋돼도, 이전 장소 목록에 새 시각이 붙지 않고 같은 시점의 값을 돌려준다")
	void readsPlacesAndTheSyncedAtFromTheSameSnapshot() {
		// 가져온 시각을 읽기 직전에 다른 연결에서 새 가져오기가 커밋된다(장소 하나가 추가되고 시각이 바뀐다). 같은 스레드의 JDBC 호출은
		// 읽기 전용 트랜잭션의 연결을 재사용하므로, 별도 스레드에서 실행해 실제로 다른 연결·다른 트랜잭션으로 커밋한다
		doAnswer(invocation -> {
			commitAnotherImportOnAnotherConnection();
			return invocation.callRealMethod();
		}).when(dataAsOfPort).syncedAt(any());

		ListPlacesResult result = list();

		// 읽기 스냅샷이면 장소 목록과 시각이 모두 커밋 전 값이다. 기본 격리 수준이면 시각만 NEW가 되어 이전 목록에 새 시각이 붙는다
		assertThat(result.places()).hasSize(1);
		assertThat(result.dataAsOf()).isEqualTo(OLD);

		// 커밋이 끝난 뒤의 다음 요청은 새 값을 본다
		Mockito.reset(dataAsOfPort);
		ListPlacesResult next = list();
		assertThat(next.places()).hasSize(2);
		assertThat(next.dataAsOf()).isEqualTo(NEW);
	}

}
