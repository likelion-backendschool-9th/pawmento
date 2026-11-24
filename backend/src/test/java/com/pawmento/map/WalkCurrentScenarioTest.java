package com.pawmento.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

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

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// 시나리오는 docs/map/walk-resume.md에 있다. 현재 진행 중인 산책 조회 API(GET /maps/walks/current)를 요청으로 확인한다.
// 시작한 지 오래된 산책은 DB에 직접 넣어 만든다(시각을 정확히 정해야 하고, 요청으로는 과거 시각을 만들 수 없다).
// 서명 키는 테스트 전용 값이다. 실제 환경의 키로 쓰지 않는다
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "pawmento.auth.jwt-secret=test-only-jwt-secret-0123456789-abcdefghij")
@AutoConfigureMockMvc
class WalkCurrentScenarioTest {

	private static final String PASSWORD = "correct-password";

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private record Guardian(long id, Cookie cookie) {
	}

	@Test
	@DisplayName("#167-S1 진행 중인 산책이 있으면 200과 그 산책(IN_PROGRESS, endedAt 없음)과 서버에 쌓인 좌표 12개를 기록 시각 순서로 받는다")
	void returnsTheWalkInProgressWithItsPoints() throws Exception {
		Guardian me = signUpAndLogin();
		long pet = createPet(me);
		// DB는 마이크로초까지 저장하고 나노초는 반올림한다. 비교가 어긋나지 않게 처음부터 마이크로초로 맞춘다
		Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		Instant startedAt = now.minusSeconds(600);
		long walk = insertWalk(pet, me.id(), startedAt, null, now.minusSeconds(30));
		seedPoints(walk, startedAt, 12);

		JsonNode body = readBody(current(me, pet).andExpect(status().isOk()).andReturn());

		assertThat(body.get("walkId").asLong()).isEqualTo(walk);
		assertThat(Instant.parse(body.get("startedAt").asText())).isEqualTo(startedAt);
		assertThat(body.get("status").asText()).isEqualTo("IN_PROGRESS");
		assertThat(body.has("endedAt")).isTrue();
		assertThat(body.get("endedAt").isNull()).isTrue();
		assertThat(body.get("points")).hasSize(12);
		Instant previous = Instant.MIN;
		for (JsonNode point : body.get("points")) {
			Instant recordedAt = Instant.parse(point.get("recordedAt").asText());
			assertThat(recordedAt).isAfter(previous);
			previous = recordedAt;
			assertThat(point.get("latitude").isNumber()).isTrue();
			assertThat(point.get("longitude").isNumber()).isTrue();
		}
	}

	@Test
	@DisplayName("#167-S1 API로 시작하고 좌표를 쌓은 산책을 새로고침 뒤에 그대로 받고, 이어서 쌓은 좌표도 같은 산책에 나온다")
	void continuesAWalkStartedThroughTheApi() throws Exception {
		Guardian me = signUpAndLogin();
		long pet = createPet(me);
		long walk = startedWalk(me, pet);
		addPoints(me, walk, Instant.now(), 37.5);

		JsonNode first = readBody(current(me, pet).andExpect(status().isOk()).andReturn());
		assertThat(first.get("walkId").asLong()).isEqualTo(walk);
		assertThat(first.get("points")).hasSize(1);
		assertThat(first.get("points").get(0).get("latitude").asDouble()).isEqualTo(37.5);

		addPoints(me, walk, Instant.now().plusSeconds(15), 37.6);
		JsonNode second = readBody(current(me, pet).andExpect(status().isOk()).andReturn());
		assertThat(second.get("walkId").asLong()).isEqualTo(walk);
		assertThat(second.get("points")).hasSize(2);
	}

