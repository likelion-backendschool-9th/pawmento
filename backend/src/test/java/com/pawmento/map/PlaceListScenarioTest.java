package com.pawmento.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.pawmento.TestcontainersConfiguration;
import com.pawmento.global.security.AccessTokenCookie;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// 시나리오는 docs/map/animal-places.md(#172-S10~S15)에 있다. 영역 안의 장소를 조회하는 API(GET /maps/places)를 요청으로 확인한다.
// 장소는 DB에 직접 넣어 만든다(가져오기는 다음 PR이다). 이 테스트가 만든 행은 source_id의 접두사로 찾아 끝날 때 지운다.
// 서명 키는 테스트 전용 값이다. 실제 환경의 키로 쓰지 않는다
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "pawmento.auth.jwt-secret=test-only-jwt-secret-0123456789-abcdefghij")
@AutoConfigureMockMvc
class PlaceListScenarioTest {

	private static final String PASSWORD = "correct-password";

	// 서울 종로구 근처. 중심은 (37.5800, 127.0000)이다
	private static final double MIN_LAT = 37.5700;
	private static final double MAX_LAT = 37.5900;
	private static final double MIN_LNG = 126.9900;
	private static final double MAX_LNG = 127.0100;

	// 위도 1도는 약 111,195m다. 30m 경계를 확인할 때 쓴다(29m와 31m)
	private static final double LAT_29M = 29 / 111_195.0;
	private static final double LAT_31M = 31 / 111_195.0;

	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	private final String prefix = "test-" + UUID.randomUUID() + "-";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@AfterEach
	void deletePlaces() {
		jdbcTemplate.update("delete from places where source_id like ?", prefix + "%");
	}

	private record Guardian(long id, Cookie cookie) {
	}

	@Test
	@DisplayName("#172-S10 영역 안의 병원 3곳은 200으로 나오고 영역 밖의 1곳은 나오지 않으며, 장소마다 이름·주소·전화·좌표·24시·운영 정보를 가진다")
	void listsPlacesInTheArea() throws Exception {
		Guardian me = signUpAndLogin();
		long near = insertPlace("HOSPITAL", "a1", "대학로동물병원", 37.5801, 127.0001, "02-111-2222", false, null, null, null);
		long twentyFour = insertPlace("HOSPITAL", "a2", "24시 해든동물병원", 37.5750, 126.9950, "02-333-4444", true, "NAME", null,
				null);
		long noPhone = insertPlace("HOSPITAL", "a3", "혜화동물병원", 37.5860, 127.0060, null, false, null, null, null);
		insertPlace("HOSPITAL", "out", "영역 밖 병원", 37.6500, 127.0000, "02-999-9999", false, null, null, null);

		JsonNode body = readBody(list(me, "").andExpect(status().isOk()).andReturn());

		assertThat(body.get("truncated").asBoolean()).isFalse();
		JsonNode places = body.get("places");
		assertThat(places).hasSize(3);
		// 영역 가운데에 가까운 순서다
		assertThat(ids(places)).containsExactly(near, twentyFour, noPhone);
		JsonNode first = places.get(0);
		assertThat(first.get("id").asLong()).isEqualTo(near);
		assertThat(first.get("category").asText()).isEqualTo("HOSPITAL");
		assertThat(first.get("name").asText()).isEqualTo("대학로동물병원");
		assertThat(first.get("address").asText()).isEqualTo("서울 종로구 대학로 1");
		assertThat(first.get("phone").asText()).isEqualTo("02-111-2222");
		assertThat(first.get("latitude").asDouble()).isEqualTo(37.5801);
		assertThat(first.get("longitude").asDouble()).isEqualTo(127.0001);
		assertThat(first.get("open24h").asBoolean()).isFalse();
		assertThat(first.get("open24hBasis").isNull()).isTrue();
		// 없는 값은 필드가 사라지지 않고 null이다. 정보 없음과 빈 문자열을 구분한다
		assertThat(first.has("openingHours")).isTrue();
		assertThat(first.get("openingHours").isNull()).isTrue();
		assertThat(first.has("closedDays")).isTrue();
		assertThat(first.get("closedDays").isNull()).isTrue();
		assertThat(places.get(1).get("open24h").asBoolean()).isTrue();
		assertThat(places.get(1).get("open24hBasis").asText()).isEqualTo("NAME");
		assertThat(places.get(2).get("phone").isNull()).isTrue();
	}

