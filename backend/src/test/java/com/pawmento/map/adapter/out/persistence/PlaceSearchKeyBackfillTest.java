package com.pawmento.map.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.pawmento.TestcontainersConfiguration;
import com.pawmento.map.domain.Place;
import com.pawmento.map.domain.PlaceCategory;
import com.pawmento.map.domain.PlaceDraft;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

// 시나리오는 docs/map/place-search.md(#195-S19)에 있다. 검색용 열을 더하기 전에 저장한 장소를 서버가 시작할 때 채우는 부분과,
// 채우는 사이에 장소 가져오기가 같은 행을 갱신해도 최신 정보를 덮어쓰지 않는 부분, 검색어를 LIKE 특수문자 없이 글자 그대로 찾는
// 부분을 진짜 Postgres로 확인한다. 각 테스트가 끝나면 되돌린다
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "pawmento.auth.jwt-secret=test-only-jwt-secret-0123456789-abcdefghij")
@Transactional
class PlaceSearchKeyBackfillTest {

	private static final Set<PlaceCategory> ALL = EnumSet.allOf(PlaceCategory.class);

	private final String prefix = "backfill-" + UUID.randomUUID() + "-";

	// 영문 표지라 정규화해도 그대로이고, 같은 DB의 다른 장소와 섞이지 않게 검색어에 넣는다
	private final String token = "z" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);

	@Autowired
	private PlaceSearchKeyBackfill backfill;

	@Autowired
	private PlacePersistenceAdapter adapter;

	@Autowired
	private PlaceJpaRepository repository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@PersistenceContext
	private EntityManager entityManager;

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

	private PlaceDraft draft(String sourceId, String name, String address) {
		return new PlaceDraft(PlaceCategory.HOSPITAL, prefix + sourceId, name, address, null, 37.58, 127.0, false,
				null, null, null, null);
	}

	@Test
	@DisplayName("#195-S19 서버가 시작할 때(run) 검색용 열이 비어 있는 장소를 채우고, 이미 채워진 행은 다시 쓰지 않으며, 다시 실행하면 바꿀 행이 없다")
	void startupFillsOnlyTheRowsThatLackSearchKeys() {
		insertWithoutKeys("a", token + " 서울숲 동물병원", "서울 성동구 서울숲(4길) 18");
		insertWithoutKeys("noaddr", token + " 주소 없는 병원", null);
		jdbcTemplate.update("""
				insert into places (category, source_id, name, address, latitude, longitude, open24h, name_key, address_key,
				                    created_at, updated_at)
				values ('HOSPITAL', ?, ?, ?, 37.58, 127.0, false, 'keep-name', 'keep-address', now(), now())
				""", prefix + "done", token + " 이미 채워진 병원", "서울 어딘가");

		// 서버가 시작할 때 도는 경로(ApplicationRunner.run)로 확인한다
		backfill.run(new DefaultApplicationArguments());

		assertThat(column("name_key", "a")).isEqualTo(token + "서울숲동물병원");
		assertThat(column("address_key", "a")).isEqualTo("서울성동구서울숲4길18");
		assertThat(column("name_key", "noaddr")).isEqualTo(token + "주소없는병원");
		assertThat(column("address_key", "noaddr")).isNull();
		// 이미 채워진 행은 건드리지 않는다
		assertThat(column("name_key", "done")).isEqualTo("keep-name");
		assertThat(column("address_key", "done")).isEqualTo("keep-address");
		// 다시 실행하면 바꿀 행이 없다
		assertThat(backfill.backfill()).isZero();
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
		// 채우기가 행을 읽은 상태(검색용 열이 비어 있고 이름이 "옛 이름")를 흉내 낸다
		PlaceJpaEntity stale = repository.findById(id).orElseThrow();
		entityManager.detach(stale);

		// 그 사이에 가져오기가 같은 장소의 이름·주소를 바꾸고 검색용 열도 채운다
		adapter.replace(PlaceCategory.HOSPITAL, List.of(draft("race", token + " 새 이름 병원", "서울 새 주소 2")));
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
		entityManager.detach(loaded);

		// 같은 값을 다시 가져와도 이름·주소가 그대로이면 열을 바꾸지 않는다(apply가 바뀐 것이 없다고 본다)
		adapter.replace(PlaceCategory.HOSPITAL, List.of(draft("calm", token + " 그대로 병원", "서울 그대로 1")));
		assertThat(column("name_key", "calm")).isNull();

		assertThat(backfill.fill(loaded)).isTrue();
		assertThat(column("name_key", "calm")).isEqualTo(token + "그대로병원");
		assertThat(column("address_key", "calm")).isEqualTo("서울그대로1");
	}

	@Test
	@DisplayName("#195-S19 채워진 뒤에는 새로 가져온 장소와 같이 이름·주소로 찾는다")
	void findsBackfilledPlacesLikeNewOnes() {
		insertWithoutKeys("old", token + " 서울숲 동물병원", "서울 성동구 서울숲4길 18");
		adapter.replace(PlaceCategory.GROOMING, List.of(new PlaceDraft(PlaceCategory.GROOMING, prefix + "new",
				token + " 서울숲 펫살롱", "서울 성동구 1", null, 37.58, 127.0, false, null, null, null, null)));

		// 채우기 전에는 이전 행을 찾지 못하고, 새로 가져온 장소는 가져올 때 열이 채워져 있어 찾는다
		assertThat(names(adapter.loadByKeyword(token + "서울숲", ALL))).containsExactly(token + " 서울숲 펫살롱");

		backfill.run(new DefaultApplicationArguments());

		assertThat(names(adapter.loadByKeyword(token + "서울숲", ALL)))
			.containsExactlyInAnyOrder(token + " 서울숲 동물병원", token + " 서울숲 펫살롱");
		// 주소로도 찾는다
		assertThat(names(adapter.loadByKeyword(token + "서울숲", EnumSet.of(PlaceCategory.HOSPITAL))))
			.containsExactly(token + " 서울숲 동물병원");
	}

	@Test
	@DisplayName("#195-S19 가져오기에서 같은 장소를 다시 가져와 이름·주소가 그대로이면 열도 그대로이고, 이름이 바뀌면 열도 바뀐다")
	void keepsKeysWhenSyncDoesNotChangeThePlace() {
		PlaceDraft draft = draft("sync", token + " 스테이 동물병원", "서울 성동구 1");
		adapter.replace(PlaceCategory.HOSPITAL, List.of(draft));
		assertThat(column("name_key", "sync")).isEqualTo(token + "스테이동물병원");

		adapter.replace(PlaceCategory.HOSPITAL, List.of(draft));
		assertThat(column("name_key", "sync")).isEqualTo(token + "스테이동물병원");

		adapter.replace(PlaceCategory.HOSPITAL, List.of(draft("sync", token + " 해든 동물병원", "서울 성동구 1")));
		assertThat(column("name_key", "sync")).isEqualTo(token + "해든동물병원");
	}

	@Test
	@DisplayName("#195-S8 LIKE 특수문자(%, !, \\)가 든 검색어는 글자 그대로 찾고 모든 장소에 맞지 않는다")
	void treatsLikeSpecialCharactersAsPlainText() {
		jdbcTemplate.update("""
				insert into places (category, source_id, name, address, latitude, longitude, open24h, name_key, address_key,
				                    created_at, updated_at)
				values ('HOSPITAL', ?, ?, null, 37.58, 127.0, false, ?, null, now(), now())
				""", prefix + "special", token + "a!b%c\\d", token + "a!b%c\\d");

		assertThat(adapter.loadByKeyword(token + "a!b%c\\d", ALL)).hasSize(1);
		assertThat(adapter.loadByKeyword("b%c", ALL)).hasSize(1);
		assertThat(adapter.loadByKeyword("a!b", ALL)).hasSize(1);
		// 특수문자가 글자 그대로라서 모든 장소에 맞지 않는다
		assertThat(adapter.loadByKeyword("%%", ALL)).isEmpty();
		assertThat(adapter.loadByKeyword("a%d", ALL)).isEmpty();
		assertThat(adapter.loadByKeyword("a!!b", ALL)).isEmpty();
	}

	private static List<String> names(List<Place> places) {
		return places.stream().map(Place::name).toList();
	}

}
