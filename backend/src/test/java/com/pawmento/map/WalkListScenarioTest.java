package com.pawmento.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.pawmento.TestcontainersConfiguration;
import com.pawmento.map.application.port.out.LoadWalkPointsPort;
import com.pawmento.global.security.AccessTokenCookie;
import com.pawmento.support.SignUpTokens;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// 시나리오는 docs/map/walk-route.md에 있다. 날짜별 산책 조회 API를 요청으로 확인한다.
// 오래된 날짜의 산책은 DB에 직접 넣어 만든다(시각을 정확히 정해야 하고, 요청으로는 과거 시각을 만들 수 없다).
// 서명 키는 테스트 전용 값이다. 실제 환경의 키로 쓰지 않는다
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "pawmento.auth.jwt-secret=test-only-jwt-secret-0123456789-abcdefghij")
@AutoConfigureMockMvc
class WalkListScenarioTest {

	private static final String PASSWORD = "correct-password";

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private SignUpTokens signUpTokens;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	// 산책 행을 읽은 뒤 좌표를 읽기 전에 다른 연결이 끼어들게 하려고 좌표 포트를 감시한다. 기본은 실제 호출이다
	@MockitoSpyBean
	private LoadWalkPointsPort loadWalkPointsPort;

	private record Guardian(long id, Cookie cookie) {
	}

	@Test
	@DisplayName("#73-S7 11월 17일에 시작한 산책 2개가 시작 순서로 나오고, 자정을 넘겨 중단된 산책도 17일에 나오며, 18일에 시작한 산책은 나오지 않는다")
	void listsWalksStartedOnTheDay() throws Exception {
		Guardian me = signUpAndLogin();
		long pet = createPet(me);
		// 서울 기준: 17일 10:00에 시작해 10:40에 끝낸 산책(좌표 40개)
		long ended = insertWalk(pet, me.id(), "2026-11-17T01:00:00Z", "2026-11-17T01:40:00Z", "2026-11-17T01:40:00Z");
		seedPoints(ended, "2026-11-17T01:00:00Z", 40);
		// 17일 23:50에 시작해 18일 00:20에 마지막 좌표를 받고 중단된 산책(좌표 40개)
		long interrupted = insertWalk(pet, me.id(), "2026-11-17T14:50:00Z", null, "2026-11-17T15:20:00Z");
		seedPoints(interrupted, "2026-11-17T14:50:00Z", 40);
		// 18일 09:00에 시작한 산책
		long nextDay = insertWalk(pet, me.id(), "2026-11-18T00:00:00Z", null, "2026-11-18T00:10:00Z");
		seedPoints(nextDay, "2026-11-18T00:00:00Z", 5);

		JsonNode walks = readBody(list(me, pet, "2026-11-17").andExpect(status().isOk()).andReturn()).get("walks");

		assertThat(walks).hasSize(2);
		JsonNode first = walks.get(0);
		assertThat(first.get("walkId").asLong()).isEqualTo(ended);
		assertThat(first.get("startedAt").asText()).isEqualTo("2026-11-17T01:00:00Z");
		assertThat(first.get("endedAt").asText()).isEqualTo("2026-11-17T01:40:00Z");
		assertThat(first.get("status").asText()).isEqualTo("ENDED");
		assertThat(first.get("points")).hasSize(40);
		JsonNode second = walks.get(1);
		assertThat(second.get("walkId").asLong()).isEqualTo(interrupted);
		assertThat(second.get("status").asText()).isEqualTo("INTERRUPTED");
		// 끝내지 않은 산책은 endedAt이 비어 있다(null)
		assertThat(second.get("endedAt").isNull()).isTrue();
		assertThat(second.get("points")).hasSize(40);
		// 좌표는 기록 시각 순서이고 위도·경도·시각이 모두 있다
		Instant previous = Instant.MIN;
		for (JsonNode point : second.get("points")) {
			Instant recordedAt = Instant.parse(point.get("recordedAt").asText());
			assertThat(recordedAt).isAfter(previous);
			previous = recordedAt;
			assertThat(point.get("latitude").isNumber()).isTrue();
			assertThat(point.get("longitude").isNumber()).isTrue();
		}
		assertThat(second.get("points").get(0).get("recordedAt").asText()).isEqualTo("2026-11-17T14:50:00Z");
		// 18일에 시작한 산책은 18일에 나온다
		JsonNode next = readBody(list(me, pet, "2026-11-18").andExpect(status().isOk()).andReturn()).get("walks");
		assertThat(next).hasSize(1);
		assertThat(next.get(0).get("walkId").asLong()).isEqualTo(nextDay);
		assertThat(next.get(0).get("points")).hasSize(5);
	}

