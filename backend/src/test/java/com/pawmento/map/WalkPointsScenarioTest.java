package com.pawmento.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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

// 시나리오는 docs/map/walk-route.md에 있다. 좌표 쌓기 API를 요청으로 확인한다.
// 좌표를 읽는 API(날짜별 조회)는 다음 단계라서, 저장된 좌표는 DB를 직접 읽어 확인한다.
// 서명 키는 테스트 전용 값이다. 실제 환경의 키로 쓰지 않는다
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "pawmento.auth.jwt-secret=test-only-jwt-secret-0123456789-abcdefghij")
@AutoConfigureMockMvc
class WalkPointsScenarioTest {

	private static final String PASSWORD = "correct-password";

	// 산책 시각과 무관하게 고정한 좌표 기록 시각의 기준. 초 단위로 더해 서로 다른 시각을 만든다
	private static final Instant BASE = Instant.parse("2026-11-17T01:00:00Z");

	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private record Guardian(long id, Cookie cookie) {
	}

	@Test
	@DisplayName("#73-S4 진행 중인 산책에 좌표 4개를 보내면 204이고 본문은 없으며, 좌표가 기록 시각 순서로 쌓이고 마지막 활동 시각이 갱신된다")
	void addPoints() throws Exception {
		Guardian me = signUpAndLogin();
		long walk = startedWalk(me, createPet(me));

		// 일부러 시각 순서를 섞어 보낸다
		MvcResult result = addPoints(me, walk, point(37.5, 127.0, 3), point(37.1, 127.1, 1), point(37.3, 127.3, 2),
				point(37.4, 127.4, 4))
			.andExpect(status().isNoContent())
			.andReturn();

		assertThat(result.getResponse().getContentAsString()).isEmpty();
		List<Double> latitudes = jdbcTemplate.queryForList(
				"select latitude from walk_points where walk_id = ? order by recorded_at", Double.class, walk);
		assertThat(latitudes).containsExactly(37.1, 37.3, 37.5, 37.4);
		assertThat(jdbcTemplate.queryForObject(
				"select last_activity_at > started_at and ended_at is null from walks where id = ?", Boolean.class,
				walk)).isTrue();
	}

	@Test
	@DisplayName("#73-S5 좌표 하나가 위도 91이면 400과 이유를 받고, 같은 요청의 나머지 좌표도 쌓이지 않으며 마지막 활동 시각도 그대로다")
	void invalidCoordinateRejectsWholeBatch() throws Exception {
		Guardian me = signUpAndLogin();
		long walk = startedWalk(me, createPet(me));
		Instant activityBefore = lastActivityOf(walk);

		addPoints(me, walk, point(37.5, 127.0, 1), point(91.0, 127.0, 2), point(37.6, 127.1, 3))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION"))
			.andExpect(jsonPath("$.message").value("위도는 -90 이상 90 이하로 입력해 주세요."));
		addPoints(me, walk, point(37.5, 181.0, 1)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("경도는 -180 이상 180 이하로 입력해 주세요."));

		assertThat(pointCount(walk)).isZero();
		assertThat(lastActivityOf(walk)).isEqualTo(activityBefore);
	}

