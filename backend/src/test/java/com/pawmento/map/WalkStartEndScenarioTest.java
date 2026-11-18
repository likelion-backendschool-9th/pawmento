package com.pawmento.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

// 시나리오는 docs/map/walk-route.md에 있다. 산책 시작·끝 API를 요청으로 확인하고, 반려동물은 pet API로 만든다.
// 서명 키는 테스트 전용 값이다. 실제 환경의 키로 쓰지 않는다
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "pawmento.auth.jwt-secret=test-only-jwt-secret-0123456789-abcdefghij")
@AutoConfigureMockMvc
class WalkStartEndScenarioTest {

	private static final String PASSWORD = "correct-password";

	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private record Guardian(long id, Cookie cookie) {
	}

	@Test
	@DisplayName("#73-S1 내 반려동물로 산책을 시작하면 201과 walkId, startedAt, IN_PROGRESS를 받고 산책이 저장된다")
	void startWalk() throws Exception {
		Guardian me = signUpAndLogin();
		long pet = createPet(me);

		JsonNode body = readBody(startWalk(me, pet).andExpect(status().isCreated()).andReturn());

		assertThat(body.get("walkId").asLong()).isPositive();
		assertThat(body.get("startedAt").asText()).isNotBlank();
		assertThat(body.get("status").asText()).isEqualTo("IN_PROGRESS");
		assertThat(jdbcTemplate.queryForMap("select pet_id, created_by, ended_at from walks where id = ?",
				body.get("walkId").asLong())).containsEntry("pet_id", pet)
			.containsEntry("created_by", me.id())
			.containsEntry("ended_at", null);
	}

	@Test
	@DisplayName("#73-S2 진행 중인 산책을 끝내면 200과 endedAt, ENDED를 받고, 다시 끝내도 같은 결과다")
	void endWalk() throws Exception {
		Guardian me = signUpAndLogin();
		long walk = startedWalk(me, createPet(me));

		JsonNode first = readBody(endWalk(me, walk).andExpect(status().isOk()).andReturn());
		JsonNode again = readBody(endWalk(me, walk).andExpect(status().isOk()).andReturn());

		assertThat(first.get("status").asText()).isEqualTo("ENDED");
		assertThat(first.get("endedAt").asText()).isNotBlank();
		assertThat(again.get("status").asText()).isEqualTo("ENDED");
		assertThat(again.get("endedAt").asText()).isEqualTo(first.get("endedAt").asText());
	}

	@Test
	@DisplayName("#73-S2 이미 중단된 산책을 끝내려 해도 200과 INTERRUPTED를 받고 endedAt은 비어 있다")
	void endInterruptedWalk() throws Exception {
		Guardian me = signUpAndLogin();
		long walk = startedWalk(me, createPet(me));
		makeInterrupted(walk);

		endWalk(me, walk).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("INTERRUPTED"))
			.andExpect(jsonPath("$.endedAt").doesNotExist());