	@Test
	@DisplayName("#73-S7 날짜는 서울 0시를 기준으로 나뉜다. 23:59:59.999999에 시작한 산책은 그날이고 0시 정각에 시작한 산책은 다음 날이다")
	void daysSplitAtSeoulMidnight() throws Exception {
		Guardian me = signUpAndLogin();
		long pet = createPet(me);
		long lastMoment = insertWalk(pet, me.id(), "2026-11-17T14:59:59.999999Z", null, "2026-11-17T14:59:59.999999Z");
		long midnight = insertWalk(pet, me.id(), "2026-11-17T15:00:00Z", null, "2026-11-17T15:00:00Z");
		long firstMoment = insertWalk(pet, me.id(), "2026-11-16T15:00:00Z", null, "2026-11-16T15:00:00Z");
		long justBefore = insertWalk(pet, me.id(), "2026-11-16T14:59:59.999999Z", null, "2026-11-16T14:59:59.999999Z");

		assertThat(walkIds(list(me, pet, "2026-11-17"))).containsExactly(firstMoment, lastMoment);
		assertThat(walkIds(list(me, pet, "2026-11-18"))).containsExactly(midnight);
		assertThat(walkIds(list(me, pet, "2026-11-16"))).containsExactly(justBefore);
	}

	@Test
	@DisplayName("#73-S8 그 날 시작한 산책이 없으면 200과 빈 목록을 받는다")
	void emptyDay() throws Exception {
		Guardian me = signUpAndLogin();
		long pet = createPet(me);
		insertWalk(pet, me.id(), "2026-11-18T00:00:00Z", null, "2026-11-18T00:10:00Z");

		list(me, pet, "2026-11-17").andExpect(status().isOk()).andExpect(jsonPath("$.walks").isEmpty());
		// 미래 날짜와 없는 반려동물도 형식이 맞으면 빈 목록이다
		list(me, pet, "2099-01-01").andExpect(status().isOk()).andExpect(jsonPath("$.walks").isEmpty());
		list(me, 999_999_999L, "2026-11-17").andExpect(status().isOk()).andExpect(jsonPath("$.walks").isEmpty());
	}

	@Test
	@DisplayName("#73-S9 로그인 없이는 401이고, 다른 회원은 내 반려동물을 조회해도 200과 빈 목록이며 내 산책은 나오지 않는다")
	void onlyMyWalks() throws Exception {
		Guardian owner = signUpAndLogin();
		Guardian other = signUpAndLogin();
		long pet = createPet(owner);
		long walk = insertWalk(pet, owner.id(), "2026-11-17T01:00:00Z", "2026-11-17T01:40:00Z", "2026-11-17T01:40:00Z");
		seedPoints(walk, "2026-11-17T01:00:00Z", 4);

		mockMvc.perform(get("/api/v1/maps/walks?petId=" + pet + "&date=2026-11-17"))
			.andExpect(status().isUnauthorized());
		list(other, pet, "2026-11-17").andExpect(status().isOk()).andExpect(jsonPath("$.walks").isEmpty());
		assertThat(walkIds(list(owner, pet, "2026-11-17"))).containsExactly(walk);

		// 다른 회원이 같은 반려동물에 만든 산책은 내 조회에 나오지 않는다
		long othersWalk = insertWalk(pet, other.id(), "2026-11-17T02:00:00Z", null, "2026-11-17T02:00:00Z");
		assertThat(walkIds(list(owner, pet, "2026-11-17"))).containsExactly(walk);
		assertThat(walkIds(list(other, pet, "2026-11-17"))).containsExactly(othersWalk);
	}

	@Test
	@DisplayName("#73-S9 다른 회원이 내 산책에 좌표를 쌓거나 끝내려 하면 403이고 아무것도 바뀌지 않는다")
	void othersCannotChangeMyWalk() throws Exception {
		Guardian owner = signUpAndLogin();
		Guardian other = signUpAndLogin();
		long pet = createPet(owner);
		long walk = startedWalk(owner, pet);

		mockMvc
			.perform(post("/api/v1/maps/walks/" + walk + "/points").cookie(other.cookie())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"points\":[{\"latitude\":37.5,\"longitude\":127.0,\"recordedAt\":\"2026-11-17T01:00:01Z\"}]}"))
			.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/v1/maps/walks/" + walk + "/end").cookie(other.cookie()))
			.andExpect(status().isForbidden());