	@Test
	@DisplayName("#172-S10 영역의 경계선 위에 있는 장소도 나온다")
	void includesPlacesOnTheBoundary() throws Exception {
		Guardian me = signUpAndLogin();
		long corner = insertPlace("HOSPITAL", "edge1", "경계 병원", MIN_LAT, MIN_LNG, null, false, null, null, null);
		long other = insertPlace("HOSPITAL", "edge2", "다른 경계 병원", MAX_LAT, MAX_LNG, null, false, null, null, null);

		assertThat(ids(readBody(list(me, "").andExpect(status().isOk()).andReturn()).get("places")))
			.containsExactlyInAnyOrder(corner, other);
	}

	@Test
	@DisplayName("#172-S10 영역 안에 장소가 없으면 200과 빈 목록이다")
	void emptyArea() throws Exception {
		Guardian me = signUpAndLogin();

		JsonNode body = readBody(list(me, "").andExpect(status().isOk()).andReturn());

		assertThat(body.get("places")).isEmpty();
		assertThat(body.get("truncated").asBoolean()).isFalse();
	}

	@Test
	@DisplayName("#172-S10 센터는 운영 시간과 휴무일을 가지고, 휴무 정보가 없으면 closedDays가 null이다")
	void shelterHasHoursAndClosedDays() throws Exception {
		Guardian me = signUpAndLogin();
		insertPlace("SHELTER", "s1", "남부보호센터", 37.5800, 127.0000, "051-111-2222", true, "HOURS", "00:00~24:00", "토요일+일요일");
		insertPlace("SHELTER", "s2", "북부보호센터", 37.5810, 127.0010, null, false, null, "09:00~18:00", null);

		JsonNode places = readBody(list(me, "").andExpect(status().isOk()).andReturn()).get("places");

		assertThat(places).hasSize(2);
		JsonNode withDays = places.get(0);
		assertThat(withDays.get("category").asText()).isEqualTo("SHELTER");
		assertThat(withDays.get("open24h").asBoolean()).isTrue();
		assertThat(withDays.get("open24hBasis").asText()).isEqualTo("HOURS");
		assertThat(withDays.get("openingHours").asText()).isEqualTo("00:00~24:00");
		assertThat(withDays.get("closedDays").asText()).isEqualTo("토요일+일요일");
		assertThat(places.get(1).get("closedDays").isNull()).isTrue();
	}

	@Test
	@DisplayName("#172-S11 categories를 보내면 그 종류만 나오고, 보내지 않으면 세 종류가 모두 나온다. 순서와 상관없다")
	void filtersByCategories() throws Exception {
		Guardian me = signUpAndLogin();
		long h1 = insertPlace("HOSPITAL", "h1", "병원 하나", 37.5801, 127.0001, null, false, null, null, null);
		long h2 = insertPlace("HOSPITAL", "h2", "병원 둘", 37.5802, 127.0002, null, false, null, null, null);
		long g1 = insertPlace("GROOMING", "g1", "미용 하나", 37.5803, 127.0003, null, false, null, null, null);
		long g2 = insertPlace("GROOMING", "g2", "미용 둘", 37.5804, 127.0004, null, false, null, null, null);
		long g3 = insertPlace("GROOMING", "g3", "미용 셋", 37.5805, 127.0005, null, false, null, null, null);
		long s1 = insertPlace("SHELTER", "s1", "센터 하나", 37.5806, 127.0006, null, false, null, null, null);

		assertThat(ids(places(me, "&categories=HOSPITAL,SHELTER"))).containsExactlyInAnyOrder(h1, h2, s1);
		assertThat(ids(places(me, "&categories=SHELTER,HOSPITAL"))).containsExactlyInAnyOrder(h1, h2, s1);
		assertThat(ids(places(me, "&categories=GROOMING"))).containsExactlyInAnyOrder(g1, g2, g3);
		assertThat(ids(places(me, ""))).containsExactlyInAnyOrder(h1, h2, g1, g2, g3, s1);
		assertThat(ids(places(me, "&categories=HOSPITAL,GROOMING,SHELTER"))).containsExactlyInAnyOrder(h1, h2, g1, g2,
				g3, s1);
	}