		assertThat(jdbcTemplate.queryForObject("select ended_at from walks where id = ?", Object.class, walk)).isNull();
	}

	@Test
	@DisplayName("#73-S11 다른 회원의 반려동물로 시작하면 403, 없는 반려동물이면 404이고 산책이 만들어지지 않는다")
	void startWithUnusablePet() throws Exception {
		Guardian me = signUpAndLogin();
		Guardian other = signUpAndLogin();
		long othersPet = createPet(other);

		startWalk(me, othersPet).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
		startWalk(me, 999_999_999L).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));

		assertThat(walkCountOf(me)).isZero();
	}

	@Test
	@DisplayName("#73-S12 진행 중인 산책이 있는 반려동물로 다시 시작하면 409이고 새 산책은 만들어지지 않는다")
	void startWhileInProgress() throws Exception {
		Guardian me = signUpAndLogin();
		long pet = createPet(me);
		startedWalk(me, pet);

		startWalk(me, pet).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONFLICT"));

		assertThat(walkCountOf(me)).isEqualTo(1);
	}

	@Test
	@DisplayName("#73-S12 진행 중인 산책이 있어도 다른 반려동물로는 시작할 수 있다")
	void startWithAnotherPetWhileInProgress() throws Exception {
		Guardian me = signUpAndLogin();
		startedWalk(me, createPet(me));

		startWalk(me, createPet(me)).andExpect(status().isCreated());

		assertThat(walkCountOf(me)).isEqualTo(2);
	}

	@Test
	@DisplayName("#73-S13 마지막 활동 후 5분이 넘은 산책이 있으면 새 산책을 시작할 수 있고, 이전 산책은 중단으로 남는다")
	void startAfterInterrupted() throws Exception {
		Guardian me = signUpAndLogin();
		long pet = createPet(me);
		long old = startedWalk(me, pet);
		makeInterrupted(old);

		JsonNode body = readBody(startWalk(me, pet).andExpect(status().isCreated()).andReturn());

		assertThat(body.get("walkId").asLong()).isNotEqualTo(old);
		assertThat(walkCountOf(me)).isEqualTo(2);
		endWalk(me, old).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INTERRUPTED"));
	}

	@Test
	@DisplayName("#73-S12 같은 반려동물의 산책 시작이 동시에 여러 번 와도 하나만 만들어지고 나머지는 409다")
	void concurrentStartsCreateOnlyOneWalk() throws Exception {
		Guardian me = signUpAndLogin();
		int threads = 8;
		// 겹치는 순간이 한 번만 생기면 우연히 지나갈 수 있어서, 반려동물을 바꿔 가며 여러 번 확인한다
		for (int round = 0; round < 5; round++) {
			long pet = createPet(me);
			List<Integer> statuses = startConcurrently(me, pet, threads);

			assertThat(statuses).filteredOn(code -> code == 201).hasSize(1);
			assertThat(statuses).filteredOn(code -> code == 409).hasSize(threads - 1);
			assertThat(jdbcTemplate.queryForObject("select count(*) from walks where pet_id = ?", Integer.class, pet))
				.isEqualTo(1);
		}
	}

	@Test
	@DisplayName("#73-S9 다른 회원이 끝내려 하면 403이고 산책은 바뀌지 않으며, 없는 산책은 404다")
	void endOthersOrMissingWalk() throws Exception {
		Guardian me = signUpAndLogin();
		Guardian other = signUpAndLogin();
		long walk = startedWalk(me, createPet(me));

		endWalk(other, walk).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
		endWalk(me, 999_999_999L).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));

		assertThat(jdbcTemplate.queryForObject("select ended_at from walks where id = ?", Object.class, walk)).isNull();
	}

	@Test
	@DisplayName("#73-S9 로그인 없이 시작하거나 끝내려 하면 401과 JSON 에러를 받는다")
	void withoutLogin() throws Exception {
		mockMvc.perform(post("/api/v1/maps/walks").contentType(MediaType.APPLICATION_JSON).content("{\"petId\":1}"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
		mockMvc.perform(post("/api/v1/maps/walks/1/end"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	@DisplayName("#73-S1 petId가 없거나 숫자가 아니면 JSON 에러가 달린 400을 받는다")
	void invalidStartRequest() throws Exception {
		Guardian me = signUpAndLogin();

		mockMvc.perform(post("/api/v1/maps/walks").cookie(me.cookie()).contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION"))
			.andExpect(jsonPath("$.message").value("petId를 입력해 주세요."));
		mockMvc
			.perform(post("/api/v1/maps/walks").cookie(me.cookie())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"petId\":\"abc\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION"));
	}

	// 같은 반려동물로 산책 시작을 동시에 보내고 응답 코드를 모은다
	private List<Integer> startConcurrently(Guardian guardian, long petId, int threads) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CountDownLatch ready = new CountDownLatch(threads);
		CountDownLatch go = new CountDownLatch(1);
		List<Integer> statuses = new ArrayList<>();
		try {
			List<Future<Integer>> futures = new ArrayList<>();
			for (int i = 0; i < threads; i++) {
				futures.add(pool.submit(() -> {
					ready.countDown();
					go.await();
					return startWalk(guardian, petId).andReturn().getResponse().getStatus();
				}));
			}
			ready.await();
			go.countDown();
			for (Future<Integer> future : futures) {
				statuses.add(future.get());
			}
		}
		finally {
			pool.shutdownNow();
		}
		return statuses;
	}

	@Test
	@DisplayName("#73-S1 petId에 소수를 보내면 정수로 잘라 쓰지 않고 400이며 산책이 만들어지지 않는다")
	void decimalPetIdIsRejected() throws Exception {
		Guardian me = signUpAndLogin();
		long pet = createPet(me);

		// 10.9를 10으로 잘라 읽으면 내 10번 반려동물의 산책이 만들어질 수 있다
		for (String petId : new String[] { pet + ".9", pet + ".1", pet + ".0" }) {
			MvcResult result = mockMvc
				.perform(post("/api/v1/maps/walks").cookie(me.cookie())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"petId\":" + petId + "}"))
				.andReturn();
			assertThat(result.getResponse().getStatus()).as("petId=%s", petId).isEqualTo(400);
			JsonNode body = readBody(result);
			assertThat(body.get("code").asText()).as("petId=%s", petId).isEqualTo("VALIDATION");
			assertThat(body.get("message").asText()).as("petId=%s", petId).isEqualTo("petId는 정수로 입력해 주세요.");
		}

		assertThat(walkCountOf(me)).isZero();
		startWalk(me, pet).andExpect(status().isCreated());
	}

	@Test
	@DisplayName("#73-S1 petId가 Long 범위를 넘으면 400이고, 범위 안의 큰 정수는 반려동물 조회까지 진행해 404를 받는다")
	void petIdRangeIsChecked() throws Exception {
		Guardian me = signUpAndLogin();

		// 범위 밖: 처리되지 않은 예외(500)가 아니라 400 VALIDATION
		for (String petId : new String[] { "9223372036854775808", "-9223372036854775809", "1e30", "1e2147483647",
				"-1e2147483647", "1e-999999999" }) {
			MvcResult result = mockMvc
				.perform(post("/api/v1/maps/walks").cookie(me.cookie())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"petId\":" + petId + "}"))
				.andReturn();
			assertThat(result.getResponse().getStatus()).as("petId=%s", petId).isEqualTo(400);
			assertThat(readBody(result).get("code").asText()).as("petId=%s", petId).isEqualTo("VALIDATION");
		}

		// 범위 안의 큰 정수(19자리 포함): 검증을 통과해 반려동물 조회까지 가고, 없으니 404
		for (String petId : new String[] { "1000000000000000000", "9223372036854775807", "-9223372036854775808" }) {
			mockMvc
				.perform(post("/api/v1/maps/walks").cookie(me.cookie())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"petId\":" + petId + "}"))
				.andExpect(status().isNotFound());
		}

		assertThat(walkCountOf(me)).isZero();
	}

	private ResultActions startWalk(Guardian guardian, long petId) throws Exception {
		return mockMvc.perform(authorized(post("/api/v1/maps/walks"), guardian).contentType(MediaType.APPLICATION_JSON)
			.content("{\"petId\":%d}".formatted(petId)));
	}

	private ResultActions endWalk(Guardian guardian, long walkId) throws Exception {
		return mockMvc.perform(authorized(post("/api/v1/maps/walks/" + walkId + "/end"), guardian));
	}

	private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authorized(
			org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder, Guardian guardian) {
		return builder.cookie(guardian.cookie());
	}

	private long startedWalk(Guardian guardian, long petId) throws Exception {
		return readBody(startWalk(guardian, petId).andExpect(status().isCreated()).andReturn()).get("walkId").asLong();
	}

	// 마지막 활동이 10분 전인 것으로 바꿔 중단된 산책을 만든다
	private void makeInterrupted(long walkId) {
		jdbcTemplate.update("update walks set last_activity_at = now() - interval '10 minutes' where id = ?", walkId);
	}

	private int walkCountOf(Guardian guardian) {
		return jdbcTemplate.queryForObject("select count(*) from walks where created_by = ?", Integer.class,
				guardian.id());
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
