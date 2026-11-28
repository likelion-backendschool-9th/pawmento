package com.pawmento.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.pawmento.TestcontainersConfiguration;
import com.pawmento.map.application.exception.PublicDataSyncFailedException;
import com.pawmento.map.application.port.in.SyncPublicDataPlacesCommand;
import com.pawmento.map.application.port.in.SyncPublicDataPlacesUseCase;
import com.pawmento.map.application.port.out.LoadLocalDataPort;
import com.pawmento.map.application.port.out.LoadShelterDataPort;
import com.pawmento.map.application.port.out.ShelterSnapshot;
import com.pawmento.map.application.port.out.LocalDataSnapshot;
import com.pawmento.map.application.port.out.PlaceSyncLockPort;
import com.pawmento.map.domain.LocalDataRecord;
import com.pawmento.map.domain.PlaceCategory;
import com.pawmento.map.domain.ShelterRecord;
import com.pawmento.member.application.port.in.SignUpMemberUseCase;
import com.pawmento.support.SignUpTokens;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// 시나리오는 docs/map/animal-places.md(#172-S22)에 있다. 관리자 수동 가져오기 API(POST·GET /api/v1/admin/places/sync)를 요청으로 확인한다.
// 정부 서버는 가짜 포트로 대신한다. 이 테스트가 만든 places 행은 끝나면 지운다
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = { "pawmento.auth.jwt-secret=test-only-jwt-secret-0123456789-abcdefghij",
		"pawmento.media.worker.enabled=false", "pawmento.map.public-data.sync.enabled=false" })
@AutoConfigureMockMvc
class PlaceSyncAdminScenarioTest {

	private static final String X = "199947.178659";

	private static final String Y = "453593.826987";

	private final JsonMapper json = JsonMapper.builder().build();

	@Autowired
	private MockMvc mvc;

	@Autowired
	private SignUpTokens signUpTokens;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private SignUpMemberUseCase signUp;

	@Autowired
	private PlaceSyncLockPort locks;

	@Autowired
	private SyncPublicDataPlacesUseCase syncUseCase;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@MockitoBean
	private LoadLocalDataPort loader;

	@MockitoBean
	private LoadShelterDataPort shelterLoader;

	private long admin;

	private long user;

	@BeforeEach
	void setUp() {
		reset(loader, shelterLoader);
		jdbc.update("delete from places");
		String prefix = "sync-" + UUID.randomUUID();
		admin = signUp.signUp(signUpTokens.command(prefix + "-a@example.com", "password123", "관리자")).id();
		user = signUp.signUp(signUpTokens.command(prefix + "-u@example.com", "password123", "회원")).id();
		jdbc.update("update member set role='ADMIN' where id=?", admin);
		// 영업/정상 1곳, 폐업 1곳, 좌표 없음 1곳
		when(loader.load(any())).thenReturn(new LocalDataSnapshot(3, 3,
				List.of(new LocalDataRecord("m1", "영업/정상", "대학로동물병원", "서울특별시 종로구 대학로 1", null, "0222733233", X, Y, null),
						new LocalDataRecord("m2", "폐업", "폐업병원", "주소", null, null, X, Y, null),
						new LocalDataRecord("m3", "영업/정상", "좌표없는병원", "주소", null, null, "", "", null))));
	}

	@AfterEach
	void tearDown() {
		jdbc.update("delete from places");
	}

	private ResultActions asRole(long actor, String role, String url) throws Exception {
		return mvc.perform(post(url).with(jwt().jwt(j -> j.subject("" + actor).claim("role", role))
			.authorities(new SimpleGrantedAuthority("ROLE_" + role))));
	}

	private ResultActions asAdmin(String query) throws Exception {
		return asRole(admin, "ADMIN", "/api/v1/admin/places/sync" + query);
	}