	@Test
	@DisplayName("#172-S12 영역 안에 장소가 501개 있으면 500개와 truncated=true를 받고, 영역 가운데에 가까운 것부터 준다. 정확히 500개면 false다")
	void truncatesAtFiveHundred() throws Exception {
		Guardian me = signUpAndLogin();
		// 중심(37.5800, 127.0000)에서 위도 방향으로 0.00001도씩 멀어지는 장소 501개
		jdbcTemplate.update("""
				insert into places (category, source_id, name, latitude, longitude, open24h, created_at, updated_at)
				select 'HOSPITAL', ? || g, '병원' || g, 37.5800 + g * 0.00001, 127.0000, false, now(), now()
				from generate_series(0, 500) g
				""", prefix);

		JsonNode body = readBody(list(me, "").andExpect(status().isOk()).andReturn());

		assertThat(body.get("places")).hasSize(500);
		assertThat(body.get("truncated").asBoolean()).isTrue();
		assertThat(body.get("places").get(0).get("name").asText()).isEqualTo("병원0");
		assertThat(body.get("places").get(499).get("name").asText()).isEqualTo("병원499");
		// 가장 먼 하나(병원500)가 빠진다
		List<String> names = new ArrayList<>();
		body.get("places").forEach(place -> names.add(place.get("name").asText()));
		assertThat(names).doesNotContain("병원500");

		jdbcTemplate.update("delete from places where source_id = ?", prefix + "500");
		JsonNode exact = readBody(list(me, "").andExpect(status().isOk()).andReturn());
		assertThat(exact.get("places")).hasSize(500);
		assertThat(exact.get("truncated").asBoolean()).isFalse();
	}

	@Test
	@DisplayName("#172-S13 영역의 최솟값이 최댓값보다 크거나, 위도가 91이거나, 없는 종류(CAFE·소문자)이거나, 영역을 하나라도 빼고 보내거나, 형식이 틀리면 JSON 에러가 달린 400이다")
	void invalidRequestsAreRejected() throws Exception {
		Guardian me = signUpAndLogin();
		String valid = area();

		for (String query : new String[] {
				// 최솟값이 최댓값보다 크다
				"minLatitude=37.59&minLongitude=126.99&maxLatitude=37.57&maxLongitude=127.01",
				"minLatitude=37.57&minLongitude=127.01&maxLatitude=37.59&maxLongitude=126.99",
				// 범위를 벗어난 좌표
				"minLatitude=37.57&minLongitude=126.99&maxLatitude=91&maxLongitude=127.01",
				"minLatitude=-91&minLongitude=126.99&maxLatitude=37.59&maxLongitude=127.01",
				"minLatitude=37.57&minLongitude=126.99&maxLatitude=37.59&maxLongitude=181",
				// 영역을 하나라도 뺀다
				"minLongitude=126.99&maxLatitude=37.59&maxLongitude=127.01",
				"minLatitude=37.57&maxLatitude=37.59&maxLongitude=127.01",
				"minLatitude=37.57&minLongitude=126.99&maxLongitude=127.01",
				"minLatitude=37.57&minLongitude=126.99&maxLatitude=37.59", "",
				// 숫자가 아니거나 비었다
				"minLatitude=abc&minLongitude=126.99&maxLatitude=37.59&maxLongitude=127.01",
				"minLatitude=&minLongitude=126.99&maxLatitude=37.59&maxLongitude=127.01",
				"minLatitude=NaN&minLongitude=126.99&maxLatitude=37.59&maxLongitude=127.01",
				// 같은 키를 두 번 보낸다
				valid + "&minLatitude=37.57",
				// 없는 종류, 소문자, 빈 항목, 비어 있는 값, 중복, 공백
				valid + "&categories=CAFE", valid + "&categories=hospital", valid + "&categories=HOSPITAL,CAFE",
				valid + "&categories=HOSPITAL,", valid + "&categories=,HOSPITAL", valid + "&categories=",
				valid + "&categories=HOSPITAL,HOSPITAL", valid + "&categories=HOSPITAL%20,GROOMING",
				valid + "&categories=HOSPITAL&categories=GROOMING", valid + "&categories=PHARMACY" }) {
			MvcResult result = mockMvc.perform(withRawQuery(query).cookie(me.cookie())).andReturn();
			assertThat(result.getResponse().getStatus()).as("query=%s", query).isEqualTo(400);
			assertThat(readBody(result).get("code").asText()).as("query=%s", query).isEqualTo("VALIDATION");
			assertThat(readBody(result).get("message").asText()).as("query=%s", query).isNotBlank();
		}
	}

	@Test
	@DisplayName("#172-S13 영역은 같은 이름의 요청 헤더로 채울 수 없고, 이 API가 받지 않는 파라미터는 무시한다")
	void headersDoNotFillTheArea() throws Exception {
		Guardian me = signUpAndLogin();

		MvcResult header = mockMvc
			.perform(get("/api/v1/maps/places?minLongitude=126.99&maxLatitude=37.59&maxLongitude=127.01")
				.cookie(me.cookie())
				.header("minLatitude", "37.57"))
			.andReturn();
		assertThat(header.getResponse().getStatus()).isEqualTo(400);
		assertThat(readBody(header).get("message").asText()).isEqualTo("minLatitude를 입력해 주세요.");

		// 받지 않는 파라미터(petId 포함)는 값과 인코딩을 보지 않고 지나간다
		assertThat(mockMvc.perform(withRawQuery(area() + "&petId=abc&utm_source=%E0").cookie(me.cookie()))
			.andReturn()
			.getResponse()
			.getStatus()).isEqualTo(200);
	}

