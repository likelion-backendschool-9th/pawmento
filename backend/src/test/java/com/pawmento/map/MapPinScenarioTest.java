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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultActions;

import com.pawmento.TestcontainersConfiguration;
import com.pawmento.global.security.AccessTokenCookie;
import com.pawmento.map.application.port.out.SaveRecordLocationPort;
import com.pawmento.map.domain.RecordLocation;

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
	private SaveRecordLocationPort saveRecordLocationPort;

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
			.andExpect(jsonPath("$.pins[0].recordId").value(inside))
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
			.andExpect(jsonPath("$.pins[0].recordId").value(onMin))
			.andExpect(jsonPath("$.pins[0].placeName").value((Object) null))
			.andExpect(jsonPath("$.pins[1].recordId").value(onMax));
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
			.andExpect(jsonPath("$.pins[0].recordId").value(mine));
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
			.andExpect(jsonPath("$.message").value("minLatitude 값의 형식이 올바르지 않습니다."));
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

		mockMvc.perform(get("/api/v1/pets/abc/map-pins?" + AREA).cookie(me.cookie()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION"))
			.andExpect(jsonPath("$.message").value("petId 값의 형식이 올바르지 않습니다."));
	}

	@Test
	@DisplayName("#19-S6 영역 안의 핀이 500개를 넘으면 500개만 오래된 순으로 돌려주고 잘렸다고 알려 준다")
	void truncatesOverLimit() throws Exception {
		Guardian me = signUpAndLogin();
		long first = 0;
		for (int i = 0; i < 501; i++) {
			long recordId = saveLocation(me, PET, 37.5, 127.0, null);
			if (i == 0) {
				first = recordId;
			}
		}

		JsonNode body = readBody(mockMvc.perform(pins(me, PET, AREA)).andExpect(status().isOk()).andReturn());

		assertThat(body.get("pins")).hasSize(500);
		assertThat(body.get("truncated").asBoolean()).isTrue();
		// 가장 오래된 기록부터 500개이고, 501번째는 빠진다
		assertThat(body.get("pins").get(0).get("recordId").asLong()).isEqualTo(first);
		assertThat(body.get("pins").get(499).get("recordId").asLong()).isEqualTo(first + 499);
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
		mockMvc.perform(get("/api/v1/pets/" + PET + "/map-pins?" + AREA))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
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
			.andExpect(jsonPath("$.pins[0].recordId").value(mine));
		mockMvc.perform(pins(other, PET, AREA))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.pins.length()").value(1))
			.andExpect(jsonPath("$.pins[0].recordId").value(theirs));
	}

	private long saveLocation(Guardian guardian, long petId, double latitude, double longitude, String placeName) {
		long recordId = RECORD_IDS.incrementAndGet();
		saveRecordLocationPort.save(RecordLocation.create(recordId, petId, guardian.id(), latitude, longitude, placeName));
		return recordId;
	}

	private RequestBuilder pins(Guardian guardian, long petId, String query) {
		return get("/api/v1/pets/" + petId + "/map-pins" + (query.isEmpty() ? "" : "?" + query))
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