	private ResultActions statusOf(String category) throws Exception {
		return mvc.perform(get("/api/v1/admin/places/sync?category=" + category)
			.with(jwt().jwt(j -> j.subject("" + admin).claim("role", "ADMIN"))
				.authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))));
	}

	// 가져오기는 백그라운드에서 돌아서, 끝날 때까지 상태를 조회한다
	private JsonNode awaitFinished(String category) throws Exception {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
		while (System.nanoTime() < deadline) {
			JsonNode status = body(statusOf(category));
			if (!"RUNNING".equals(status.get("state").asText())) {
				return status;
			}
			Thread.sleep(50);
		}
		throw new AssertionError("가져오기가 20초 안에 끝나지 않았습니다: " + category);
	}

	private JsonNode body(ResultActions action) throws Exception {
		MvcResult result = action.andReturn();
		return json.readTree(result.getResponse().getContentAsString());
	}

	private long places() {
		return jdbc.queryForObject("select count(*) from places", Long.class);
	}

	@Test
	@DisplayName("#172-S22 로그인하지 않았으면 401이고 가져오기는 시작되지 않는다")
	void requiresLogin() throws Exception {
		int status = mvc.perform(post("/api/v1/admin/places/sync?category=HOSPITAL")).andReturn().getResponse().getStatus();

		assertThat(status).isEqualTo(401);
		verify(loader, never()).load(any());
	}

	@Test
	@DisplayName("#172-S22 일반 회원은 403이고 가져오기는 시작되지 않는다")
	void regularMemberIsForbidden() throws Exception {
		int status = asRole(user, "USER", "/api/v1/admin/places/sync?category=HOSPITAL").andReturn().getResponse().getStatus();

		assertThat(status).isEqualTo(403);
		verify(loader, never()).load(any());
		assertThat(places()).isZero();
	}

	@Test
	@DisplayName("#172-S22 토큰에는 ADMIN이라 적혀 있어도 지금 DB의 역할이 관리자가 아니면 시작도 조회도 403이다(역할이 바뀐 뒤에도 쓰던 토큰으로 못 쓴다)")
	void currentRoleIsCheckedAgainstTheDatabase() throws Exception {
		int status = asRole(user, "ADMIN", "/api/v1/admin/places/sync?category=HOSPITAL").andReturn().getResponse().getStatus();
		int statusQuery = asRoleGet(user, "ADMIN").andReturn().getResponse().getStatus();

		assertThat(status).isEqualTo(403);
		assertThat(statusQuery).isEqualTo(403);
		verify(loader, never()).load(any());
	}

	@Test
	@DisplayName("#172-S22 관리자가 category=HOSPITAL로 부르면 바로 202와 RUNNING을 받고, 끝난 뒤 조회하면 읽은·저장·제외·추가·갱신·삭제 건수가 있다")
	void adminStartsTheSync() throws Exception {
		ResultActions action = asAdmin("?category=HOSPITAL");

		assertThat(action.andReturn().getResponse().getStatus()).isEqualTo(202);
		JsonNode started = body(action);
		assertThat(started.get("category").asText()).isEqualTo("HOSPITAL");
		assertThat(started.get("state").asText()).isIn("RUNNING", "SUCCEEDED");

		JsonNode status = awaitFinished("HOSPITAL");
		assertThat(status.get("state").asText()).isEqualTo("SUCCEEDED");
		JsonNode result = status.get("result");
		assertThat(result.get("read").asInt()).isEqualTo(3);
		assertThat(result.get("saved").asInt()).isEqualTo(1);
		assertThat(result.get("excluded").asInt()).isEqualTo(2);
		assertThat(result.get("closed").asInt()).isEqualTo(1);
		assertThat(result.get("noCoordinates").asInt()).isEqualTo(1);
		assertThat(result.get("outOfRange").asInt()).isZero();
		assertThat(result.get("inserted").asInt()).isEqualTo(1);
		assertThat(result.get("updated").asInt()).isZero();
		assertThat(result.get("deleted").asInt()).isZero();
		assertThat(jdbc.queryForObject("select name from places where category='HOSPITAL' and source_id='m1'", String.class))
			.isEqualTo("대학로동물병원");
	}

	@Test
	@DisplayName("#172-S22 이 서버에서 돌린 적이 없는 종류를 조회하면 NONE이고, 일반 회원은 조회도 403이다")
	void statusBeforeAnyRunAndAccessControl() throws Exception {
		assertThat(body(statusOf("GROOMING")).get("state").asText()).isEqualTo("NONE");

		int status = asRoleGet(user, "USER").andReturn().getResponse().getStatus();
		assertThat(status).isEqualTo(403);
	}

	private ResultActions asRoleGet(long actor, String role) throws Exception {
		return mvc.perform(get("/api/v1/admin/places/sync?category=HOSPITAL")
			.with(jwt().jwt(j -> j.subject("" + actor).claim("role", role))
				.authorities(new SimpleGrantedAuthority("ROLE_" + role))));
	}

	@Test
	@DisplayName("#172-S22 다시 돌리면 바뀐 것이 없어 추가·갱신·삭제가 0이고, 이번 목록에 없는 저장된 곳은 삭제로 센다")
	void secondRunIsIdempotentAndDeletesMissing() throws Exception {
		asAdmin("?category=HOSPITAL");
		awaitFinished("HOSPITAL");
		jdbc.update("""
				insert into places (category, source_id, name, latitude, longitude, open24h, created_at, updated_at)
				values ('HOSPITAL', 'stale', '사라진 병원', 37.5, 127.0, false, now(), now())
				""");
		jdbc.update("""
				insert into places (category, source_id, name, latitude, longitude, open24h, created_at, updated_at)
				values ('GROOMING', 'other-kind', '다른 종류', 37.5, 127.0, false, now(), now())
				""");

		asAdmin("?category=HOSPITAL");
		JsonNode second = awaitFinished("HOSPITAL").get("result");

		assertThat(second.get("inserted").asInt()).isZero();
		assertThat(second.get("updated").asInt()).isZero();
		assertThat(second.get("deleted").asInt()).isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from places where source_id='stale'", Long.class)).isZero();
		assertThat(jdbc.queryForObject("select count(*) from places where source_id='other-kind'", Long.class)).isEqualTo(1);
	}

	@Test
	@DisplayName("#172-S22 category가 없거나 중복이거나 소문자이거나 모르는 종류이면 400이고, 가져오기는 시작되지 않는다")
	void invalidCategoryIsRejected() throws Exception {
		for (String query : new String[] { "", "?category=", "?category=hospital", "?category=CAFE",
				"?category=HOSPITAL&category=GROOMING", "?category=HOSPITAL,GROOMING", "?other=HOSPITAL" }) {
			ResultActions action = asAdmin(query);

			assertThat(action.andReturn().getResponse().getStatus()).as("query=%s", query).isEqualTo(400);
			assertThat(body(action).get("code").asText()).as("query=%s", query).isEqualTo("VALIDATION");
		}
		verify(loader, never()).load(any());
	}

	@Test
	@DisplayName("#172-S22 category=SHELTER로 부르면 보호센터를 가져와 SHELTER로 저장하고, 병원·미용 장소는 건드리지 않는다")
	void adminRunsTheShelterSync() throws Exception {
		jdbc.update("""
				insert into places (category, source_id, name, latitude, longitude, open24h, created_at, updated_at)
				values ('HOSPITAL', 'h1', '그대로 있어야 하는 병원', 37.5, 127.0, false, now(), now())
				""");
		when(shelterLoader.load()).thenReturn(new ShelterSnapshot(500,
				List.of(new ShelterRecord("c1", "서울 센터", "서울특별시 종로구 1", "02-733-9006", "37.57", "126.98", "00:00",
						"24:00", "토요일+일요일", "2026-01-02"),
						new ShelterRecord("c2", "좌표 없는 센터", "주소", null, "", "", null, null, null, null))));

		assertThat(asAdmin("?category=SHELTER").andReturn().getResponse().getStatus()).isEqualTo(202);
		JsonNode status = awaitFinished("SHELTER");
		assertThat(status.get("state").asText()).isEqualTo("SUCCEEDED");
		JsonNode body = status.get("result");
		assertThat(body.get("category").asText()).isEqualTo("SHELTER");
		assertThat(body.get("read").asInt()).isEqualTo(2);
		assertThat(body.get("saved").asInt()).isEqualTo(1);
		assertThat(body.get("noCoordinates").asInt()).isEqualTo(1);
		assertThat(jdbc.queryForObject("select open24h from places where category='SHELTER' and source_id='c1'",
				Boolean.class))
			.isTrue();
		assertThat(jdbc.queryForObject("select closed_days from places where source_id='c1'", String.class))
			.isEqualTo("토요일+일요일");
		assertThat(jdbc.queryForObject("select count(*) from places where category='HOSPITAL' and source_id='h1'",
				Long.class))
			.isEqualTo(1);
		verify(loader, never()).load(any());
	}

	@Test
	@DisplayName("#172-S5 받기가 실패하면 상태가 FAILED(FETCH_FAILED)이고 저장된 장소는 하나도 바뀌지 않는다")
	void failedFetchLeavesPlacesUntouched() throws Exception {
		jdbc.update("""
				insert into places (category, source_id, name, latitude, longitude, open24h, created_at, updated_at)
				values ('HOSPITAL', 'keep', '그대로 있어야 하는 병원', 37.5, 127.0, false, now(), now())
				""");
		when(loader.load(any())).thenThrow(PublicDataSyncFailedException.fetchFailed(new RuntimeException("시간 초과")));

		assertThat(asAdmin("?category=HOSPITAL").andReturn().getResponse().getStatus()).isEqualTo(202);
		JsonNode status = awaitFinished("HOSPITAL");

		assertThat(status.get("state").asText()).isEqualTo("FAILED");
		assertThat(status.get("failure").asText()).isEqualTo("FETCH_FAILED");
		assertThat(jdbc.queryForObject("select name from places where source_id='keep'", String.class))
			.isEqualTo("그대로 있어야 하는 병원");
		assertThat(places()).isEqualTo(1);
	}

	@Test
	@DisplayName("#172-S22 가져오는 중에 같은 종류를 다시 시작하면 409이고, 끝나면 다시 시작할 수 있다")
	void rejectsSecondStartWhileRunning() throws Exception {
		CountDownLatch inLoader = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		when(loader.load(any())).thenAnswer(invocation -> {
			inLoader.countDown();
			release.await(20, TimeUnit.SECONDS);
			return new LocalDataSnapshot(0, 0, List.of());
		});

		assertThat(asAdmin("?category=HOSPITAL").andReturn().getResponse().getStatus()).isEqualTo(202);
		assertThat(inLoader.await(10, TimeUnit.SECONDS)).isTrue();

		ResultActions blocked = asAdmin("?category=HOSPITAL");
		assertThat(blocked.andReturn().getResponse().getStatus()).isEqualTo(409);
		assertThat(body(blocked).get("code").asText()).isEqualTo("CONFLICT");
		assertThat(body(statusOf("HOSPITAL")).get("state").asText()).isEqualTo("RUNNING");

		release.countDown();
		assertThat(awaitFinished("HOSPITAL").get("state").asText()).isEqualTo("SUCCEEDED");
		assertThat(asAdmin("?category=HOSPITAL").andReturn().getResponse().getStatus()).isEqualTo(202);
		awaitFinished("HOSPITAL");
	}

	@Test
	@DisplayName("#172-S22 다른 곳(자동 실행 등)이 같은 종류의 잠금을 쥐고 있으면 받지 않고 FAILED(ALREADY_RUNNING)이며, 다른 종류는 막히지 않는다")
	void lockHeldElsewhereMakesTheRunFail() throws Exception {
		try (var lock = locks.tryAcquire(PlaceCategory.HOSPITAL).orElseThrow()) {
			assertThat(asAdmin("?category=HOSPITAL").andReturn().getResponse().getStatus()).isEqualTo(202);
			JsonNode blocked = awaitFinished("HOSPITAL");

			assertThat(blocked.get("state").asText()).isEqualTo("FAILED");
			assertThat(blocked.get("failure").asText()).isEqualTo("ALREADY_RUNNING");
			verify(loader, never()).load(any());

			assertThat(asAdmin("?category=GROOMING").andReturn().getResponse().getStatus()).isEqualTo(202);
			assertThat(awaitFinished("GROOMING").get("state").asText()).isEqualTo("SUCCEEDED");
		}
	}

	@Test
	@DisplayName("#172-S22 바깥 트랜잭션 안에서는 가져오기를 부를 수 없고, 받기도 잠금도 시작하지 않는다")
	void cannotRunInsideAnOuterTransaction() {
		assertThatThrownBy(() -> new TransactionTemplate(transactionManager)
			.executeWithoutResult(status -> syncUseCase.sync(new SyncPublicDataPlacesCommand("HOSPITAL"))))
			.isInstanceOf(IllegalTransactionStateException.class);

		verify(loader, never()).load(any());
		var lock = locks.tryAcquire(PlaceCategory.HOSPITAL);
		assertThat(lock).isPresent();
		lock.get().close();
	}

}
