package com.pawmento.family;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import com.jayway.jsonpath.JsonPath;
import com.pawmento.TestcontainersConfiguration;
import com.pawmento.family.application.exception.AlreadyFamilyException;
import com.pawmento.family.application.exception.InviteCodeNotFoundException;
import com.pawmento.family.application.port.in.CheckPetAccessQuery;
import com.pawmento.family.application.port.in.CheckPetAccessUseCase;
import com.pawmento.family.application.port.in.IssueInviteCodeCommand;
import com.pawmento.family.application.port.in.IssueInviteCodeUseCase;
import com.pawmento.family.application.port.in.JoinFamilyCommand;
import com.pawmento.family.application.port.in.JoinFamilyUseCase;
import com.pawmento.family.application.port.out.SavePetFamilyMemberPort;
import com.pawmento.family.domain.PetFamilyMember;
import com.pawmento.pet.application.port.in.CreatePetCommand;
import com.pawmento.pet.application.port.in.CreatePetUseCase;
import com.pawmento.pet.application.port.in.LockPetOwnerQuery;
import com.pawmento.pet.application.port.in.LockPetOwnerUseCase;

// 시나리오는 docs/family/membership.md의 #133-S2~S5, S19다. 실제 PostgreSQL로 HTTP·잠금·동시 요청을 확인한다
// 테스트 메서드에는 트랜잭션을 두지 않는다. 각 요청이 실제처럼 따로 커밋해야 동시성을 볼 수 있다
@Import({ TestcontainersConfiguration.class, FamilyInviteScenarioTest.Time.class })
@SpringBootTest(properties = "pawmento.auth.jwt-secret=test-only-jwt-secret-0123456789-abcdefghij")
@AutoConfigureMockMvc
class FamilyInviteScenarioTest {

	// 2026-11-24 12:00 Asia/Seoul
	private static final Instant NOW = Instant.parse("2026-11-24T03:00:00Z");

	@TestConfiguration(proxyBeanMethods = false)
	static class Time {
		@Bean
		@Primary
		MovableClock familyClock() {
			return new MovableClock(NOW);
		}
	}

	// 테스트 중에 시각을 옮길 수 있는 시계. 여러 스레드가 같이 읽는다
	static final class MovableClock extends Clock {
		private final AtomicReference<Instant> now;

		MovableClock(Instant now) {
			this.now = new AtomicReference<>(now);
		}

		void set(Instant instant) {
			now.set(instant);
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now.get();
		}
	}

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private TransactionTemplate transaction;

	@Autowired
	private MovableClock clock;

	@Autowired
	private CreatePetUseCase createPet;

	@Autowired
	private LockPetOwnerUseCase lockPetOwners;

	@Autowired
	private CheckPetAccessUseCase access;

	@Autowired
	private IssueInviteCodeUseCase issue;

	@Autowired
	private JoinFamilyUseCase join;

	// 평소엔 진짜 저장소를 그대로 부르고, S19의 저장 실패 테스트에서만 한 번 실패시킨다
	@MockitoSpyBean
	private SavePetFamilyMemberPort saveFamily;

	@BeforeEach
	void resetClock() {
		clock.set(NOW);
	}

	@Test
	@DisplayName("#133-S2 소유주와 가족은 코드를 만들고(201), 관계없는 회원은 403, 없는 반려동물은 404다. DB에는 해시만 남는다")
	void issueInviteCode() throws Exception {
		long owner = uniqueMemberId();
		long family = uniqueMemberId();
		long pet = pet(owner);
		saveFamily.save(PetFamilyMember.join(pet, family, NOW));

		String body = issueCall(owner, pet)
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		String code = JsonPath.read(body, "$.code");
		String expiresAt = JsonPath.read(body, "$.expiresAt");
		assertThat(code).matches("[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{8}");
		assertThat(Instant.parse(expiresAt)).isEqualTo(NOW.plus(Duration.ofHours(24)));

		issueCall(family, pet).andExpect(status().isCreated());

		issueCall(uniqueMemberId(), pet)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("FORBIDDEN"));
		issueCall(owner, Long.MAX_VALUE)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"));

