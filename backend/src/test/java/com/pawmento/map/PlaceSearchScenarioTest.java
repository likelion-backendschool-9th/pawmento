package com.pawmento.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.pawmento.TestcontainersConfiguration;
import com.pawmento.global.security.AccessTokenCookie;
import com.pawmento.map.domain.Place;
import com.pawmento.map.domain.PlaceCategory;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// 시나리오는 docs/map/place-search.md(#195-S1~S8)에 있다. 이름·주소 검색 API(GET /maps/places/search)를 요청으로 확인한다.
// 장소는 DB에 직접 넣는다. 같은 DB를 쓰는 다른 테스트의 장소가 섞이지 않게, 이 테스트의 장소 이름과 검색어에는 테스트마다 다른
// 영문 표지(token)를 넣는다. 이 테스트가 만든 행은 source_id의 접두사로 찾아 끝날 때 지운다.
// 서명 키는 테스트 전용 값이다. 실제 환경의 키로 쓰지 않는다
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "pawmento.auth.jwt-secret=test-only-jwt-secret-0123456789-abcdefghij")
@AutoConfigureMockMvc
class PlaceSearchScenarioTest {

	private static final String PASSWORD = "correct-password";

	// 서울 종로구 근처. 지도 중심이다
	private static final double LAT = 37.5800;

	private static final double LNG = 127.0000;

	// 위도 1도는 약 111,195m다
	private static final double METER = 1 / 111_195.0;

	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	private final String prefix = "test-" + UUID.randomUUID() + "-";

	// 영문 소문자와 숫자라 정규화(소문자·구두점 제거)해도 그대로이고, 이름과 검색어에 넣어 이 테스트의 장소만 찾게 한다
	private final String token = "k" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@AfterEach
	void deletePlaces() {
		jdbcTemplate.update("delete from places where source_id like ?", prefix + "%");
		jdbcTemplate.update("delete from place_sync_runs");
	}

	@Test
	@DisplayName("#195-S1 이름과 주소 중 한 곳에 검색어가 들어 있는 장소를 찾고, 영역 조회와 같은 필드에 거리와 dataAsOf를 더해 준다")
	void findsByNameOrAddress() throws Exception {
		Cookie me = signUpAndLogin();
		long byName = insertPlace("HOSPITAL", "a1", token + " 동물병원", "서울 종로구 대학로 1", LAT, LNG, "02-111-2222");
		long byAddress = insertPlace("GROOMING", "a2", "보리네 펫살롱", "서울 성동구 " + token + "4길 18", LAT, LNG, null);
		insertPlace("HOSPITAL", "a3", "강남 24시 동물병원", "서울 강남구 1", LAT, LNG, null);
		insertSyncRun("HOSPITAL");
		insertSyncRun("GROOMING");

		JsonNode body = read(search(me, "q=" + token).andExpect(status().isOk()).andReturn());

		assertThat(body.get("truncated").asBoolean()).isFalse();
		JsonNode places = body.get("places");
		assertThat(places).hasSize(2);
		// 이름이 검색어로 시작하는 곳이 먼저, 주소에만 든 곳이 나중이다(지도 중심 없음)
		assertThat(places.get(0).get("id").asLong()).isEqualTo(byName);
		assertThat(places.get(1).get("id").asLong()).isEqualTo(byAddress);
		JsonNode first = places.get(0);
		assertThat(first.get("category").asText()).isEqualTo("HOSPITAL");
		assertThat(first.get("name").asText()).isEqualTo(token + " 동물병원");
		assertThat(first.get("address").asText()).isEqualTo("서울 종로구 대학로 1");
		assertThat(first.get("phone").asText()).isEqualTo("02-111-2222");
		assertThat(first.get("latitude").asDouble()).isEqualTo(LAT);
		assertThat(first.get("longitude").asDouble()).isEqualTo(LNG);
		assertThat(first.get("open24h").asBoolean()).isFalse();
		assertThat(first.has("open24hBasis")).isTrue();
		assertThat(first.has("openingHours")).isTrue();
		assertThat(first.has("closedDays")).isTrue();
		assertThat(first.has("distanceMeters")).isTrue();
		assertThat(first.get("distanceMeters").isNull()).isTrue();
		assertThat(places.get(1).get("phone").isNull()).isTrue();
		assertThat(body.has("dataAsOf")).isTrue();
		assertThat(body.get("dataAsOf").isNull()).isFalse();
	}

