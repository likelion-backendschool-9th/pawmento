package com.pawmento.map.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import com.pawmento.TestcontainersConfiguration;
import com.pawmento.map.domain.PlaceCategory;
import com.pawmento.map.domain.PlaceDraft;

// 시나리오는 docs/map/place-search.md(#195-S19)에 있다. 서버가 시작할 때와 같은 조건으로 확인한다: 장소는 먼저 커밋해 두고, 테스트의
// 트랜잭션 없이 run()을 불러 저장 결과를 다시 읽는다(@Transactional 테스트에서는 채우기의 UPDATE가 테스트 트랜잭션에 참여해 조건이 달라진다).
// 이 테스트가 만든 행은 source_id의 접두사로 찾아 끝날 때 지운다. 같은 종류의 다른 장소를 지우거나 가져온 시각을 남기지 않도록
// adapter.replace 대신 한 장소의 갱신만 커밋한다
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "pawmento.auth.jwt-secret=test-only-jwt-secret-0123456789-abcdefghij")
class PlaceSearchKeyBackfillStartupTest {

	private final String prefix = "startup-" + UUID.randomUUID() + "-";

	// 영문 표지라 정규화해도 그대로이고, 같은 DB의 다른 장소와 섞이지 않는다
	private final String token = "s" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);

	@Autowired
	private PlaceSearchKeyBackfill backfill;

	@Autowired
	private PlaceJpaRepository repository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private TransactionTemplate transactionTemplate;

	@AfterEach
	void deletePlaces() {
		jdbcTemplate.update("delete from places where source_id like ?", prefix + "%");
	}

	private long insertWithoutKeys(String sourceId, String name, String address) {
		return jdbcTemplate.queryForObject("""
				insert into places (category, source_id, name, address, latitude, longitude, open24h, created_at, updated_at)
				values ('HOSPITAL', ?, ?, ?, 37.58, 127.0, false, now(), now()) returning id
				""", Long.class, prefix + sourceId, name, address);
	}

	private String column(String column, String sourceId) {
		return jdbcTemplate.queryForObject("select " + column + " from places where source_id = ?", String.class,
				prefix + sourceId);
	}

	// 가져오기가 한 장소에 하는 일(apply)만 따로 커밋한다. adapter.replace는 같은 종류의 모든 장소를 지우고 가져온 시각(place_sync_runs)도
	// 남겨서 공유 테스트 DB의 다른 데이터에 영향을 주므로 쓰지 않는다
	private boolean syncRow(String sourceId, PlaceDraft draft) {
		long id = jdbcTemplate.queryForObject("select id from places where source_id = ?", Long.class, prefix + sourceId);
		return Boolean.TRUE.equals(transactionTemplate.execute(status -> repository.findById(id).orElseThrow().apply(draft)));
	}

	private PlaceDraft draft(String sourceId, String name, String address) {
		return new PlaceDraft(PlaceCategory.HOSPITAL, prefix + sourceId, name, address, null, 37.58, 127.0, false,
				null, null, null, null);
	}

	@Test
	@DisplayName("#195-S19 서버가 시작할 때(run) 커밋돼 있던 장소의 비어 있는 검색용 열을 채우고, 이미 채워진 행은 다시 쓰지 않으며, 다시 실행하면 바꿀 행이 없다")
	void startupFillsOnlyTheRowsThatLackSearchKeys() {
		insertWithoutKeys("a", token + " 서울숲 동물병원", "서울 성동구 서울숲(4길) 18");
		insertWithoutKeys("noaddr", token + " 주소 없는 병원", null);
		jdbcTemplate.update("""
				insert into places (category, source_id, name, address, latitude, longitude, open24h, name_key, address_key,
				                    created_at, updated_at)
				values ('HOSPITAL', ?, ?, ?, 37.58, 127.0, false, 'keep-name', 'keep-address', now(), now())
				""", prefix + "done", token + " 이미 채워진 병원", "서울 어딘가");

		backfill.run(new DefaultApplicationArguments());

		assertThat(column("name_key", "a")).isEqualTo(token + "서울숲동물병원");
		assertThat(column("address_key", "a")).isEqualTo("서울성동구서울숲4길18");
		assertThat(column("name_key", "noaddr")).isEqualTo(token + "주소없는병원");
		assertThat(column("address_key", "noaddr")).isNull();
		// 이미 채워진 행은 건드리지 않는다
		assertThat(column("name_key", "done")).isEqualTo("keep-name");
		assertThat(column("address_key", "done")).isEqualTo("keep-address");
		// 다시 실행하면 바꿀 행이 없다
		assertThat(backfill.backfill()).isEqualTo(new PlaceSearchKeyBackfill.Result(0, 0));
	}

	@Test
	@DisplayName("#195-S19 채우기는 두 열만 바꾸고 수정 시각과 이름·주소는 그대로 둔다")
	void fillsOnlyTheTwoKeyColumns() {
		insertWithoutKeys("a", token + " 서울숲 동물병원", "서울 성동구 1");
		jdbcTemplate.update("update places set updated_at = '2026-01-01T00:00:00Z' where source_id = ?", prefix + "a");

		backfill.run(new DefaultApplicationArguments());

		assertThat(column("name", "a")).isEqualTo(token + " 서울숲 동물병원");
		assertThat(column("address", "a")).isEqualTo("서울 성동구 1");
		assertThat(column("to_char(updated_at at time zone 'UTC', 'YYYY-MM-DD')", "a")).isEqualTo("2026-01-01");
	}

	@Test
	@DisplayName("#195-S19 읽은 뒤에 가져오기가 같은 행의 이름·주소를 먼저 갱신했으면, 채우기는 최신 정보와 검색용 열을 덮어쓰지 않는다")
	void doesNotOverwriteWhatSyncUpdatedMeanwhile() {
		long id = insertWithoutKeys("race", token + " 옛 이름 병원", "서울 옛 주소 1");
		// 채우기가 행을 읽은 상태(검색용 열이 비어 있고 이름이 "옛 이름"). 트랜잭션 밖에서 읽었으므로 이미 분리된 값이다
		PlaceJpaEntity stale = repository.findById(id).orElseThrow();

		// 그 사이에 가져오기가 같은 장소의 이름·주소를 바꾸고 검색용 열도 채운다
		syncRow("race", draft("race", token + " 새 이름 병원", "서울 새 주소 2"));
		assertThat(column("name_key", "race")).isEqualTo(token + "새이름병원");

		// 옛 값을 들고 있는 채우기가 이어서 실행된다
		boolean filled = backfill.fill(stale);

		assertThat(filled).isFalse();
		assertThat(column("name", "race")).isEqualTo(token + " 새 이름 병원");
		assertThat(column("address", "race")).isEqualTo("서울 새 주소 2");
		assertThat(column("name_key", "race")).isEqualTo(token + "새이름병원");
		assertThat(column("address_key", "race")).isEqualTo("서울새주소2");
	}

	@Test
	@DisplayName("#195-S19 읽은 뒤에 가져오기가 바꾼 것이 없으면(열도 그대로 비어 있음) 읽은 값으로 두 열을 채운다")
	void fillsWhenSyncDidNotChangeTheRow() {
		long id = insertWithoutKeys("calm", token + " 그대로 병원", "서울 그대로 1");
		PlaceJpaEntity loaded = repository.findById(id).orElseThrow();

		// 같은 값을 다시 가져와도 이름·주소가 그대로이면 열을 바꾸지 않는다(apply가 바뀐 것이 없다고 본다)
		assertThat(syncRow("calm", draft("calm", token + " 그대로 병원", "서울 그대로 1"))).isFalse();
		assertThat(column("name_key", "calm")).isNull();

		assertThat(backfill.fill(loaded)).isTrue();
		assertThat(column("name_key", "calm")).isEqualTo(token + "그대로병원");
		assertThat(column("address_key", "calm")).isEqualTo("서울그대로1");
	}

	@Test
	@DisplayName("#195-S19 소문자로 바꾸면 두 배로 길어지는 255자 이름·주소(İ)도 검색용 열에 저장되어, 채우기와 가져오기가 실패하지 않는다")
	void keysFitWhenLowercasingMakesTheTextLonger() {
		// İ(U+0130)는 소문자로 바꾸면 i와 결합 점(U+0307) 두 글자가 된다. 255자는 키가 510자가 된다
		String longest = "İ".repeat(255);
		insertWithoutKeys("long", longest, longest);

		PlaceSearchKeyBackfill.Result result = backfill.backfill();

		assertThat(result.failed()).isZero();
		assertThat(column("length(name_key)", "long")).isEqualTo("510");
		assertThat(column("length(address_key)", "long")).isEqualTo("510");
		// 가져오기에서 새로 저장해도(저장 후보의 키는 from에서 채워진다) 실패하지 않는다
		repository.saveAndFlush(PlaceJpaEntity.from(draft("long2", longest, "서울 1")));
		assertThat(column("length(name_key)", "long2")).isEqualTo("510");
	}

}
