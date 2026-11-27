package com.pawmento.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.pawmento.TestcontainersConfiguration;
import com.pawmento.global.security.AccessTokenCookie;
import com.pawmento.map.application.port.out.LoadRoadAddressPort;
import com.pawmento.support.SignUpTokens;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// 시나리오는 docs/map/place-address.md(#239-S2, S6, S7)에 있다. 장소 주소 조회 API(GET /maps/places/{placeId}/address)를 요청으로 확인한다.
// 장소는 DB에 직접 넣고, 네이버 호출은 가짜 포트로 대신한다. 이 테스트가 만든 행은 source_id의 접두사로 찾아 끝날 때 지운다.
// 서명 키는 테스트 전용 값이다. 실제 환경의 키로 쓰지 않는다
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "pawmento.auth.jwt-secret=test-only-jwt-secret-0123456789-abcdefghij")
@AutoConfigureMockMvc
class PlaceAddressScenarioTest {

	private static final String PASSWORD = "correct-password";

	private static final String MASKED = "서울특별시 성동구 서울숲*길 **-**, ***동 *층 ***호 (성수동*가, 갤러리아 포레)";

	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	private final String prefix = "test-" + UUID.randomUUID() + "-";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private SignUpTokens signUpTokens;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@MockitoBean
	private LoadRoadAddressPort loadRoadAddressPort;

	@AfterEach
	void deletePlaces() {
		jdbcTemplate.update("delete from places where source_id like ?", prefix + "%");
	}

	@Test
	@DisplayName("#239-S6 로그인하지 않았으면 401이고 네이버는 불리지 않는다")
	void unauthorizedWithoutLogin() throws Exception {
		long placeId = insertPlace("a1", MASKED);

		mockMvc.perform(get("/api/v1/maps/places/" + placeId + "/address")).andExpect(status().isUnauthorized());

		verify(loadRoadAddressPort, never()).loadRoadAddressByCoordinates(anyDouble(), anyDouble());
	}

	@Test
	@DisplayName("#239-S6 없는 장소이거나 양의 정수가 아니면 404이거나 400이고 네이버는 불리지 않는다")
	void notFoundOrBadRequestForUnknownPlace() throws Exception {
		Cookie me = signUpAndLogin();

		mockMvc.perform(get("/api/v1/maps/places/999999999/address").cookie(me)).andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/maps/places/0/address").cookie(me)).andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/maps/places/abc/address").cookie(me)).andExpect(status().isBadRequest());

		verify(loadRoadAddressPort, never()).loadRoadAddressByCoordinates(anyDouble(), anyDouble());
	}

	@Test
	@DisplayName("#239-S2 가려지지 않은 주소의 장소는 address가 null이고 네이버는 불리지 않는다")
	void nullAddressWhenNotMasked() throws Exception {
		Cookie me = signUpAndLogin();
		long placeId = insertPlace("a1", "서울 종로구 대학로 1");

		JsonNode body = read(mockMvc.perform(get("/api/v1/maps/places/" + placeId + "/address").cookie(me))
			.andExpect(status().isOk())
			.andReturn());

		assertThat(body.has("address")).isTrue();
		assertThat(body.get("address").isNull()).isTrue();
		verify(loadRoadAddressPort, never()).loadRoadAddressByCoordinates(anyDouble(), anyDouble());
	}

	@Test
	@DisplayName("#239-S6 가려진 주소의 장소는 저장된 좌표로 찾은 주소를 200으로 돌려준다")
	void resolvesMaskedAddress() throws Exception {
		Cookie me = signUpAndLogin();
		long placeId = insertPlace("a1", MASKED);
		when(loadRoadAddressPort.loadRoadAddressByCoordinates(37.5445, 127.0374))
			.thenReturn(Optional.of("서울특별시 성동구 서울숲2길 32-14"));

		JsonNode body = read(mockMvc.perform(get("/api/v1/maps/places/" + placeId + "/address").cookie(me))
			.andExpect(status().isOk())
			.andReturn());

		assertThat(body.get("address").asText()).isEqualTo("서울특별시 성동구 서울숲2길 32-14 (갤러리아 포레)");
	}

	@Test
	@DisplayName("#239-S3 네이버가 주소를 못 찾으면 200이고 address는 null이다")
	void nullAddressWhenNothingFound() throws Exception {
		Cookie me = signUpAndLogin();
		long placeId = insertPlace("a1", MASKED);
		when(loadRoadAddressPort.loadRoadAddressByCoordinates(37.5445, 127.0374)).thenReturn(Optional.empty());

		JsonNode body = read(mockMvc.perform(get("/api/v1/maps/places/" + placeId + "/address").cookie(me))
			.andExpect(status().isOk())
			.andReturn());

		assertThat(body.get("address").isNull()).isTrue();
	}

	@Test
	@DisplayName("#239-S7 주소를 조회해도 places의 주소와 좌표는 바뀌지 않는다")
	void doesNotChangeStoredPlace() throws Exception {
		Cookie me = signUpAndLogin();
		long placeId = insertPlace("a1", MASKED);
		when(loadRoadAddressPort.loadRoadAddressByCoordinates(37.5445, 127.0374))
			.thenReturn(Optional.of("서울특별시 성동구 서울숲2길 32-14"));

		mockMvc.perform(get("/api/v1/maps/places/" + placeId + "/address").cookie(me)).andExpect(status().isOk());

		var row = jdbcTemplate.queryForMap("select address, latitude, longitude from places where id = ?", placeId);
		assertThat(row.get("address")).isEqualTo(MASKED);
		assertThat(((Number) row.get("latitude")).doubleValue()).isEqualTo(37.5445);
		assertThat(((Number) row.get("longitude")).doubleValue()).isEqualTo(127.0374);
	}

	private long insertPlace(String sourceId, String address) {
		return jdbcTemplate.queryForObject("""
				insert into places (category, source_id, name, address, phone, latitude, longitude, open24h, open24h_basis,
				                    opening_hours, closed_days, source_updated_at, created_at, updated_at)
				values ('HOSPITAL', ?, '갤러리아 동물병원', ?, null, 37.5445, 127.0374, false, null, null, null, ?, now(), now())
				returning id
				""", Long.class, prefix + sourceId, address, Timestamp.from(Instant.parse("2026-11-21T12:00:00Z")));
	}

	private Cookie signUpAndLogin() throws Exception {
		String email = "guardian-" + UUID.randomUUID() + "@example.com";
		String body = "{\"email\":\"%s\",\"password\":\"%s\",\"nickname\":\"보호자\",\"verificationToken\":\"%s\"}"
			.formatted(email, PASSWORD, signUpTokens.issue(email));
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