	@Test
	@DisplayName("#195-S2 띄어쓰기·괄호·구두점·대소문자는 무시하고, 글자가 이어서 들어 있어야 찾는다")
	void ignoresSpacingAndPunctuation() throws Exception {
		Cookie me = signUpAndLogin();
		long id = insertPlace("HOSPITAL", "a1", token + "스테이동물병원", "서울 종로구 1", LAT, LNG, null);

		for (String q : List.of(token + "스테이 동물병원", token + "스테이(동물병원)", token + "스테이동물 병원",
				token.toUpperCase() + "스테이동물병원")) {
			JsonNode places = read(search(me, "q=" + encode(q)).andExpect(status().isOk()).andReturn()).get("places");
			assertThat(places).as(q).hasSize(1);
			assertThat(places.get(0).get("id").asLong()).isEqualTo(id);
		}
		// 글자 순서가 달라지거나 중간 글자가 빠지면 찾지 않는다
		JsonNode none = read(search(me, "q=" + encode(token + "스테이병원")).andExpect(status().isOk()).andReturn())
			.get("places");
		assertThat(none).isEmpty();
	}

	@Test
	@DisplayName("#195-S3 지도 중심을 보내면 가까운 순이고 거리(m)를 주며, 보내지 않으면 이름이 검색어로 시작하는 곳 → 이름에 든 곳 → 주소에만 든 곳 순이다")
	void ordersByDistanceOrRelevance() throws Exception {
		Cookie me = signUpAndLogin();
		long a = insertPlace("HOSPITAL", "a", token + " A", "주소", LAT + 300 * METER, LNG, null);
		long b = insertPlace("HOSPITAL", "b", token + " B", "주소", LAT + 100 * METER, LNG, null);
		long c = insertPlace("HOSPITAL", "c", token + " C", "주소", LAT + 1200 * METER, LNG, null);
		long middle = insertPlace("HOSPITAL", "d", "가나 " + token + " D", "주소", LAT + 50 * METER, LNG, null);

		JsonNode near = read(search(me, "q=" + token + "&latitude=" + LAT + "&longitude=" + LNG)
			.andExpect(status().isOk())
			.andReturn()).get("places");

		assertThat(ids(near)).containsExactly(middle, b, a, c);
		assertThat(near.get(0).get("distanceMeters").asInt()).isBetween(49, 51);
		assertThat(near.get(1).get("distanceMeters").asInt()).isBetween(99, 101);
		assertThat(near.get(3).get("distanceMeters").asInt()).isBetween(1199, 1201);

		JsonNode byRelevance = read(search(me, "q=" + token).andExpect(status().isOk()).andReturn()).get("places");

		// 이름이 검색어로 시작(이름순 A, B, C) 뒤에 이름에 든 곳
		assertThat(ids(byRelevance)).containsExactly(a, b, c, middle);
		assertThat(byRelevance.get(0).get("distanceMeters").isNull()).isTrue();
	}