	@Test
	@DisplayName("#73-S5 points가 없거나 비었거나 비어 있는 항목이 있거나 필드·시각 형식이 틀리면 400이고 아무것도 쌓이지 않는다")
	void malformedBodiesAreRejected() throws Exception {
		Guardian me = signUpAndLogin();
		long walk = startedWalk(me, createPet(me));

		for (String body : new String[] { "{}", "{\"points\":null}", "{\"points\":[]}", "{\"points\":[null]}",
				"{\"points\":[{\"longitude\":127.0,\"recordedAt\":\"2026-11-17T01:00:01Z\"}]}",
				"{\"points\":[{\"latitude\":37.5,\"recordedAt\":\"2026-11-17T01:00:01Z\"}]}",
				"{\"points\":[{\"latitude\":37.5,\"longitude\":127.0}]}",
				"{\"points\":[{\"latitude\":37.5,\"longitude\":127.0,\"recordedAt\":\"어제\"}]}",
				"{\"points\":[{\"latitude\":\"abc\",\"longitude\":127.0,\"recordedAt\":\"2026-11-17T01:00:01Z\"}]}",
				// 숫자가 아주 커서 무한대로 읽히는 값도 처리되지 않은 오류가 아니라 400이다
				"{\"points\":[{\"latitude\":1e400,\"longitude\":127.0,\"recordedAt\":\"2026-11-17T01:00:01Z\"}]}",
				"{\"points\":[{\"latitude\":37.5,\"longitude\":-1e999999,\"recordedAt\":\"2026-11-17T01:00:01Z\"}]}",
				"{\"points\":\"x\"}", "not json", "" }) {
			MvcResult result = mockMvc
				.perform(post("/api/v1/maps/walks/" + walk + "/points").cookie(me.cookie())
					.contentType(MediaType.APPLICATION_JSON)
					.content(body))
				.andReturn();
			assertThat(result.getResponse().getStatus()).as("body=%s", body).isEqualTo(400);
			assertThat(readBody(result).get("code").asText()).as("body=%s", body).isEqualTo("VALIDATION");
		}

		// 비었을 때는 도메인이 이유를 알려 준다
		addRaw(me, walk, "{\"points\":[]}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("좌표를 한 개 이상 보내 주세요."));
		assertThat(pointCount(walk)).isZero();
	}

	@Test
	@DisplayName("#73-S14 좌표 101개를 한 번에 보내면 400이고 쌓이지 않으며, 100개는 쌓인다")
	void batchLimitPerRequest() throws Exception {
		Guardian me = signUpAndLogin();
		long walk = startedWalk(me, createPet(me));

		addPoints(me, walk, points(101, 0)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION"))
			.andExpect(jsonPath("$.message").value("좌표는 한 번에 100개까지 보낼 수 있습니다."));
		assertThat(pointCount(walk)).isZero();

		addPoints(me, walk, points(100, 0)).andExpect(status().isNoContent());
		assertThat(pointCount(walk)).isEqualTo(100);
	}

	@Test
	@DisplayName("#73-S14 4,950개가 쌓인 산책에 새 좌표 100개를 보내면 409이고 하나도 쌓이지 않으며, 50개는 정확히 5,000개까지 쌓인다")
	void walkLimit() throws Exception {
		Guardian me = signUpAndLogin();
		long walk = startedWalk(me, createPet(me));
		seedPoints(walk, 4950);
		Instant activityBefore = lastActivityOf(walk);

		// 새로 쌓일 좌표가 한도를 넘으면 요청 전체를 되돌린다. 이미 넣은 좌표가 남지 않아야 한다
		addPoints(me, walk, points(100, 10_000)).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CONFLICT"))
			.andExpect(jsonPath("$.message").value("한 산책에는 좌표를 5000개까지 쌓을 수 있습니다."));
		assertThat(pointCount(walk)).isEqualTo(4950);
		assertThat(lastActivityOf(walk)).isEqualTo(activityBefore);

		addPoints(me, walk, points(50, 10_000)).andExpect(status().isNoContent());
		assertThat(pointCount(walk)).isEqualTo(5000);
		addPoints(me, walk, points(1, 20_000)).andExpect(status().isConflict());
		assertThat(pointCount(walk)).isEqualTo(5000);
	}

	@Test
	@DisplayName("#73-S14 이미 쌓인 좌표를 다시 보내는 것은 개수를 늘리지 않아서, 한도에 닿은 산책에도 재전송은 204다")
	void resendDoesNotCountTowardsLimit() throws Exception {
		Guardian me = signUpAndLogin();
		long walk = startedWalk(me, createPet(me));
		// 4,990개가 쌓였고 그중 마지막 20개를 다시 보내면서 새 좌표를 10개 더한다
		seedPoints(walk, 4990);
		List<String> batch = new ArrayList<>(points(20, 4970));
		batch.addAll(points(10, 30_000));

		addPoints(me, walk, batch.toArray(String[]::new)).andExpect(status().isNoContent());

		assertThat(pointCount(walk)).isEqualTo(5000);
		// 한도에 닿은 뒤에도 같은 묶음을 다시 보내면(재전송) 새로 쌓이는 것이 없어서 204다
		addPoints(me, walk, batch.toArray(String[]::new)).andExpect(status().isNoContent());
		assertThat(pointCount(walk)).isEqualTo(5000);
	}

	@Test
	@DisplayName("#73-S6 같은 묶음을 다시 보내면 204이고 같은 좌표가 두 번 쌓이지 않으며, 좌표가 달라도 먼저 쌓인 값이 남는다")
	void resendKeepsFirstValue() throws Exception {
		Guardian me = signUpAndLogin();
		long walk = startedWalk(me, createPet(me));
		addPoints(me, walk, point(37.1, 127.1, 1), point(37.2, 127.2, 2)).andExpect(status().isNoContent());

		// 네트워크가 끊겨 같은 묶음을 다시 보낸다. 두 번째 좌표는 값이 다르지만 같은 시각이다
		addPoints(me, walk, point(37.1, 127.1, 1), point(38.9, 128.9, 2), point(37.3, 127.3, 3))
			.andExpect(status().isNoContent());

		List<Double> latitudes = jdbcTemplate.queryForList(
				"select latitude from walk_points where walk_id = ? order by recorded_at", Double.class, walk);
		assertThat(latitudes).containsExactly(37.1, 37.2, 37.3);
	}

	@Test
	@DisplayName("#73-S6 한 묶음 안에서 같은 시각이 겹치면 한 번만 쌓이고, 마이크로초보다 정밀한 시각도 같은 값으로 비교한다")
	void duplicatesInsideBatchAndNanos() throws Exception {
		Guardian me = signUpAndLogin();
		long walk = startedWalk(me, createPet(me));

		addPoints(me, walk, point(37.1, 127.1, 1), point(37.9, 127.9, 1)).andExpect(status().isNoContent());
		assertThat(pointCount(walk)).isEqualTo(1);

		String nanos = "{\"latitude\":37.5,\"longitude\":127.5,\"recordedAt\":\"2026-11-17T01:10:00.123456789Z\"}";
		addRaw(me, walk, "{\"points\":[" + nanos + "]}").andExpect(status().isNoContent());
		addRaw(me, walk, "{\"points\":[" + nanos + "]}").andExpect(status().isNoContent());
		assertThat(pointCount(walk)).isEqualTo(2);
	}

	@Test
	@DisplayName("#73-S3 끝났거나 중단된 산책에는 좌표를 쌓을 수 없고(409) 마지막 활동 시각도 바꾸지 않으며, 없는 산책은 404다")
	void notAcceptingWalks() throws Exception {
		Guardian me = signUpAndLogin();
		long pet = createPet(me);
		long ended = startedWalk(me, pet);
		mockMvc.perform(post("/api/v1/maps/walks/" + ended + "/end").cookie(me.cookie())).andExpect(status().isOk());
		long interrupted = startedWalk(me, createPet(me));
		makeInterrupted(interrupted);
		Instant interruptedActivity = lastActivityOf(interrupted);

		addPoints(me, ended, point(37.5, 127.0, 1)).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CONFLICT"));
		addPoints(me, interrupted, point(37.5, 127.0, 1)).andExpect(status().isConflict());
		addPoints(me, 999_999_999L, point(37.5, 127.0, 1)).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"));

		assertThat(pointCount(ended)).isZero();
		assertThat(pointCount(interrupted)).isZero();
		// 중단된 산책이 좌표를 받아 다시 진행 중이 되지 않는다
		assertThat(lastActivityOf(interrupted)).isEqualTo(interruptedActivity);
	}

	@Test
	@DisplayName("#73-S9 로그인 없이는 401이고, 다른 회원의 산책에는 좌표를 쌓을 수 없다(403). 묶음이 크거나 끝난 산책이어도 소유자 검사가 먼저다")
	void onlyOwnerAddsPoints() throws Exception {
		Guardian owner = signUpAndLogin();
		Guardian other = signUpAndLogin();
		long walk = startedWalk(owner, createPet(owner));

		mockMvc
			.perform(post("/api/v1/maps/walks/" + walk + "/points").contentType(MediaType.APPLICATION_JSON)
				.content("{\"points\":[{\"latitude\":37.5,\"longitude\":127.0,\"recordedAt\":\"2026-11-17T01:00:01Z\"}]}"))
			.andExpect(status().isUnauthorized());
		addPoints(other, walk, point(37.5, 127.0, 1)).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));
		addPoints(other, walk, points(101, 0)).andExpect(status().isForbidden());
		mockMvc.perform(post("/api/v1/maps/walks/" + walk + "/end").cookie(owner.cookie())).andExpect(status().isOk());
		addPoints(other, walk, point(37.5, 127.0, 1)).andExpect(status().isForbidden());

		assertThat(pointCount(walk)).isZero();
	}

