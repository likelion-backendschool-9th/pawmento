package com.pawmento.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

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
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultActions;

import com.pawmento.TestcontainersConfiguration;
import com.pawmento.global.security.AccessTokenCookie;
import com.pawmento.map.application.port.out.DiaryLocationStorePort;
import com.pawmento.map.domain.DiaryLocationValue;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// 시나리오는 docs/map/record-location.md에 있다.
// 위치를 저장하는 API는 아직 없어서, 저장 포트로 직접 넣고 핀 조회 API만 요청으로 확인한다.
// 서명 키는 테스트 전용 값이다. 실제 환경의 키로 쓰지 않는다
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "pawmento.auth.jwt-secret=test-only-jwt-secret-0123456789-abcdefghij")
@AutoConfigureMockMvc
class MapPinScenarioTest {

	private static final String PASSWORD = "correct-password";
	private static final long PET = 10L;

	// 테스트끼리 같은 DB를 쓰고 기록 id는 유일해야 하므로 겹치지 않게 센다
	private static final AtomicLong RECORD_IDS = new AtomicLong(1_000);

	// 서울 근처의 영역. 안쪽 좌표와 바깥 좌표를 만들 때 쓴다
	private static final String AREA = "minLatitude=37.0&minLongitude=126.0&maxLatitude=38.0&maxLongitude=128.0";

	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DiaryLocationStorePort diaryLocationStore;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private record Guardian(long id, Cookie cookie) {
	}

	@Test
	@DisplayName("#19-S5 영역 안의 핀만 조회되고 영역 밖 기록은 나오지 않는다")
	void onlyPinsInsideArea() throws Exception {
		Guardian me = signUpAndLogin();
		long inside = saveLocation(me, PET, 37.5, 127.0, "강남역");
		saveLocation(me, PET, 36.9, 127.0, null); // 위도 아래
		saveLocation(me, PET, 38.1, 127.0, null); // 위도 위
		saveLocation(me, PET, 37.5, 125.9, null); // 경도 왼쪽
		saveLocation(me, PET, 37.5, 128.1, null); // 경도 오른쪽

		mockMvc.perform(pins(me, PET, AREA))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.pins.length()").value(1))
			.andExpect(jsonPath("$.pins[0].diaryId").value(inside))
			.andExpect(jsonPath("$.pins[0].latitude").value(37.5))
			.andExpect(jsonPath("$.pins[0].longitude").value(127.0))
			.andExpect(jsonPath("$.pins[0].placeName").value("강남역"))
			// 요청한 petId가 곧 핀의 반려동물이라 응답에 담지 않는다
			.andExpect(jsonPath("$.pins[0].petId").doesNotExist())
			.andExpect(jsonPath("$.truncated").value(false));
	}

	@Test
	@DisplayName("#19-S5 영역의 경계선 위에 있는 핀도 조회되고, 장소 이름이 없으면 null이다")
	void boundaryPinsAreIncluded() throws Exception {
		Guardian me = signUpAndLogin();
		long onMin = saveLocation(me, PET, 37.0, 126.0, null);
		long onMax = saveLocation(me, PET, 38.0, 128.0, null);

		mockMvc.perform(pins(me, PET, AREA))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.pins.length()").value(2))
			.andExpect(jsonPath("$.pins[0].diaryId").value(onMin))
			.andExpect(jsonPath("$.pins[0].placeName").value((Object) null))
			.andExpect(jsonPath("$.pins[1].diaryId").value(onMax));
	}

	@Test
	@DisplayName("#19-S5 핀이 없는 영역이면 빈 목록을 받는다")
	void emptyArea() throws Exception {
		Guardian me = signUpAndLogin();

		mockMvc.perform(pins(me, PET, AREA))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.pins").isArray())
			.andExpect(jsonPath("$.pins.length()").value(0))
			.andExpect(jsonPath("$.truncated").value(false));
	}

	@Test
	@DisplayName("#19-S5 같은 영역이어도 다른 반려동물의 핀은 나오지 않는다")
	void otherPetPinsAreExcluded() throws Exception {
		Guardian me = signUpAndLogin();
		long mine = saveLocation(me, PET, 37.5, 127.0, null);
		saveLocation(me, PET + 1, 37.5, 127.0, null);

		mockMvc.perform(pins(me, PET, AREA))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.pins.length()").value(1))
			.andExpect(jsonPath("$.pins[0].diaryId").value(mine));
	}