	@Test
	@DisplayName("#195-S4 병원·미용을 모두 요청하면 같은 이름의 겹치는 미용을 지우고, 미용만 요청하면 그대로 나오며, categories를 빼면 세 종류를 모두 찾는다")
	void filtersCategoriesAndRemovesOverlaps() throws Exception {
		Cookie me = signUpAndLogin();
		long hospital = insertPlace("HOSPITAL", "a1", token + "북악동물병원", "주소", LAT, LNG, null);
		long sameName = insertPlace("GROOMING", "a2", token + "북악 동물병원", "주소", LAT + 30 * METER, LNG, null);
		long other = insertPlace("GROOMING", "a3", token + "북악 펫살롱", "주소", LAT + 30 * METER, LNG, null);
		long shelter = insertPlace("SHELTER", "a4", token + "북악 보호센터", "주소", LAT, LNG, null);

		JsonNode both = read(search(me, "q=" + token + "&categories=HOSPITAL,GROOMING").andExpect(status().isOk())
			.andReturn()).get("places");
		assertThat(ids(both)).containsExactlyInAnyOrder(hospital, other);

		JsonNode groomingOnly = read(search(me, "q=" + token + "&categories=GROOMING").andExpect(status().isOk())
			.andReturn()).get("places");
		assertThat(ids(groomingOnly)).containsExactlyInAnyOrder(sameName, other);

		JsonNode all = read(search(me, "q=" + token).andExpect(status().isOk()).andReturn()).get("places");
		assertThat(ids(all)).containsExactlyInAnyOrder(hospital, other, shelter);
	}

	@Test
	@DisplayName("#195-S5 맞는 장소가 21개이면 앞쪽 20개와 truncated=true를 받고, 20개이면 false다")
	void truncatesAtTwentyPlaces() throws Exception {
		Cookie me = signUpAndLogin();
		for (int i = 1; i <= 21; i++) {
			insertPlace("HOSPITAL", "p" + i, "%s 병원 %02d".formatted(token, i), "주소", LAT, LNG, null);
		}

		JsonNode over = read(search(me, "q=" + token).andExpect(status().isOk()).andReturn());
		assertThat(over.get("places")).hasSize(20);
		assertThat(over.get("truncated").asBoolean()).isTrue();
		assertThat(over.get("places").get(0).get("name").asText()).isEqualTo(token + " 병원 01");

		jdbcTemplate.update("delete from places where source_id = ?", prefix + "p21");
		JsonNode exact = read(search(me, "q=" + token).andExpect(status().isOk()).andReturn());
		assertThat(exact.get("places")).hasSize(20);
		assertThat(exact.get("truncated").asBoolean()).isFalse();
	}

	@Test
	@DisplayName("#195-S6 검색어가 없거나 너무 짧거나 길고, 위도·경도가 한쪽뿐이거나 범위를 벗어나고, 종류가 없는 이름이면 400이다")
	void rejectsInvalidRequests() throws Exception {
		Cookie me = signUpAndLogin();

		List<String> notRejected = new ArrayList<>();
		for (String query : List.of("", "q=", "q=%EC%84%9C", "q=%20%20%20", "q=" + "a".repeat(31), "q=_%20_",
				"q=%EC%84%9C%EC%9A%B8&latitude=37.5", "q=%EC%84%9C%EC%9A%B8&longitude=127.0",
				"q=%EC%84%9C%EC%9A%B8&latitude=91&longitude=127.0", "q=%EC%84%9C%EC%9A%B8&categories=CAFE",
				"q=%EC%84%9C%EC%9A%B8&categories=HOSPITAL,HOSPITAL", "q=%EC%84%9C%EC%9A%B8&latitude=abc&longitude=127",
				"q=ab%00cd", "q=%00%00", "q=%EC%84%9C%EC%9A%B8%01")) {
			MvcResult result = search(me, query).andReturn();
			if (result.getResponse().getStatus() != 400 || result.getResponse().getContentAsString().isBlank()) {
				notRejected.add(query + " -> " + result.getResponse().getStatus());
			}
		}
		assertThat(notRejected).as("400이 아닌 요청").isEmpty();
		// 검색어가 너무 짧다는 이유가 응답에 담겨 화면이 안내할 수 있다
		String reason = search(me, "q=%EC%84%9C").andReturn().getResponse().getContentAsString();
		assertThat(reason).contains("2자 이상");
		// 널 문자가 든 검색어는 500이 아니라 400이고 이유에 검색어를 담지 않는다
		String nul = search(me, "q=ab%00cd").andReturn().getResponse().getContentAsString();
		assertThat(nul).contains("쓸 수 없는 글자").doesNotContain("ab");
		// 검색어는 오류 응답에 담지 않는다
		String secret = "비밀검색어" + token;
		String tooLong = search(me, "q=" + encode(secret.repeat(6))).andReturn().getResponse().getContentAsString();
		assertThat(tooLong).doesNotContain(token);
	}