	@Test
	@DisplayName("#73-S14 같은 산책에 새 좌표 100개씩을 동시에 보내도 5,000개를 넘지 않는다. 한도에 맞는 한 요청만 쌓인다")
	void concurrentBatchesNeverExceedLimit() throws Exception {
		Guardian me = signUpAndLogin();
		long walk = startedWalk(me, createPet(me));
		seedPoints(walk, 4900);
		int threads = 8;
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CountDownLatch ready = new CountDownLatch(threads);
		CountDownLatch go = new CountDownLatch(1);
		List<Future<Integer>> futures = new ArrayList<>();
		try {
			for (int t = 0; t < threads; t++) {
				int offset = 100_000 + t * 1_000;
				futures.add(pool.submit(() -> {
					ready.countDown();
					go.await();
					return addPoints(me, walk, points(100, offset)).andReturn().getResponse().getStatus();
				}));
			}
			ready.await();
			go.countDown();
			List<Integer> statuses = new ArrayList<>();
			for (Future<Integer> future : futures) {
				statuses.add(future.get());
			}
			assertThat(statuses).containsOnly(204, 409);
			assertThat(statuses.stream().filter(s -> s == 204).count()).isEqualTo(1);
		}
		finally {
			pool.shutdownNow();
		}

		assertThat(pointCount(walk)).isEqualTo(5000);
	}