	@Test
	@DisplayName("#19-S5 영역의 최솟값이 최댓값보다 크면 400과 이유를 받는다")
	void minGreaterThanMax() throws Exception {
		Guardian me = signUpAndLogin();

		mockMvc.perform(pins(me, PET, "minLatitude=38.0&minLongitude=126.0&maxLatitude=37.0&maxLongitude=128.0"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION"))
			.andExpect(jsonPath("$.message").value("영역의 최솟값은 최댓값보다 클 수 없습니다."));
		mockMvc.perform(pins(me, PET, "minLatitude=37.0&minLongitude=128.0&maxLatitude=38.0&maxLongitude=126.0"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION"));
	}

	@Test
	@DisplayName("#19-S5 저장된 행이 도메인 규칙에 맞지 않으면 핀 조회는 400 VALIDATION이 아니라 500 INTERNAL_ERROR이고 내부 정보는 응답에 없다")
	void storedRowBreakingDomainRuleIsServerError() throws Exception {
		Guardian me = signUpAndLogin();
		// 이모지 128개: PostgreSQL varchar(255)에는 들어가지만 Java String.length()는 256이다
		jdbcTemplate.update("""
				insert into diary_location (diary_id, pet_id, created_by, latitude, longitude, place_name, created_at, updated_at)
				values (?, ?, ?, 37.5, 127.0, ?, now(), now())
				""", RECORD_IDS.incrementAndGet(), PET, me.id(), "\uD83D\uDC36".repeat(128));

		MvcResult result = mockMvc.perform(pins(me, PET, AREA))
			.andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
			.andReturn();

		// 장소 이름 규칙 문구, 예외 이름, 행 id 같은 내부 정보를 응답에 싣지 않는다
		String body = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
		assertThat(body).doesNotContain("장소 이름").doesNotContain("Exception").doesNotContain("id=");
	}

	@Test
	@DisplayName("#19-S5 영역 값이 없거나 범위를 벗어나거나 숫자가 아니면 400을 받는다")
	void invalidAreaValues() throws Exception {
		Guardian me = signUpAndLogin();

		// 값이 없다
		mockMvc.perform(pins(me, PET, "minLatitude=37.0&minLongitude=126.0&maxLatitude=38.0"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION"));
		mockMvc.perform(pins(me, PET, "")).andExpect(status().isBadRequest());
		// 범위를 벗어났다
		mockMvc.perform(pins(me, PET, "minLatitude=-91&minLongitude=126.0&maxLatitude=38.0&maxLongitude=128.0"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION"))
			.andExpect(jsonPath("$.message").value("위도는 -90 이상 90 이하로 입력해 주세요."));
		mockMvc.perform(pins(me, PET, "minLatitude=37.0&minLongitude=126.0&maxLatitude=38.0&maxLongitude=181"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("경도는 -180 이상 180 이하로 입력해 주세요."));
		// 숫자가 아니다. 스프링 내부 메시지가 아니라 우리 에러 형식과 문구로 받는다
		mockMvc.perform(pins(me, PET, "minLatitude=abc&minLongitude=126.0&maxLatitude=38.0&maxLongitude=128.0"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION"))
			.andExpect(jsonPath("$.message").value("minLatitude는 숫자로 입력해 주세요."));
		mockMvc.perform(pins(me, PET, "minLatitude=NaN&minLongitude=126.0&maxLatitude=38.0&maxLongitude=128.0"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION"));
		mockMvc.perform(pins(me, PET, "minLatitude=Infinity&minLongitude=126.0&maxLatitude=38.0&maxLongitude=128.0"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION"));
	}

	@Test
	@DisplayName("#19-S5 반려동물 id가 숫자가 아니면 본문 없는 400이 아니라 JSON 에러를 받는다")
	void petIdIsNotANumber() throws Exception {
		Guardian me = signUpAndLogin();

		mockMvc.perform(get("/api/v1/maps/pins?petId=abc&" + AREA).cookie(me.cookie()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION"))
			.andExpect(jsonPath("$.message").value("petId는 1 이상의 정수로 입력해 주세요."));
	}

	@Test
	@DisplayName("#19-S5 반려동물 id가 없으면 JSON 에러를 받는다")
	void petIdIsMissing() throws Exception {
		Guardian me = signUpAndLogin();

		mockMvc.perform(get("/api/v1/maps/pins?" + AREA).cookie(me.cookie()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION"))
			.andExpect(jsonPath("$.message").value("petId를 입력해 주세요."));
	}

	@Test
	@DisplayName("#19-S6 영역 안의 핀이 500개를 넘으면 500개만 오래된 순으로 돌려주고 잘렸다고 알려 준다")
	void truncatesOverLimit() throws Exception {
		Guardian me = signUpAndLogin();
		long first = 0;
		for (int i = 0; i < 501; i++) {
			long diaryId = saveLocation(me, PET, 37.5, 127.0, null);
			if (i == 0) {
				first = diaryId;
			}
		}

		JsonNode body = readBody(mockMvc.perform(pins(me, PET, AREA)).andExpect(status().isOk()).andReturn());

		assertThat(body.get("pins")).hasSize(500);
		assertThat(body.get("truncated").asBoolean()).isTrue();
		// 가장 오래된 기록부터 500개이고, 501번째는 빠진다
		assertThat(body.get("pins").get(0).get("diaryId").asLong()).isEqualTo(first);
		assertThat(body.get("pins").get(499).get("diaryId").asLong()).isEqualTo(first + 499);
	}

	@Test
	@DisplayName("#19-S6 영역 안의 핀이 정확히 500개이면 잘리지 않는다")
	void exactlyLimitIsNotTruncated() throws Exception {
		Guardian me = signUpAndLogin();
		for (int i = 0; i < 500; i++) {
			saveLocation(me, PET, 37.5, 127.0, null);
		}

		JsonNode body = readBody(mockMvc.perform(pins(me, PET, AREA)).andExpect(status().isOk()).andReturn());

		assertThat(body.get("pins")).hasSize(500);
		assertThat(body.get("truncated").asBoolean()).isFalse();
	}

	@Test
	@DisplayName("#19-S7 로그인 없이 부르면 401과 JSON 에러를 받는다")
	void withoutLogin() throws Exception {
		mockMvc.perform(get("/api/v1/maps/pins?petId=" + PET + "&" + AREA))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	@DisplayName("#19-S5 petId와 영역은 같은 이름의 요청 헤더로 채울 수 없고, 같은 키를 둘 이상 보내면 첫 값을 쓰지 않고 400이다")
	void queryOnlyAndNoDuplicates() throws Exception {
		Guardian me = signUpAndLogin();

		// 헤더만 있는 petId
		MvcResult petIdHeader = mockMvc
			.perform(get("/api/v1/maps/pins?" + AREA).cookie(me.cookie()).header("petId", String.valueOf(PET)))
			.andReturn();
		assertThat(petIdHeader.getResponse().getStatus()).isEqualTo(400);
		assertThat(readBody(petIdHeader).get("message").asText()).isEqualTo("petId를 입력해 주세요.");
		// 헤더만 있는 영역 값
		MvcResult areaHeader = mockMvc
			.perform(get("/api/v1/maps/pins?petId=" + PET + "&minLongitude=126.0&maxLatitude=38.0&maxLongitude=128.0")
				.cookie(me.cookie())
				.header("minLatitude", "37.0"))
			.andReturn();
		assertThat(areaHeader.getResponse().getStatus()).isEqualTo(400);
		assertThat(readBody(areaHeader).get("message").asText()).isEqualTo("minLatitude를 입력해 주세요.");
		// 중복: 숫자도 첫 값을 쓰지 않는다
		for (String query : new String[] { "petId=" + PET + "&petId=" + (PET + 1) + "&" + AREA,
				"petId=" + PET + "&%70etId=" + PET + "&" + AREA,
				"petId=" + PET + "&" + AREA + "&minLatitude=36.0" }) {
			MvcResult result = mockMvc.perform(withRawQuery(query).cookie(me.cookie())).andReturn();
			assertThat(result.getResponse().getStatus()).as("query=%s", query).isEqualTo(400);
			assertThat(readBody(result).get("code").asText()).as("query=%s", query).isEqualTo("VALIDATION");
		}
	}

	@Test
	@DisplayName("#19-S5 인코딩한 키는 읽고, 이 API가 받지 않는 파라미터는 인코딩이 잘못돼도 무시하며, 아주 작은 지수 표기 좌표도 읽는다")
	void encodedKeysAndIgnoredParameters() throws Exception {
		Guardian me = signUpAndLogin();
		long mine = saveLocation(me, PET, 0.0, 0.0, null);

		for (String query : new String[] { "%70etId=" + PET + "&" + AREA.replace("minLatitude=37.0", "%6DinLatitude=37.0"),
				"petId=" + PET + "&" + AREA + "&utm_source=%E0&%E0=x" }) {
			MvcResult result = mockMvc.perform(withRawQuery(query).cookie(me.cookie())).andReturn();
			assertThat(result.getResponse().getStatus()).as("query=%s", query).isEqualTo(200);
		}
		// 적도 근처에서 자바스크립트가 만드는 지수 표기(1e-7)
		mockMvc
			.perform(withRawQuery("petId=" + PET + "&minLatitude=-1e-7&minLongitude=-1e-7&maxLatitude=1e-7&maxLongitude=1e-7")
				.cookie(me.cookie()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.pins.length()").value(1))
			.andExpect(jsonPath("$.pins[0].diaryId").value(mine));
	}

	@Test
	@DisplayName("#19-S5 double로 반올림하면 통과하는 값도 십진수로 검사해 400이다. 위도 90.000000000000001과 1e-400 대 0의 역전")
	void decimalPrecisionIsNotRoundedAway() throws Exception {
		Guardian me = signUpAndLogin();

		mockMvc
			.perform(withRawQuery("petId=" + PET
					+ "&minLatitude=37.0&minLongitude=126.0&maxLatitude=90.000000000000001&maxLongitude=128.0")
				.cookie(me.cookie()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION"))
			.andExpect(jsonPath("$.message").value("위도는 -90 이상 90 이하로 입력해 주세요."));
		mockMvc
			.perform(withRawQuery("petId=" + PET
					+ "&minLatitude=1e-400&minLongitude=126.0&maxLatitude=0&maxLongitude=128.0")
				.cookie(me.cookie()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION"))
			.andExpect(jsonPath("$.message").value("영역의 최솟값은 최댓값보다 클 수 없습니다."));
	}

	@Test
	@DisplayName("#19-S5 petId는 1 이상의 정수만 받고, 영역 값은 십진수만 받는다. 0, 음수, 소수, 범위를 넘는 정수, NaN·16진수·접미사는 400이다")
	void strictNumbers() throws Exception {
		Guardian me = signUpAndLogin();

		for (String query : new String[] { "petId=0&" + AREA, "petId=-1&" + AREA, "petId=1.5&" + AREA,
				"petId=99999999999999999999&" + AREA, "petId=+1&" + AREA, "petId=%201&" + AREA,
				"petId=" + PET + "&minLatitude=0x1p3&minLongitude=126.0&maxLatitude=38.0&maxLongitude=128.0",
				"petId=" + PET + "&minLatitude=37.0d&minLongitude=126.0&maxLatitude=38.0&maxLongitude=128.0",
				"petId=" + PET + "&minLatitude=1e999&minLongitude=126.0&maxLatitude=38.0&maxLongitude=128.0" }) {
			MvcResult result = mockMvc.perform(withRawQuery(query).cookie(me.cookie())).andReturn();
			assertThat(result.getResponse().getStatus()).as("query=%s", query).isEqualTo(400);
			assertThat(readBody(result).get("code").asText()).as("query=%s", query).isEqualTo("VALIDATION");
		}
	}

	@Test
	@DisplayName("#19-S7 다른 회원이 만든 핀은 같은 영역·반려동물이어도 조회되지 않는다")
	void otherMembersPinsAreHidden() throws Exception {
		Guardian me = signUpAndLogin();
		Guardian other = signUpAndLogin();
		long mine = saveLocation(me, PET, 37.5, 127.0, "내 기록");
		long theirs = saveLocation(other, PET, 37.5, 127.0, "남의 기록");

		mockMvc.perform(pins(me, PET, AREA))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.pins.length()").value(1))
			.andExpect(jsonPath("$.pins[0].diaryId").value(mine));
		mockMvc.perform(pins(other, PET, AREA))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.pins.length()").value(1))
			.andExpect(jsonPath("$.pins[0].diaryId").value(theirs));
	}

	private long saveLocation(Guardian guardian, long petId, double latitude, double longitude, String placeName) {
		long diaryId = RECORD_IDS.incrementAndGet();
		diaryLocationStore.lock(diaryId);
		diaryLocationStore.write(diaryId, petId, guardian.id(), 0, new DiaryLocationValue(latitude, longitude, placeName));
		return diaryId;
	}

	// 인코딩한 키와 잘못된 인코딩을 있는 그대로 보낸다. 유효한 URI면 요청 파라미터와 원본 쿼리 문자열을 함께 채운다
	private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder withRawQuery(
			String rawQuery) {
		try {
			return get(java.net.URI.create("/api/v1/maps/pins?" + rawQuery));
		}
		catch (IllegalArgumentException invalidUri) {
			return get("/api/v1/maps/pins").with(request -> {
				request.setQueryString(rawQuery);
				return request;
			});
		}
	}

	private RequestBuilder pins(Guardian guardian, long petId, String query) {
		return get("/api/v1/maps/pins?petId=" + petId + (query.isEmpty() ? "" : "&" + query))
			.cookie(guardian.cookie());
	}

	private Guardian signUpAndLogin() throws Exception {
		String email = "guardian-" + UUID.randomUUID() + "@example.com";
		String body = "{\"email\":\"%s\",\"password\":\"%s\",\"nickname\":\"보호자\"}".formatted(email, PASSWORD);
		MvcResult signUp = mockMvc
			.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isCreated())
			.andReturn();
		long id = readBody(signUp).get("id").asLong();
		ResultActions login = mockMvc.perform(post("/api/v1/auth/login")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)));
		Cookie cookie = login.andExpect(status().isOk()).andReturn().getResponse().getCookie(AccessTokenCookie.NAME);
		assertThat(cookie).isNotNull();
		return new Guardian(id, new Cookie(AccessTokenCookie.NAME, cookie.getValue()));
	}

	private JsonNode readBody(MvcResult result) throws Exception {
		return jsonMapper.readTree(result.getResponse().getContentAsString());
	}

}