	@Test
	@DisplayName("#195-S7 로그인하지 않았으면 401이다")
	void unauthorizedWithoutLogin() throws Exception {
		mockMvc.perform(get(URI.create("/api/v1/maps/places/search?q=" + token))).andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("#195-S8 맞는 장소가 없으면 200과 빈 places이고, LIKE 특수문자는 글자 그대로 찾아 모든 장소에 맞지 않는다")
	void emptyResultAndLikeEscaping() throws Exception {
		Cookie me = signUpAndLogin();
		insertPlace("HOSPITAL", "a1", token + " 동물병원", "서울 종로구 1", LAT, LNG, null);

		JsonNode none = read(search(me, "q=" + encode(token + "동물카페")).andExpect(status().isOk()).andReturn());
		assertThat(none.get("places")).isEmpty();
		assertThat(none.get("truncated").asBoolean()).isFalse();

		for (String q : List.of("%%", "a\\b", "!!", "%a%", "%_a")) {
			JsonNode places = read(search(me, "q=" + encode(q)).andExpect(status().isOk()).andReturn()).get("places");
			assertThat(places).as(q).isEmpty();
		}
	}

	private ResultActions search(Cookie guardian, String query) throws Exception {
		// 실제 요청처럼 URL에 쿼리를 붙인다. MockMvc의 param()은 원본 쿼리 문자열을 비워 둔다.
		// 문자열 주소를 주면 MockMvc가 %를 한 번 더 인코딩하므로(%EC가 %25EC가 된다) 이미 인코딩한 주소를 URI로 그대로 보낸다
		String url = "/api/v1/maps/places/search" + (query.isEmpty() ? "" : "?" + query);
		return mockMvc.perform(get(URI.create(url)).cookie(guardian));
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	private static List<Long> ids(JsonNode places) {
		List<Long> ids = new ArrayList<>();
		places.forEach(place -> ids.add(place.get("id").asLong()));
		return ids;
	}

	// 가져오기에서 장소를 저장할 때와 같이, 이름·주소를 정규화한 열(name_key, address_key)도 함께 넣는다
	private long insertPlace(String category, String sourceId, String name, String address, double latitude,
			double longitude, String phone) {
		Place place = new Place(null, PlaceCategory.valueOf(category), name, address, phone, latitude, longitude, false,
				null, null, null);
		return jdbcTemplate.queryForObject("""
				insert into places (category, source_id, name, address, phone, latitude, longitude, open24h, open24h_basis,
				                    opening_hours, closed_days, source_updated_at, name_key, address_key, created_at, updated_at)
				values (?, ?, ?, ?, ?, ?, ?, false, null, null, null, ?, ?, ?, now(), now()) returning id
				""", Long.class, category, prefix + sourceId, name, address, phone, latitude, longitude,
				Timestamp.from(Instant.parse("2026-11-21T12:00:00Z")), place.nameKey(), place.addressKey());
	}

	private void insertSyncRun(String category) {
		jdbcTemplate.update("""
				insert into place_sync_runs (category, last_synced_at) values (?, ?)
				on conflict (category) do update set last_synced_at = excluded.last_synced_at
				""", category, Timestamp.from(Instant.parse("2026-11-24T19:31:24Z")));
	}

	private Cookie signUpAndLogin() throws Exception {
		String email = "guardian-" + UUID.randomUUID() + "@example.com";
		String body = "{\"email\":\"%s\",\"password\":\"%s\",\"nickname\":\"보호자\"}".formatted(email, PASSWORD);
		mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isCreated());
		Cookie cookie = mockMvc
			.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getCookie(AccessTokenCookie.NAME);
		assertThat(cookie).isNotNull();
		return new Cookie(AccessTokenCookie.NAME, cookie.getValue());
	}

	private JsonNode read(MvcResult result) throws Exception {
		return jsonMapper.readTree(result.getResponse().getContentAsString());
	}

}