	@Test
	@DisplayName("#167-S2 이틀 전에 시작해 4분 전까지 좌표를 받은 산책도 시작 날짜와 상관없이 받는다. 날짜별 조회의 오늘에는 나오지 않는다")
	void findsAWalkStartedTwoDaysAgo() throws Exception {
		Guardian me = signUpAndLogin();
		long pet = createPet(me);
		Instant now = Instant.now();
		Instant startedAt = now.minusSeconds(48 * 3600);
		long walk = insertWalk(pet, me.id(), startedAt, null, now.minusSeconds(240));
		seedPoints(walk, startedAt, 100);

		JsonNode body = readBody(current(me, pet).andExpect(status().isOk()).andReturn());
		assertThat(body.get("walkId").asLong()).isEqualTo(walk);
		assertThat(body.get("status").asText()).isEqualTo("IN_PROGRESS");
		assertThat(body.get("points")).hasSize(100);

		// 날짜별 조회는 시작한 날에만 산책을 돌려준다. 오늘 조회에는 나오지 않는다(이 조회를 따로 두는 이유다)
		String today = LocalDate.now(SEOUL).toString();
		JsonNode todays = readBody(mockMvc
			.perform(get("/api/v1/maps/walks?petId=" + pet + "&date=" + today).cookie(me.cookie()))
			.andExpect(status().isOk())
			.andReturn());
		assertThat(todays.get("walks")).isEmpty();
	}

	@Test
	@DisplayName("#167-S11 첫 좌표를 보내기 전이라도 시작한 지 5분 이내면 200과 points가 빈 목록인 산책을 받고, 5분이 넘으면 204다")
	void aWalkWithoutPointsIsInProgressUntilFiveMinutes() throws Exception {
		Guardian me = signUpAndLogin();
		long pet = createPet(me);
		long walk = startedWalk(me, pet);

		JsonNode body = readBody(current(me, pet).andExpect(status().isOk()).andReturn());
		assertThat(body.get("walkId").asLong()).isEqualTo(walk);
		assertThat(body.get("status").asText()).isEqualTo("IN_PROGRESS");
		assertThat(body.get("points")).isEmpty();

		// 좌표 없이 6분이 지난 산책은 중단이다. 마지막 활동 시각은 시작 시각이다
		jdbcTemplate.update("update walks set started_at = ?, last_activity_at = ? where id = ?",
				Timestamp.from(Instant.now().minusSeconds(360)), Timestamp.from(Instant.now().minusSeconds(360)), walk);
		current(me, pet).andExpect(status().isNoContent());
	}

	@Test
	@DisplayName("#167-S3 진행 중인 산책이 없으면 204다: 한 번도 시작하지 않았거나, 끝냈거나, 마지막 활동이 6분 전이라 중단됐다")
	void noContentWhenNothingIsInProgress() throws Exception {
		Guardian me = signUpAndLogin();
		long pet = createPet(me);
		current(me, pet).andExpect(status().isNoContent());

		Instant now = Instant.now();
		// 끝난 산책(마지막 활동은 방금)
		long ended = insertWalk(pet, me.id(), now.minusSeconds(1800), now.minusSeconds(60), now.minusSeconds(60));
		seedPoints(ended, now.minusSeconds(1800), 10);
		current(me, pet).andExpect(status().isNoContent());

		// 끝내지 않았지만 마지막 활동이 6분 전이라 중단된 산책
		long interrupted = insertWalk(pet, me.id(), now.minusSeconds(3600), null, now.minusSeconds(360));
		seedPoints(interrupted, now.minusSeconds(3600), 10);
		current(me, pet).andExpect(status().isNoContent());
	}

	@Test
	@DisplayName("#167-S3 204 응답에는 본문이 없다")
	void noContentHasNoBody() throws Exception {
		Guardian me = signUpAndLogin();
		long pet = createPet(me);

		MvcResult result = current(me, pet).andExpect(status().isNoContent()).andReturn();

		assertThat(result.getResponse().getContentAsString()).isEmpty();
	}

	@Test
	@DisplayName("#167-S4 좌표가 5,000개에 이른 진행 중인 산책도 저장된 좌표 5,000개를 모두 돌려준다")
	void returnsAllPointsOfAWalkAtTheLimit() throws Exception {
		Guardian me = signUpAndLogin();
		long pet = createPet(me);
		Instant now = Instant.now();
		Instant startedAt = now.minusSeconds(5000 * 15L);
		long walk = insertWalk(pet, me.id(), startedAt, null, now.minusSeconds(15));
		seedPoints(walk, startedAt, 5000);

		JsonNode body = readBody(current(me, pet).andExpect(status().isOk()).andReturn());

		assertThat(body.get("status").asText()).isEqualTo("IN_PROGRESS");
		assertThat(body.get("points")).hasSize(5000);
	}