		// 두 번 발급했으니 두 줄이고, 원래 코드는 어디에도 저장되지 않는다
		List<String> hashes = jdbc.queryForList(
				"select code_hash from pet_invite_codes where pet_id = ?", String.class, pet);
		assertThat(hashes).hasSize(2).allSatisfy(hash -> assertThat(hash).hasSize(64).isNotEqualTo(code));
	}

	@Test
	@DisplayName("#133-S2 로그인하지 않으면 401이고, petId가 없으면 400이다")
	void issueNeedsLoginAndPetId() throws Exception {
		mvc.perform(post("/api/v1/families/invite-codes")
				.contentType(MediaType.APPLICATION_JSON).content("{\"petId\":1}"))
				.andExpect(status().isUnauthorized());
		mvc.perform(post("/api/v1/families/invite-codes")
				.contentType(MediaType.APPLICATION_JSON).content("{}")
				.with(jwt().jwt(j -> j.subject("" + uniqueMemberId()))))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("#133-S3 유효한 코드로 가족이 되고(200, petId·반려동물 이름·태그), 같은 코드를 다시 쓰면 404다")
	void joinWithCode() throws Exception {
		long owner = uniqueMemberId();
		long joiner = uniqueMemberId();
		long pet = pet(owner);
		String code = issue(owner, pet);

		// 사용자가 소문자와 공백을 섞어 입력해도 된다
		joinCall(joiner, "  " + code.toLowerCase() + " ")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.petId").value(equalTo(pet), Long.class))
				.andExpect(jsonPath("$.name").value("보리"))
				.andExpect(jsonPath("$.tag").value(
						jdbc.queryForObject("select tag from pets where id = ?", String.class, pet)));
		assertThat(access.check(new CheckPetAccessQuery(pet, joiner)).role()).isEqualTo("FAMILY");
		assertThat(codeRows(pet)).isZero();

		joinCall(uniqueMemberId(), code)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"));
	}

	@Test
	@DisplayName("#133-S3 코드가 비었으면 400이다")
	void joinNeedsCode() throws Exception {
		joinCall(uniqueMemberId(), " ").andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("#133-S4 없는 코드와 만료된 코드는 둘 다 404이고 가족이 생기지 않는다")
	void unusableCodes() throws Exception {
		long owner = uniqueMemberId();
		long joiner = uniqueMemberId();
		long pet = pet(owner);
		String code = issue(owner, pet);

		joinCall(joiner, "ZZZZZZZZ")
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"));

		// 만료 시각 정각부터는 쓸 수 없다
		clock.set(NOW.plus(Duration.ofHours(24)));
		joinCall(joiner, code)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"));

		assertThat(memberships(pet, joiner)).isZero();
	}

	@Test
	@DisplayName("#133-S4 이미 소유주나 가족인 회원이 코드를 쓰면 409이고, 코드는 남아서 다른 회원이 쓸 수 있다")
	void alreadyInDoesNotConsumeCode() throws Exception {
		long owner = uniqueMemberId();
		long family = uniqueMemberId();
		long pet = pet(owner);
		saveFamily.save(PetFamilyMember.join(pet, family, NOW));
		String code = issue(owner, pet);

		joinCall(owner, code)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CONFLICT"));
		joinCall(family, code)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CONFLICT"));
		assertThat(codeRows(pet)).isEqualTo(1);

		long newcomer = uniqueMemberId();
		joinCall(newcomer, code).andExpect(status().isOk());
		assertThat(memberships(pet, newcomer)).isEqualTo(1);
	}

	@Test
	@DisplayName("#133-S4 가입이 반려동물 잠금을 기다리는 사이 코드가 만료되면, 잠금 뒤 시각으로 판단해 404이고 코드는 만료된 채 남는다")
	void codeExpiresWhileWaitingForLock() throws Exception {
		long owner = uniqueMemberId();
		long joiner = uniqueMemberId();
		long pet = pet(owner);
		String code = issue(owner, pet);
		CountDownLatch locked = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);

		try (var executor = Executors.newFixedThreadPool(2)) {
			// 다른 가족 변경 요청이 반려동물 행을 잡고 있는 상황
			Future<?> holder = executor.submit(() -> transaction.executeWithoutResult(status -> {
				lockPetOwners.lock(new LockPetOwnerQuery(pet));
				locked.countDown();
				awaitQuietly(release);
			}));
			assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();

			// 코드가 아직 유효할 때 가입을 시작한다. 코드는 읽었고, 반려동물 잠금에서 기다린다
			Future<?> joining = executor.submit(() -> join.join(new JoinFamilyCommand(joiner, code)));
			blockedOnPetsLock();

			// 기다리는 사이 만료 시각이 지나고, 그다음에 잠금이 풀린다
			clock.set(NOW.plus(Duration.ofHours(24)).plusSeconds(1));
			release.countDown();
			holder.get(5, TimeUnit.SECONDS);

			assertThat(failureOf(joining)).isInstanceOf(InviteCodeNotFoundException.class);
		}
		assertThat(memberships(pet, joiner)).isZero();
		assertThat(codeRows(pet)).isEqualTo(1);
	}

	@Test
	@DisplayName("#133-S5 두 회원이 같은 코드로 동시에 가입하면 한 명만 가족이 되고 다른 한 명은 404다")
	void sameCodeAtOnce() throws Exception {
		long owner = uniqueMemberId();
		long pet = pet(owner);
		String code = issue(owner, pet);
		long d = uniqueMemberId();
		long e = uniqueMemberId();

		List<Throwable> failures = runTogether(
				() -> join.join(new JoinFamilyCommand(d, code)),
				() -> join.join(new JoinFamilyCommand(e, code)));

		assertThat(failures).hasSize(1).first().isInstanceOf(InviteCodeNotFoundException.class);
		assertThat(memberships(pet, d) + memberships(pet, e)).isEqualTo(1);
		assertThat(codeRows(pet)).isZero();
	}

	@Test
	@DisplayName("#133-S19 같은 회원이 다른 코드 두 개로 동시에 가입하면 하나는 성공, 하나는 409이고, 409 쪽 코드는 남아 다른 회원이 쓸 수 있다")
	void sameMemberTwoCodesAtOnce() throws Exception {
		long owner = uniqueMemberId();
		long pet = pet(owner);
		String k1 = issue(owner, pet);
		String k2 = issue(owner, pet);
		long d = uniqueMemberId();

		List<Throwable> failures = runTogether(
				() -> join.join(new JoinFamilyCommand(d, k1)),
				() -> join.join(new JoinFamilyCommand(d, k2)));

		assertThat(failures).hasSize(1).first().isInstanceOf(AlreadyFamilyException.class);
		assertThat(memberships(pet, d)).isEqualTo(1);
		assertThat(codeRows(pet)).isEqualTo(1);

		// 어느 쪽이 남았는지는 순서에 달려 있으니, 둘 다 시도해 남은 코드로 다른 회원이 가족이 되는지 본다
		long other = uniqueMemberId();
		int joined = 0;
		for (String code : List.of(k1, k2)) {
			try {
				join.join(new JoinFamilyCommand(other, code));
				joined++;
			}
			catch (InviteCodeNotFoundException alreadyUsed) {
				// 먼저 쓰인 코드
			}
		}
		assertThat(joined).isEqualTo(1);
		assertThat(memberships(pet, other)).isEqualTo(1);
		assertThat(codeRows(pet)).isZero();
	}

	@Test
	@DisplayName("#133-S19 멤버십 저장이 실패하면 가입은 오류이고, 소비한 코드는 롤백되어 다시 쓸 수 있다")
	void failedSaveRollsBackConsumedCode() {
		long owner = uniqueMemberId();
		long joiner = uniqueMemberId();
		long pet = pet(owner);
		String code = issue(owner, pet);
		// 첫 저장만 실패하고, 그다음부터는 진짜 저장소를 부른다
		// @Repository의 예외 번역이 감쌀 수 있으니, 종류가 아니라 우리가 던진 실패인지만 본다
		Mockito.doThrow(new RuntimeException("테스트용 저장 실패"))
				.doCallRealMethod()
				.when(saveFamily).save(any());

		assertThatThrownBy(() -> join.join(new JoinFamilyCommand(joiner, code)))
				.hasStackTraceContaining("테스트용 저장 실패");
		assertThat(codeRows(pet)).isEqualTo(1);
		assertThat(memberships(pet, joiner)).isZero();

		join.join(new JoinFamilyCommand(joiner, code));
		assertThat(memberships(pet, joiner)).isEqualTo(1);
		assertThat(codeRows(pet)).isZero();
	}

	@Test
	@DisplayName("#133 새 코드를 발급하면 같은 반려동물의 만료된 코드만 지우고, 아직 유효한 코드와 다른 반려동물의 코드는 남긴다")
	void issueDeletesExpiredCodesOfSamePet() {
		long owner = uniqueMemberId();
		long pet = pet(owner);
		long otherOwner = uniqueMemberId();
		long otherPet = pet(otherOwner);
		issue(owner, pet);                    // NOW 발급 → NOW+24h 만료
		issue(otherOwner, otherPet);          // 다른 반려동물, 같이 만료됨
		clock.set(NOW.plus(Duration.ofHours(1)));
		String stillValid = issue(owner, pet); // NOW+25h 만료

		// 첫 코드의 만료 시각 정각. 가입 기준(expires_at > now)으로는 이미 쓸 수 없는 코드다
		clock.set(NOW.plus(Duration.ofHours(24)));
		issue(owner, pet);

		assertThat(codeRows(pet)).isEqualTo(2);
		assertThat(codeRows(otherPet)).isEqualTo(1);
		long joiner = uniqueMemberId();
		join.join(new JoinFamilyCommand(joiner, stillValid));
		assertThat(memberships(pet, joiner)).isEqualTo(1);
	}

	private long pet(long owner) {
		return createPet.create(new CreatePetCommand(owner, "보리", "DOG", null, null)).petId();
	}

	private String issue(long member, long pet) {
		return issue.issue(new IssueInviteCodeCommand(member, pet)).code();
	}

	private ResultActions issueCall(long member, long pet) throws Exception {
		return mvc.perform(post("/api/v1/families/invite-codes")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"petId\":" + pet + "}")
				.with(jwt().jwt(j -> j.subject("" + member))));
	}

	private ResultActions joinCall(long member, String code) throws Exception {
		return mvc.perform(post("/api/v1/families/members")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"code\":\"" + code + "\"}")
				.with(jwt().jwt(j -> j.subject("" + member))));
	}

	private long codeRows(long pet) {
		return jdbc.queryForObject("select count(*) from pet_invite_codes where pet_id = ?", Long.class, pet);
	}

	private long memberships(long pet, long member) {
		return jdbc.queryForObject(
				"select count(*) from pet_family_members where pet_id = ? and member_id = ?", Long.class, pet, member);
	}

	// 두 작업을 같은 순간에 출발시키고, 실패한 쪽의 예외만 모아 돌려준다
	private List<Throwable> runTogether(Runnable first, Runnable second) throws Exception {
		CountDownLatch start = new CountDownLatch(1);
		List<Throwable> failures = new ArrayList<>();
		try (var executor = Executors.newFixedThreadPool(2)) {
			List<Future<?>> futures = new ArrayList<>();
			for (Runnable task : List.of(first, second)) {
				futures.add(executor.submit(() -> {
					awaitQuietly(start);
					task.run();
				}));
			}
			start.countDown();
			for (Future<?> future : futures) {
				Throwable failure = failureOf(future);
				if (failure != null) {
					failures.add(failure);
				}
			}
		}
		return failures;
	}

	// 성공했으면 null, 실패했으면 그 예외
	private static Throwable failureOf(Future<?> future) throws Exception {
		try {
			future.get(10, TimeUnit.SECONDS);
			return null;
		}
		catch (ExecutionException e) {
			return e.getCause();
		}
	}

	// PostgreSQL에서 다른 연결이 pets 행 잠금을 기다리고 있는지 본다 (FamilyAccessScenarioTest와 같은 방식)
	private void blockedOnPetsLock() {
		await().atMost(Duration.ofSeconds(8)).until(() -> jdbc.queryForObject(
				"select count(*) from pg_stat_activity where pid <> pg_backend_pid()"
						+ " and wait_event_type = 'Lock' and lower(query) like '%from pets%for %'",
				Long.class) > 0);
	}

	private static void awaitQuietly(CountDownLatch latch) {
		try {
			assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		}
	}

	// 회원 테이블과 외래 키가 없어, 겹치지 않는 아무 회원 id면 된다
	private long uniqueMemberId() {
		return ThreadLocalRandom.current().nextLong(1_000_000_000L, Long.MAX_VALUE / 2);
	}
}