	@Test
	@DisplayName("#172-S14 로그인 없이는 401이다")
	void requiresLogin() throws Exception {
		mockMvc.perform(get("/api/v1/maps/places?" + area())).andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("#172-S14 반려동물과 상관없이 로그인한 회원이면 누구나 같은 장소를 본다")
	void anyLoggedInMemberSeesTheSamePlaces() throws Exception {
		Guardian first = signUpAndLogin();
		Guardian second = signUpAndLogin();
		long place = insertPlace("HOSPITAL", "shared", "공용 병원", 37.5801, 127.0001, null, false, null, null, null);

		assertThat(ids(places(first, ""))).containsExactly(place);
		assertThat(ids(places(second, ""))).containsExactly(place);
	}

	@Test
	@DisplayName("#172-S15 병원과 미용을 둘 다 요청했을 때 같은 이름이 30m 안에 있으면 병원만 나오고, 30m보다 멀면 둘 다 나온다")
	void overlappingGroomingIsHiddenWhenBothAreRequested() throws Exception {
		Guardian me = signUpAndLogin();
		long hospital = insertPlace("HOSPITAL", "h1", "북악동물병원", 37.5800, 127.0000, null, false, null, null, null);
		insertPlace("GROOMING", "g1", "북악동물병원", 37.5800 + LAT_29M, 127.0000, null, false, null, null, null);
		long farHospital = insertPlace("HOSPITAL", "h2", "한강동물병원", 37.5850, 127.0050, null, false, null, null, null);
		long farGrooming = insertPlace("GROOMING", "g2", "한강동물병원", 37.5850 + LAT_31M, 127.0050, null, false, null, null,
				null);
		// 이름이 다르면 가까워도 둘 다 나온다
		long otherName = insertPlace("GROOMING", "g3", "다른 미용실", 37.5800, 127.0000, null, false, null, null, null);

		List<Long> both = ids(places(me, "&categories=HOSPITAL,GROOMING"));

		assertThat(both).containsExactlyInAnyOrder(hospital, farHospital, farGrooming, otherName);
	}

	@Test
	@DisplayName("#172-S15 미용만 요청하면 병원과 겹쳐도 그대로 나온다")
	void groomingAloneIsNotHidden() throws Exception {
		Guardian me = signUpAndLogin();
		insertPlace("HOSPITAL", "h1", "북악동물병원", 37.5800, 127.0000, null, false, null, null, null);
		long grooming = insertPlace("GROOMING", "g1", "북악동물병원", 37.5800 + LAT_29M, 127.0000, null, false, null, null,
				null);

		assertThat(ids(places(me, "&categories=GROOMING"))).containsExactly(grooming);
		// 병원과 센터를 요청하는 조회는 미용과 상관없다
		assertThat(ids(places(me, "&categories=HOSPITAL,SHELTER"))).hasSize(1);
	}

	@Test
	@DisplayName("#172-S15 겹치는 병원이 영역 밖에 있으면 영역 안의 미용 장소는 그대로 나온다")
	void overlapIsDecidedOnlyAmongPlacesInTheArea() throws Exception {
		Guardian me = signUpAndLogin();
		// 병원은 영역 위쪽 경계 바로 밖, 미용은 경계 바로 안이다(둘은 10m 안이다)
		insertPlace("HOSPITAL", "h1", "경계동물병원", MAX_LAT + 0.00005, 127.0000, null, false, null, null, null);
		long grooming = insertPlace("GROOMING", "g1", "경계동물병원", MAX_LAT - 0.00004, 127.0000, null, false, null, null,
				null);

		assertThat(ids(places(me, "&categories=HOSPITAL,GROOMING"))).containsExactly(grooming);
	}

	@Test
	@DisplayName("#172-S15 겹침을 지운 뒤 500곳이면 truncated=false이고, 501곳이면 500곳과 truncated=true다")
	void truncationIsDecidedAfterRemovingOverlaps() throws Exception {
		Guardian me = signUpAndLogin();
		// 병원 300곳과 미용 300곳, 그중 100곳은 같은 이름·같은 자리다 → 겹침을 지우면 500곳
		jdbcTemplate.update("""
				insert into places (category, source_id, name, latitude, longitude, open24h, created_at, updated_at)
				select 'HOSPITAL', ? || 'h' || g, '병원' || g, 37.5800 + g * 0.00001, 127.0000, false, now(), now()
				from generate_series(0, 299) g
				""", prefix);
		jdbcTemplate.update("""
				insert into places (category, source_id, name, latitude, longitude, open24h, created_at, updated_at)
				select 'GROOMING', ? || 'g' || g,
				       case when g < 100 then '병원' || g else '미용' || g end,
				       37.5800 + g * 0.00001, 127.0001, false, now(), now()
				from generate_series(0, 299) g
				""", prefix);

		JsonNode atLimit = readBody(list(me, "&categories=HOSPITAL,GROOMING").andExpect(status().isOk()).andReturn());
		assertThat(atLimit.get("places")).hasSize(500);
		assertThat(atLimit.get("truncated").asBoolean()).isFalse();

		// 겹치지 않는 미용 한 곳을 더하면 겹침을 지운 뒤 501곳이다
		insertPlace("GROOMING", "extra", "겹치지 않는 미용실", 37.5890, 127.0090, null, false, null, null, null);
		JsonNode over = readBody(list(me, "&categories=HOSPITAL,GROOMING").andExpect(status().isOk()).andReturn());
		assertThat(over.get("places")).hasSize(500);
		assertThat(over.get("truncated").asBoolean()).isTrue();
	}

	// ---- 도우미

	private String area() {
		return "minLatitude=%s&minLongitude=%s&maxLatitude=%s&maxLongitude=%s".formatted(MIN_LAT, MIN_LNG, MAX_LAT,
				MAX_LNG);
	}

	private ResultActions list(Guardian guardian, String extra) throws Exception {
		// 실제 요청처럼 URL에 쿼리를 붙인다. MockMvc의 param()은 원본 쿼리 문자열을 비워 둔다
		return mockMvc.perform(get("/api/v1/maps/places?" + area() + extra).cookie(guardian.cookie()));
	}

	private JsonNode places(Guardian guardian, String extra) throws Exception {
		return readBody(list(guardian, extra).andExpect(status().isOk()).andReturn()).get("places");
	}

	private static List<Long> ids(JsonNode places) {
		List<Long> ids = new ArrayList<>();
		places.forEach(place -> ids.add(place.get("id").asLong()));
		return ids;
	}

	// 인코딩한 키·값과 잘못된 인코딩을 있는 그대로 보낸다. %ZZ처럼 URI가 아닌 값은 원본 쿼리 문자열만 넣는다
	private static MockHttpServletRequestBuilder withRawQuery(String rawQuery) {
		try {
			return get(java.net.URI.create("/api/v1/maps/places?" + rawQuery));
		}
		catch (IllegalArgumentException invalidUri) {
			return get("/api/v1/maps/places").with(request -> {
				request.setQueryString(rawQuery);
				return request;
			});
		}
	}

	// 장소를 DB에 직접 넣는다. 주소는 "서울 종로구 대학로 1"로 고정한다
	private long insertPlace(String category, String sourceId, String name, double latitude, double longitude,
			String phone, boolean open24h, String open24hBasis, String openingHours, String closedDays) {
		return jdbcTemplate.queryForObject("""
				insert into places (category, source_id, name, address, phone, latitude, longitude, open24h, open24h_basis,
				                    opening_hours, closed_days, source_updated_at, created_at, updated_at)
				values (?, ?, ?, '서울 종로구 대학로 1', ?, ?, ?, ?, ?, ?, ?, ?, now(), now()) returning id
				""", Long.class, category, prefix + sourceId, name, phone, latitude, longitude, open24h, open24hBasis,
				openingHours, closedDays, Timestamp.from(Instant.parse("2026-11-21T12:00:00Z")));
	}

	private Guardian signUpAndLogin() throws Exception {
		String email = "guardian-" + UUID.randomUUID() + "@example.com";
		String body = "{\"email\":\"%s\",\"password\":\"%s\",\"nickname\":\"보호자\"}".formatted(email, PASSWORD);
		MvcResult signUp = mockMvc
			.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isCreated())
			.andReturn();
		long id = readBody(signUp).get("id").asLong();
		ResultActions login = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)));
		Cookie cookie = login.andExpect(status().isOk()).andReturn().getResponse().getCookie(AccessTokenCookie.NAME);
		assertThat(cookie).isNotNull();
		return new Guardian(id, new Cookie(AccessTokenCookie.NAME, cookie.getValue()));
	}

	private JsonNode readBody(MvcResult result) throws Exception {
		return jsonMapper.readTree(result.getResponse().getContentAsString());
	}

}