	@Test
	@DisplayName("#167-S5 로그인 없이는 401이고, 다른 회원은 내 반려동물을 조회해도 204이며 내 산책과 경로는 나오지 않는다")
	void onlyMyWalk() throws Exception {
		Guardian owner = signUpAndLogin();
		Guardian other = signUpAndLogin();
		long pet = createPet(owner);
		Instant now = Instant.now();
		long walk = insertWalk(pet, owner.id(), now.minusSeconds(600), null, now.minusSeconds(30));
		seedPoints(walk, now.minusSeconds(600), 5);

		mockMvc.perform(get("/api/v1/maps/walks/current?petId=" + pet)).andExpect(status().isUnauthorized());
		MvcResult otherResult = current(other, pet).andExpect(status().isNoContent()).andReturn();
		assertThat(otherResult.getResponse().getContentAsString()).isEmpty();
		assertThat(readBody(current(owner, pet).andExpect(status().isOk()).andReturn()).get("walkId").asLong())
			.isEqualTo(walk);

		// 다른 회원이 같은 반려동물에 만든 산책은 내 조회에 나오지 않고, 그 회원 조회에만 나온다
		long othersWalk = insertWalk(pet, other.id(), now.minusSeconds(300), null, now.minusSeconds(10));
		assertThat(readBody(current(owner, pet).andExpect(status().isOk()).andReturn()).get("walkId").asLong())
			.isEqualTo(walk);
		assertThat(readBody(current(other, pet).andExpect(status().isOk()).andReturn()).get("walkId").asLong())
			.isEqualTo(othersWalk);
	}

	@Test
	@DisplayName("#167-S5 같은 회원의 다른 반려동물 산책은 나오지 않고, 없는 반려동물은 204다")
	void otherPetsWalksAreNotReturned() throws Exception {
		Guardian me = signUpAndLogin();
		long pet = createPet(me);
		long otherPet = createPet(me);
		Instant now = Instant.now();
		insertWalk(otherPet, me.id(), now.minusSeconds(300), null, now.minusSeconds(10));

		current(me, pet).andExpect(status().isNoContent());
		assertThat(readBody(current(me, otherPet).andExpect(status().isOk()).andReturn()).get("points")).isEmpty();
		current(me, 999_999_999L).andExpect(status().isNoContent());
	}

	@Test
	@DisplayName("#167-S5 진행 중인 산책을 끝내면 다음 조회는 204다")
	void endedWalkIsNoLongerCurrent() throws Exception {
		Guardian me = signUpAndLogin();
		long pet = createPet(me);
		long walk = startedWalk(me, pet);
		current(me, pet).andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/maps/walks/" + walk + "/end").cookie(me.cookie())).andExpect(status().isOk());

		current(me, pet).andExpect(status().isNoContent());
	}

	@Test
	@DisplayName("#167-S5 petId가 없거나 형식이 틀리거나 중복이면 JSON 에러가 달린 400이고, 같은 이름의 요청 헤더로는 채울 수 없다")
	void invalidQueryIsRejected() throws Exception {
		Guardian me = signUpAndLogin();
		long pet = createPet(me);

		for (String query : new String[] { "", "petId=", "petId=abc", "petId=10.5", "petId=0", "petId=-1",
				"petId=99999999999999999999", "petId=" + pet + "&petId=" + pet, "petId=" + pet + "&%70etId=" + pet,
				"petId=%ZZ" }) {
			MvcResult result = mockMvc.perform(withRawQuery(query).cookie(me.cookie())).andReturn();
			assertThat(result.getResponse().getStatus()).as("query=%s", query).isEqualTo(400);
			assertThat(readBody(result).get("code").asText()).as("query=%s", query).isEqualTo("VALIDATION");
			assertThat(readBody(result).get("message").asText()).as("query=%s", query).isNotBlank();
		}
		MvcResult header = mockMvc.perform(get("/api/v1/maps/walks/current").cookie(me.cookie()).header("petId", pet))
			.andReturn();
		assertThat(header.getResponse().getStatus()).isEqualTo(400);
		assertThat(readBody(header).get("message").asText()).isEqualTo("petId를 입력해 주세요.");
	}