	// ---- 도우미

	private static String point(double latitude, double longitude, int second) {
		return "{\"latitude\":%s,\"longitude\":%s,\"recordedAt\":\"%s\"}".formatted(latitude, longitude,
				BASE.plusSeconds(second));
	}

	// 서로 다른 시각의 좌표 count개. startSecond부터 1초씩 늘어난다
	private static List<String> points(int count, int startSecond) {
		List<String> points = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			points.add(point(37.0 + (i % 100) * 0.001, 127.0, startSecond + i));
		}
		return points;
	}

	private ResultActions addPoints(Guardian guardian, long walkId, List<String> points) throws Exception {
		return addPoints(guardian, walkId, points.toArray(String[]::new));
	}

	private ResultActions addPoints(Guardian guardian, long walkId, String... points) throws Exception {
		return addRaw(guardian, walkId, "{\"points\":[" + String.join(",", points) + "]}");
	}

	private ResultActions addRaw(Guardian guardian, long walkId, String body) throws Exception {
		return mockMvc.perform(post("/api/v1/maps/walks/" + walkId + "/points").cookie(guardian.cookie())
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	// 좌표를 요청으로 넣으면 한도(100개)에 걸려 오래 걸리므로, 많이 쌓인 산책은 DB에 직접 넣어 만든다
	private void seedPoints(long walkId, int count) {
		jdbcTemplate.update("""
				insert into walk_points (walk_id, latitude, longitude, recorded_at)
				select ?, 37.0, 127.0, ?::timestamptz + (g || ' seconds')::interval from generate_series(0, ?) g
				""", walkId, java.sql.Timestamp.from(BASE), count - 1);
	}

	private int pointCount(long walkId) {
		return jdbcTemplate.queryForObject("select count(*) from walk_points where walk_id = ?", Integer.class, walkId);
	}

	private Instant lastActivityOf(long walkId) {
		return jdbcTemplate.queryForObject("select last_activity_at from walks where id = ?", java.sql.Timestamp.class,
				walkId)
			.toInstant();
	}

	// 마지막 활동이 10분 전인 것으로 바꿔 중단된 산책을 만든다
	private void makeInterrupted(long walkId) {
		jdbcTemplate.update("update walks set last_activity_at = now() - interval '10 minutes' where id = ?", walkId);
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