		assertThat(jdbcTemplate.queryForObject("select count(*) from walk_points where walk_id = ?", Integer.class,
				walk))
			.isZero();
		assertThat(jdbcTemplate.queryForObject("select ended_at is null from walks where id = ?", Boolean.class, walk))
			.isTrue();
	}

	@Test
	@DisplayName("#73-S7 다른 반려동물의 산책은 나오지 않는다")
	void otherPetsWalksAreExcluded() throws Exception {
		Guardian me = signUpAndLogin();
		long pet = createPet(me);
		long otherPet = createPet(me);
		long mine = insertWalk(pet, me.id(), "2026-11-17T01:00:00Z", null, "2026-11-17T01:00:00Z");
		insertWalk(otherPet, me.id(), "2026-11-17T02:00:00Z", null, "2026-11-17T02:00:00Z");

		assertThat(walkIds(list(me, pet, "2026-11-17"))).containsExactly(mine);
	}

	@Test
	@DisplayName("#73-S7 방금 시작해 좌표를 쌓은 산책은 IN_PROGRESS이고 endedAt이 비어 있으며, 끝내면 ENDED와 endedAt이 나온다")
	void inProgressThenEnded() throws Exception {
		Guardian me = signUpAndLogin();
		long pet = createPet(me);
		MvcResult started = mockMvc
			.perform(post("/api/v1/maps/walks").cookie(me.cookie())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"petId\":%d}".formatted(pet)))
			.andExpect(status().isCreated())
			.andReturn();
		long walk = readBody(started).get("walkId").asLong();
		// 시작한 시각이 속한 서울 날짜로 조회한다(자정 근처에 돌아도 같은 날짜가 된다)
		String date = LocalDate.ofInstant(Instant.parse(readBody(started).get("startedAt").asText()), SEOUL)
			.toString();
		Instant recordedAt = Instant.now();
		mockMvc
			.perform(post("/api/v1/maps/walks/" + walk + "/points").cookie(me.cookie())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"points\":[{\"latitude\":37.5,\"longitude\":127.0,\"recordedAt\":\"%s\"}]}"
					.formatted(recordedAt)))
			.andExpect(status().isNoContent());

		JsonNode running = readBody(list(me, pet, date).andExpect(status().isOk()).andReturn()).get("walks").get(0);
		assertThat(running.get("status").asText()).isEqualTo("IN_PROGRESS");
		assertThat(running.get("endedAt").isNull()).isTrue();
		assertThat(running.get("points")).hasSize(1);
		assertThat(running.get("points").get(0).get("latitude").asDouble()).isEqualTo(37.5);

		mockMvc.perform(post("/api/v1/maps/walks/" + walk + "/end").cookie(me.cookie())).andExpect(status().isOk());
		JsonNode finished = readBody(list(me, pet, date).andExpect(status().isOk()).andReturn()).get("walks").get(0);
		assertThat(finished.get("status").asText()).isEqualTo("ENDED");
		assertThat(finished.get("endedAt").asText()).isNotBlank();
	}

	@Test
	@DisplayName("#73-S7 산책 행을 읽은 뒤 좌표를 읽기 전에 다른 요청의 좌표 추가가 커밋돼도, 같은 스냅샷이라 새 좌표를 담고도 INTERRUPTED가 되는 일이 없다")
	void walkAndPointsAreReadFromTheSameSnapshot() throws Exception {
		Guardian me = signUpAndLogin();
		long pet = createPet(me);
		// 마지막 활동이 10분 전이라 지금은 중단된 산책이다. 시작은 한 시간 전, 좌표는 2개다
		Instant now = Instant.now();
		Instant startedAt = now.minusSeconds(3600);
		long walk = insertWalk(pet, me.id(), startedAt.toString(), null, now.minusSeconds(600).toString());
		seedPoints(walk, startedAt.toString(), 2);
		String date = LocalDate.ofInstant(startedAt, SEOUL).toString();

		// 좌표를 읽기 직전에 다른 연결이 좌표를 추가하고 마지막 활동 시각을 지금으로 갱신해 커밋한 것과 같은 순서를 만든다
		// 훅은 조회 트랜잭션의 스레드에서 돌고 이 스레드의 JdbcTemplate은 그 트랜잭션의 읽기 전용 연결을 쓴다.
		// 다른 요청처럼 별도 스레드(별도 연결)에서 커밋한다
		doAnswer(invocation -> {
			java.util.concurrent.ExecutorService other = java.util.concurrent.Executors.newSingleThreadExecutor();
			try {
				other.submit(() -> {
					jdbcTemplate.update(
							"insert into walk_points (walk_id, latitude, longitude, recorded_at) values (?, 37.9, 127.9, ?)",
							walk, Timestamp.from(now));
					jdbcTemplate.update("update walks set last_activity_at = ? where id = ?", Timestamp.from(now), walk);
				}).get(10, java.util.concurrent.TimeUnit.SECONDS);
			}
			finally {
				other.shutdownNow();
			}
			return invocation.callRealMethod();
		}).when(loadWalkPointsPort).loadByWalkIds(anyCollection());
		JsonNode result;
		try {
			result = readBody(list(me, pet, date).andExpect(status().isOk()).andReturn()).get("walks").get(0);
		}
		finally {
			reset(loadWalkPointsPort);
		}

		// 읽기 스냅샷은 추가가 커밋되기 전의 것이다. 상태와 좌표가 서로 어긋나지 않는다:
		// 중단으로 보이면 새 좌표도 없고, 새 좌표가 있으면 진행 중이어야 한다
		String status = result.get("status").asText();
		int points = result.get("points").size();
		assertThat(status.equals("INTERRUPTED") ? points == 2 : points == 3).as("status=%s points=%d", status, points)
			.isTrue();
		// 다음 요청은 커밋된 새 상태를 읽는다
		JsonNode after = readBody(list(me, pet, date).andExpect(status().isOk()).andReturn()).get("walks").get(0);
		assertThat(after.get("status").asText()).isEqualTo("IN_PROGRESS");
		assertThat(after.get("points")).hasSize(3);
	}

	@Test
	@DisplayName("#73-S7 petId·date가 없거나 형식이 틀리면 JSON 에러가 달린 400이다. 달력에 없는 날, 다른 날짜 형식, offset 접미사, 중복도 거절한다")
	void invalidQueryIsRejected() throws Exception {
		Guardian me = signUpAndLogin();
		long pet = createPet(me);

		for (String query : new String[] { "", "petId=" + pet, "date=2026-11-17", "petId=" + pet + "&date=",
				"petId=&date=2026-11-17", "petId=abc&date=2026-11-17", "petId=10.5&date=2026-11-17",
				"petId=0&date=2026-11-17", "petId=-1&date=2026-11-17", "petId=99999999999999999999&date=2026-11-17",
				"petId=" + pet + "&date=2026-02-30", "petId=" + pet + "&date=2026-13-01",
				"petId=" + pet + "&date=20261117", "petId=" + pet + "&date=2026-1-7", "petId=" + pet + "&date=abc",
				"petId=" + pet + "&date=2026-11-17T10:00", "petId=" + pet + "&date=2026-11-17,2026-11-18",
				// offset 접미사는 스프링의 ISO.DATE가 받아 주던 형식이다
				"petId=" + pet + "&date=2026-11-17Z", "petId=" + pet + "&date=2026-11-17%2B09:00",
				"petId=" + pet + "&date=2026-11-17-09:00", "petId=" + pet + "&date=%2B10000-01-01",
				"petId=" + pet + "&date=+10000-01-01", "petId=" + pet + "&date=-0001-01-01",
				// 같은 키가 둘 이상이면 첫 값을 쓰지 않고 거절한다
				"petId=" + pet + "&petId=" + pet + "&date=2026-11-17",
				"petId=" + pet + "&%70etId=" + pet + "&date=2026-11-17",
				"petId=" + pet + "&date=2026-11-17&date=2026-11-18", "petId=%ZZ&date=2026-11-17" }) {
			MvcResult result = mockMvc.perform(withRawQuery(query).cookie(me.cookie())).andReturn();
			assertThat(result.getResponse().getStatus()).as("query=%s", query).isEqualTo(400);
			assertThat(readBody(result).get("code").asText()).as("query=%s", query).isEqualTo("VALIDATION");
			assertThat(readBody(result).get("message").asText()).as("query=%s", query).isNotBlank();
		}
	}

	@Test
	@DisplayName("#73-S7 필수 쿼리는 같은 이름의 요청 헤더로 채울 수 없다. petId·date 모두 쿼리에 있어야 한다")
	void requiredQueryIsNotFilledByHeaders() throws Exception {
		Guardian me = signUpAndLogin();
		long pet = createPet(me);

		MvcResult petIdHeader = mockMvc
			.perform(get("/api/v1/maps/walks?date=2026-11-17").cookie(me.cookie()).header("petId", String.valueOf(pet)))
			.andReturn();
		MvcResult dateHeader = mockMvc
			.perform(get("/api/v1/maps/walks?petId=" + pet).cookie(me.cookie()).header("date", "2026-11-17"))
			.andReturn();

		assertThat(petIdHeader.getResponse().getStatus()).isEqualTo(400);
		assertThat(readBody(petIdHeader).get("message").asText()).isEqualTo("petId를 입력해 주세요.");
		assertThat(dateHeader.getResponse().getStatus()).isEqualTo(400);
		assertThat(readBody(dateHeader).get("message").asText()).isEqualTo("date를 입력해 주세요.");
	}

	@Test
	@DisplayName("#73-S7 인코딩한 키는 읽고, 이 API가 받지 않는 파라미터는 인코딩이 잘못돼도 무시한다")
	void encodedKeysAndIgnoredParameters() throws Exception {
		Guardian me = signUpAndLogin();
		long pet = createPet(me);
		long walk = insertWalk(pet, me.id(), "2026-11-17T01:00:00Z", null, "2026-11-17T01:00:00Z");

		for (String query : new String[] { "%70etId=" + pet + "&date=2026-11-17",
				"petId=" + pet + "&date=2026-11-17&utm_source=%E0&%E0=x" }) {
			MvcResult result = mockMvc.perform(withRawQuery(query).cookie(me.cookie())).andReturn();
			assertThat(result.getResponse().getStatus()).as("query=%s", query).isEqualTo(200);
			assertThat(readBody(result).get("walks").get(0).get("walkId").asLong()).as("query=%s", query)
				.isEqualTo(walk);
		}
	}

	// ---- 도우미

	// 인코딩한 키·값과 잘못된 인코딩을 있는 그대로 보낸다. 유효한 URI면 요청 파라미터와 원본 쿼리 문자열을 함께 채우고
	// (이전 구현이 쓰던 파라미터 바인딩에도 같은 입력이 가도록), %ZZ처럼 URI가 아닌 값은 원본 쿼리 문자열만 넣는다.
	// get("...?...")은 %를 한 번 더 인코딩해서 쓰지 않는다
	private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder withRawQuery(
			String rawQuery) {
		try {
			return get(java.net.URI.create("/api/v1/maps/walks?" + rawQuery));
		}
		catch (IllegalArgumentException invalidUri) {
			return get("/api/v1/maps/walks").with(request -> {
				request.setQueryString(rawQuery);
				return request;
			});
		}
	}

	private ResultActions list(Guardian guardian, long petId, String date) throws Exception {
		// MockMvc의 param()은 요청 파라미터만 넣고 원본 쿼리 문자열은 비워 둔다. 실제 요청처럼 URL에 쿼리를 붙인다
		return mockMvc.perform(get("/api/v1/maps/walks?petId=" + petId + "&date=" + date).cookie(guardian.cookie()));
	}

	private java.util.List<Long> walkIds(ResultActions actions) throws Exception {
		JsonNode walks = readBody(actions.andExpect(status().isOk()).andReturn()).get("walks");
		java.util.List<Long> ids = new java.util.ArrayList<>();
		walks.forEach(walk -> ids.add(walk.get("walkId").asLong()));
		return ids;
	}

	// 정해진 시각의 산책을 DB에 직접 넣는다. 끝낸 시각이 없으면 null이다
	private long insertWalk(long petId, long memberId, String startedAt, String endedAt, String lastActivityAt) {
		return jdbcTemplate.queryForObject("""
				insert into walks (pet_id, created_by, started_at, ended_at, last_activity_at, created_at, updated_at)
				values (?, ?, ?, ?, ?, now(), now()) returning id
				""", Long.class, petId, memberId, Timestamp.from(Instant.parse(startedAt)),
				endedAt == null ? null : Timestamp.from(Instant.parse(endedAt)),
				Timestamp.from(Instant.parse(lastActivityAt)));
	}

	// 시작 시각부터 30초 간격으로 count개의 좌표를 넣는다. 위도는 점점 커진다
	private void seedPoints(long walkId, String from, int count) {
		jdbcTemplate.update("""
				insert into walk_points (walk_id, latitude, longitude, recorded_at)
				select ?, 37.0 + g * 0.0001, 127.0, ?::timestamptz + (g * 30 || ' seconds')::interval
				from generate_series(0, ?) g
				""", walkId, Timestamp.from(Instant.parse(from)), count - 1);
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
		String body = "{\"email\":\"%s\",\"password\":\"%s\",\"nickname\":\"보호자\",\"verificationToken\":\"%s\"}"
			.formatted(email, PASSWORD, signUpTokens.issue(email));
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