	@Test
	@DisplayName("#167-S5 이 API가 받지 않는 파라미터는 무시하고, 인코딩한 petId 키는 읽는다")
	void ignoresUnknownParametersAndReadsEncodedKeys() throws Exception {
		Guardian me = signUpAndLogin();
		long pet = createPet(me);
		Instant now = Instant.now();
		long walk = insertWalk(pet, me.id(), now.minusSeconds(300), null, now.minusSeconds(10));

		for (String query : new String[] { "%70etId=" + pet, "petId=" + pet + "&utm_source=%E0&%E0=x",
				"petId=" + pet + "&date=2026-11-17" }) {
			MvcResult result = mockMvc.perform(withRawQuery(query).cookie(me.cookie())).andReturn();
			assertThat(result.getResponse().getStatus()).as("query=%s", query).isEqualTo(200);
			assertThat(readBody(result).get("walkId").asLong()).as("query=%s", query).isEqualTo(walk);
		}
	}

	// ---- 도우미

	// 인코딩한 키·값과 잘못된 인코딩을 있는 그대로 보낸다. 유효한 URI면 요청 파라미터와 원본 쿼리 문자열을 함께 채우고,
	// %ZZ처럼 URI가 아닌 값은 원본 쿼리 문자열만 넣는다. get("...?...")은 %를 한 번 더 인코딩해서 쓰지 않는다
	private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder withRawQuery(
			String rawQuery) {
		try {
			return get(java.net.URI.create("/api/v1/maps/walks/current?" + rawQuery));
		}
		catch (IllegalArgumentException invalidUri) {
			return get("/api/v1/maps/walks/current").with(request -> {
				request.setQueryString(rawQuery);
				return request;
			});
		}
	}

	private ResultActions current(Guardian guardian, long petId) throws Exception {
		// MockMvc의 param()은 원본 쿼리 문자열을 비워 둔다. 실제 요청처럼 URL에 쿼리를 붙인다
		return mockMvc.perform(get("/api/v1/maps/walks/current?petId=" + petId).cookie(guardian.cookie()));
	}

	// 정해진 시각의 산책을 DB에 직접 넣는다. 끝낸 시각이 없으면 null이다
	private long insertWalk(long petId, long memberId, Instant startedAt, Instant endedAt, Instant lastActivityAt) {
		return jdbcTemplate.queryForObject("""
				insert into walks (pet_id, created_by, started_at, ended_at, last_activity_at, created_at, updated_at)
				values (?, ?, ?, ?, ?, now(), now()) returning id
				""", Long.class, petId, memberId, Timestamp.from(startedAt),
				endedAt == null ? null : Timestamp.from(endedAt), Timestamp.from(lastActivityAt));
	}

	// 시작 시각부터 15초 간격으로 count개의 좌표를 넣는다. 위도는 점점 커진다
	private void seedPoints(long walkId, Instant from, int count) {
		jdbcTemplate.update("""
				insert into walk_points (walk_id, latitude, longitude, recorded_at)
				select ?, 37.0 + g * 0.00001, 127.0, ?::timestamptz + (g * 15 || ' seconds')::interval
				from generate_series(0, ?) g
				""", walkId, Timestamp.from(from), count - 1);
	}

	private long startedWalk(Guardian guardian, long petId) throws Exception {
		MvcResult started = mockMvc
			.perform(post("/api/v1/maps/walks").cookie(guardian.cookie())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"petId\":%d}".formatted(petId)))
			.andExpect(status().isCreated())
			.andReturn();
		return readBody(started).get("walkId").asLong();
	}

	private void addPoints(Guardian guardian, long walkId, Instant recordedAt, double latitude) throws Exception {
		mockMvc
			.perform(post("/api/v1/maps/walks/" + walkId + "/points").cookie(guardian.cookie())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"points\":[{\"latitude\":%s,\"longitude\":127.0,\"recordedAt\":\"%s\"}]}"
					.formatted(latitude, recordedAt)))
			.andExpect(status().isNoContent());
	}

	private long createPet(Guardian guardian) throws Exception {
		MvcResult created = mockMvc
			.perform(post("/api/v1/pets").cookie(guardian.cookie())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"콩이\",\"species\":\"DOG\"}"))
			.andExpect(status().isCreated())
			.andReturn();
		return readBody(created).get("petId").asLong();
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
