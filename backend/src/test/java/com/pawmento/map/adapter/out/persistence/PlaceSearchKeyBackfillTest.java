package com.pawmento.map.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.pawmento.TestcontainersConfiguration;
import com.pawmento.map.domain.Place;
import com.pawmento.map.domain.PlaceCategory;
import com.pawmento.map.domain.PlaceDraft;

// 시나리오는 docs/map/place-search.md(#195-S19)에 있다. 검색용 열을 더하기 전에 저장한 장소를 서버가 시작할 때 채우는 부분과,
// 검색어를 LIKE 특수문자 없이 글자 그대로 찾는 부분을 진짜 Postgres로 확인한다. 각 테스트가 끝나면 되돌린다
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
	private JdbcTemplate jdbcTemplate;

	private void insertWithoutKeys(String sourceId, String name, String address) {
		jdbcTemplate.update("""
				insert into places (category, source_id, name, address, latitude, longitude, open24h, created_at, updated_at)
				values ('HOSPITAL', ?, ?, ?, 37.58, 127.0, false, now(), now())
				""", prefix + sourceId, name, address);
	}

	private String nameKeyOf(String sourceId) {
		return jdbcTemplate.queryForObject("select name_key from places where source_id = ?", String.class,
				prefix + sourceId);
	}

	private String addressKeyOf(String sourceId) {
		return jdbcTemplate.queryForObject("select address_key from places where source_id = ?", String.class,
				prefix + sourceId);
	}

	@Test
	@DisplayName("#195-S19 검색용 열이 비어 있는 장소는 시작할 때 채우고, 이미 채워진 행은 다시 쓰지 않으며, 다시 실행하면 바꿀 행이 없다")
	void fillsOnlyTheRowsThatLackSearchKeys() {
		insertWithoutKeys("a", token + " 서울숲 동물병원", "서울 성동구 서울숲(4길) 18");
		insertWithoutKeys("noaddr", token + " 주소 없는 병원", null);
		jdbcTemplate.update("""
				insert into places (category, source_id, name, address, latitude, longitude, open24h, name_key, address_key,
				                    created_at, updated_at)
				values ('HOSPITAL', ?, ?, ?, 37.58, 127.0, false, 'keep-name', 'keep-address', now(), now())
				""", prefix + "done", token + " 이미 채워진 병원", "서울 어딘가");

		int filled = backfill.backfill();

		assertThat(filled).isGreaterThanOrEqualTo(2);
		assertThat(nameKeyOf("a")).isEqualTo(token + "서울숲동물병원");
		assertThat(addressKeyOf("a")).isEqualTo("서울성동구서울숲4길18");
		assertThat(nameKeyOf("noaddr")).isEqualTo(token + "주소없는병원");
		assertThat(addressKeyOf("noaddr")).isNull();
		// 이미 채워진 행은 건드리지 않는다
		assertThat(nameKeyOf("done")).isEqualTo("keep-name");
		assertThat(addressKeyOf("done")).isEqualTo("keep-address");
		// 다시 실행하면 바꿀 행이 없다
		assertThat(backfill.backfill()).isZero();
	}

	@Test
	@DisplayName("#195-S19 채워진 뒤에는 새로 가져온 장소와 같이 이름·주소로 찾는다")
	void findsBackfilledPlacesLikeNewOnes() {
		insertWithoutKeys("old", token + " 서울숲 동물병원", "서울 성동구 서울숲4길 18");
		adapter.replace(PlaceCategory.GROOMING, List.of(new PlaceDraft(PlaceCategory.GROOMING, prefix + "new",
				token + " 서울숲 펫살롱", "서울 성동구 1", null, 37.58, 127.0, false, null, null, null, null)));

		// 채우기 전에는 이전 행을 찾지 못하고, 새로 가져온 장소는 가져올 때 열이 채워져 있어 찾는다
		assertThat(names(adapter.loadByKeyword(token + "서울숲", ALL))).containsExactly(token + " 서울숲 펫살롱");

		backfill.backfill();

		assertThat(names(adapter.loadByKeyword(token + "서울숲", ALL)))
			.containsExactlyInAnyOrder(token + " 서울숲 동물병원", token + " 서울숲 펫살롱");
		// 주소로도 찾는다
		assertThat(names(adapter.loadByKeyword(token + "서울숲", EnumSet.of(PlaceCategory.HOSPITAL))))
			.containsExactly(token + " 서울숲 동물병원");
	}

	@Test
	@DisplayName("#195-S19 가져오기에서 같은 장소를 다시 가져와 이름·주소가 그대로이면 열도 그대로이고, 이름이 바뀌면 열도 바뀐다")
	void keepsKeysWhenSyncDoesNotChangeThePlace() {
		PlaceDraft draft = new PlaceDraft(PlaceCategory.HOSPITAL, prefix + "sync", token + " 스테이 동물병원",
				"서울 성동구 1", null, 37.58, 127.0, false, null, null, null, null);
		adapter.replace(PlaceCategory.HOSPITAL, List.of(draft));
		assertThat(nameKeyOf("sync")).isEqualTo(token + "스테이동물병원");

		adapter.replace(PlaceCategory.HOSPITAL, List.of(draft));
		assertThat(nameKeyOf("sync")).isEqualTo(token + "스테이동물병원");

		PlaceDraft renamed = new PlaceDraft(PlaceCategory.HOSPITAL, prefix + "sync", token + " 해든 동물병원",
				"서울 성동구 1", null, 37.58, 127.0, false, null, null, null, null);
		adapter.replace(PlaceCategory.HOSPITAL, List.of(renamed));
		assertThat(nameKeyOf("sync")).isEqualTo(token + "해든동물병원");
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
