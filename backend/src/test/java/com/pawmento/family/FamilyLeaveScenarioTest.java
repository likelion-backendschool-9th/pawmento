package com.pawmento.family;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.pawmento.TestcontainersConfiguration;
import com.pawmento.family.application.exception.FamilyAccessDeniedException;
import com.pawmento.family.application.exception.InviteCodeNotFoundException;
import com.pawmento.family.application.port.in.IssueInviteCodeCommand;
import com.pawmento.family.application.port.in.IssueInviteCodeUseCase;
import com.pawmento.family.application.port.in.JoinFamilyCommand;
import com.pawmento.family.application.port.in.JoinFamilyUseCase;
import com.pawmento.family.application.port.in.RemoveFamilyMemberCommand;
import com.pawmento.family.application.port.in.RemoveFamilyMemberUseCase;
import com.pawmento.family.application.port.out.DeletePetFamilyMemberPort;
import com.pawmento.family.application.port.out.DeletePetInviteCodesByIssuerPort;
import com.pawmento.family.application.port.out.LoadPetFamilyMemberPort;
import com.pawmento.family.application.port.out.SavePetFamilyMemberPort;
import com.pawmento.family.domain.PetFamilyMember;
import com.pawmento.pet.application.port.in.CreatePetCommand;
import com.pawmento.pet.application.port.in.CreatePetUseCase;

// 시나리오는 docs/family/membership.md의 #133-S10·S11·S18이다. 실제 PostgreSQL로 HTTP·잠금 순서를 확인한다
// 테스트 메서드에는 트랜잭션을 두지 않는다. 각 요청이 실제처럼 따로 커밋해야 잠금 대기를 볼 수 있다
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "pawmento.auth.jwt-secret=test-only-jwt-secret-0123456789-abcdefghij")
@AutoConfigureMockMvc
class FamilyLeaveScenarioTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private CreatePetUseCase createPet;

	@Autowired
	private IssueInviteCodeUseCase issue;

	@Autowired
	private JoinFamilyUseCase join;

	@Autowired
	private RemoveFamilyMemberUseCase remove;

	// 가족 저장소 어댑터 하나가 조회·저장·삭제 포트를 함께 구현한다. 평소엔 진짜를 부르고, S18에서만 특정 지점에서 멈춘다
	@MockitoSpyBean
	private LoadPetFamilyMemberPort families;

	// 초대 코드 저장소 어댑터. 평소엔 진짜를 부르고, 롤백 테스트에서만 발급자 기준 삭제를 실패시킨다
	@MockitoSpyBean
	private DeletePetInviteCodesByIssuerPort inviteCodes;

	@Test
	@DisplayName("#133-S10 소유주가 가족을 내보내면 204이고, 내보낸 가족은 목록 403, 그 가족이 만든 코드는 404가 된다. 다른 사람의 코드는 남는다")
	void ownerRemovesFamily() throws Exception {
		long owner = uniqueMemberId();
		long family = uniqueMemberId();
		long pet = pet(owner);
		joinWithCode(owner, pet, family);
		String familyCode = issue.issue(new IssueInviteCodeCommand(family, pet)).code();
		String ownerCode = issue.issue(new IssueInviteCodeCommand(owner, pet)).code();

		removeCall(owner, family, pet).andExpect(status().isNoContent());

		list(family, pet).andExpect(status().isForbidden());
		assertThat(memberships(pet, family)).isZero();
		assertThat(codesBy(pet, family)).isZero();
		long newcomer = uniqueMemberId();
		assertThat(failureOf(() -> join.join(new JoinFamilyCommand(newcomer, familyCode))))
				.isInstanceOf(InviteCodeNotFoundException.class);
		join.join(new JoinFamilyCommand(newcomer, ownerCode));
		assertThat(memberships(pet, newcomer)).isEqualTo(1);
	}

	@Test
	@DisplayName("#133-S10 가족이 다른 가족을 내보내면 403, 가족이 아닌 대상은 404, 소유주 자신은 409, petId가 없으면 400이다")
	void removeRefusals() throws Exception {
		long owner = uniqueMemberId();
		long first = uniqueMemberId();
		long second = uniqueMemberId();
		long pet = pet(owner);
		saveFamily(pet, first);
		saveFamily(pet, second);

		removeCall(first, second, pet)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("FORBIDDEN"));
		removeCall(owner, uniqueMemberId(), pet)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"));
		removeCall(owner, owner, pet)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CONFLICT"));
		mvc.perform(delete("/api/v1/families/members/" + second).with(login(owner)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION"));
		removeCall(owner, second, Long.MAX_VALUE).andExpect(status().isNotFound());

		assertThat(memberships(pet, first)).isEqualTo(1);
		assertThat(memberships(pet, second)).isEqualTo(1);
	}

	@Test
	@DisplayName("#133-S11 가족이 나가면 204이고 목록 403, 자기가 만든 코드는 404가 된다. 소유주의 나가기는 409, 가족이 아닌 회원은 403이다")
	void familyLeaves() throws Exception {
		long owner = uniqueMemberId();
		long family = uniqueMemberId();
		long pet = pet(owner);
		joinWithCode(owner, pet, family);
		String familyCode = issue.issue(new IssueInviteCodeCommand(family, pet)).code();

		leaveCall(family, pet).andExpect(status().isNoContent());

		list(family, pet).andExpect(status().isForbidden());
		assertThat(codesBy(pet, family)).isZero();
		assertThat(failureOf(() -> join.join(new JoinFamilyCommand(uniqueMemberId(), familyCode))))
				.isInstanceOf(InviteCodeNotFoundException.class);

		leaveCall(owner, pet)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CONFLICT"));
		leaveCall(uniqueMemberId(), pet)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("FORBIDDEN"));
		leaveCall(family, pet).andExpect(status().isForbidden());
		mvc.perform(delete("/api/v1/families/members/me").param("petId", "" + pet))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("#133-S18 발급이 먼저 잠그면 내보내기는 기다렸다가, 발급이 커밋한 뒤 멤버십과 그 코드를 함께 지운다")
	void issueFirstThenRemove() throws Exception {
		long owner = uniqueMemberId();
		long family = uniqueMemberId();
		long pet = pet(owner);
		saveFamily(pet, family);
		CountDownLatch checked = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		// 발급이 반려동물을 잠그고 B의 멤버십을 확인한 바로 그 순간 멈춘다
		Mockito.doAnswer(invocation -> {
			Object result = invocation.callRealMethod();
			checked.countDown();
			awaitQuietly(release);
			return result;
		}).when(families).existsByPetIdAndMemberId(eq(pet), eq(family));

		try (var executor = Executors.newFixedThreadPool(2)) {
			Future<String> issuing = executor.submit(() -> issue.issue(new IssueInviteCodeCommand(family, pet)).code());
			assertThat(checked.await(5, TimeUnit.SECONDS)).isTrue();

			Future<?> removing = executor.submit(() -> remove.remove(new RemoveFamilyMemberCommand(owner, pet, family)));
			blockedOnPetsLock();
			assertThat(removing.isDone()).isFalse();

			release.countDown();
			assertThat(issuing.get(10, TimeUnit.SECONDS)).hasSize(8);
			removing.get(10, TimeUnit.SECONDS);
		}
		assertThat(memberships(pet, family)).isZero();
		assertThat(codesBy(pet, family)).isZero();
	}

	@Test
	@DisplayName("#133-S18 내보내기가 먼저 잠그면 발급은 기다렸다가, 내보내기가 커밋한 뒤 B가 가족이 아니어서 403이고 코드는 생기지 않는다")
	void removeFirstThenIssue() throws Exception {
		long owner = uniqueMemberId();
		long family = uniqueMemberId();
		long pet = pet(owner);
		saveFamily(pet, family);
		CountDownLatch deleted = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		// 내보내기가 반려동물을 잠그고 B의 멤버십을 지운 바로 그 순간 멈춘다
		Mockito.doAnswer(invocation -> {
			Object result = invocation.callRealMethod();
			deleted.countDown();
			awaitQuietly(release);
			return result;
		}).when((DeletePetFamilyMemberPort) families).delete(eq(pet), eq(family));

		try (var executor = Executors.newFixedThreadPool(2)) {
			Future<?> removing = executor.submit(() -> remove.remove(new RemoveFamilyMemberCommand(owner, pet, family)));
			assertThat(deleted.await(5, TimeUnit.SECONDS)).isTrue();

			Future<String> issuing = executor.submit(() -> issue.issue(new IssueInviteCodeCommand(family, pet)).code());
			blockedOnPetsLock();
			assertThat(issuing.isDone()).isFalse();

			release.countDown();
			removing.get(10, TimeUnit.SECONDS);
			assertThat(failureOf(issuing)).isInstanceOf(FamilyAccessDeniedException.class);
		}
		assertThat(memberships(pet, family)).isZero();
		assertThat(codesBy(pet, family)).isZero();
	}

	@Test
	@DisplayName("#133-S18 가입이 B의 코드를 읽고 잠금을 기다리는 사이 내보내기가 그 코드를 지우면, 이미 가족인 요청자도 409가 아니라 404다")
	void removeRevokesCodeWhileJoinWaits() throws Exception {
		long owner = uniqueMemberId();
		long family = uniqueMemberId();
		long alreadyFamily = uniqueMemberId();
		long pet = pet(owner);
		saveFamily(pet, family);
		saveFamily(pet, alreadyFamily);
		String familyCode = issue.issue(new IssueInviteCodeCommand(family, pet)).code();
		CountDownLatch deleted = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		// 내보내기가 잠금을 쥐고 B의 멤버십을 지운 순간 멈춘다. 코드는 풀린 뒤 같은 트랜잭션에서 지운다
		Mockito.doAnswer(invocation -> {
			Object result = invocation.callRealMethod();
			deleted.countDown();
			awaitQuietly(release);
			return result;
		}).when((DeletePetFamilyMemberPort) families).delete(eq(pet), eq(family));

		try (var executor = Executors.newFixedThreadPool(2)) {
			Future<?> removing = executor.submit(() -> remove.remove(new RemoveFamilyMemberCommand(owner, pet, family)));
			assertThat(deleted.await(5, TimeUnit.SECONDS)).isTrue();

			// 코드는 아직 커밋된 상태로 남아 있어, 가입은 코드를 읽은 뒤 반려동물 잠금에서 기다린다
			Future<?> joining = executor.submit(() -> join.join(new JoinFamilyCommand(alreadyFamily, familyCode)));
			blockedOnPetsLock();
			assertThat(joining.isDone()).isFalse();

			release.countDown();
			removing.get(10, TimeUnit.SECONDS);
			assertThat(failureOf(joining)).isInstanceOf(InviteCodeNotFoundException.class);
		}
		assertThat(memberships(pet, family)).isZero();
		assertThat(memberships(pet, alreadyFamily)).isEqualTo(1);
		assertThat(codesBy(pet, family)).isZero();
	}

	@Test
	@DisplayName("#133-S10 코드 삭제가 실패하면 앞서 지운 멤버십도 롤백되어, 멤버십과 코드가 모두 그대로 남는다")
	void failedCodeDeletionRollsBackMembership() {
		long owner = uniqueMemberId();
		long family = uniqueMemberId();
		long pet = pet(owner);
		saveFamily(pet, family);
		issue.issue(new IssueInviteCodeCommand(family, pet));
		Mockito.doThrow(new RuntimeException("테스트용 코드 삭제 실패"))
				.when(inviteCodes).deleteByIssuer(eq(pet), eq(family));

		assertThatThrownBy(() -> remove.remove(new RemoveFamilyMemberCommand(owner, pet, family)))
				.hasStackTraceContaining("테스트용 코드 삭제 실패");

		// 테스트 메서드에는 트랜잭션이 없어, 아래 조회는 내보내기와 다른 연결에서 커밋된 상태를 읽는다
		assertThat(memberships(pet, family)).isEqualTo(1);
		assertThat(codesBy(pet, family)).isEqualTo(1);
	}

	private long pet(long owner) {
		return createPet.create(new CreatePetCommand(owner, "보리", "DOG", null, null)).petId();
	}

	private void saveFamily(long pet, long member) {
		((SavePetFamilyMemberPort) families)
				.save(PetFamilyMember.join(pet, member, Instant.now()));
	}

	private void joinWithCode(long issuer, long pet, long joiner) {
		String code = issue.issue(new IssueInviteCodeCommand(issuer, pet)).code();
		join.join(new JoinFamilyCommand(joiner, code));
	}

	private RequestPostProcessor login(long member) {
		return jwt().jwt(j -> j.subject("" + member));
	}

	private ResultActions removeCall(long requester, long target, long pet) throws Exception {
		return mvc.perform(delete("/api/v1/families/members/" + target).param("petId", "" + pet).with(login(requester)));
	}

	private ResultActions leaveCall(long member, long pet) throws Exception {
		return mvc.perform(delete("/api/v1/families/members/me").param("petId", "" + pet).with(login(member)));
	}

	private ResultActions list(long member, long pet) throws Exception {
		return mvc.perform(get("/api/v1/families").param("petId", "" + pet).with(login(member)));
	}

	private long memberships(long pet, long member) {
		return jdbc.queryForObject(
				"select count(*) from pet_family_members where pet_id = ? and member_id = ?", Long.class, pet, member);
	}

	private long codesBy(long pet, long issuer) {
		return jdbc.queryForObject(
				"select count(*) from pet_invite_codes where pet_id = ? and issuer_member_id = ?", Long.class, pet, issuer);
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

	private static Throwable failureOf(Runnable action) {
		try {
			action.run();
			return null;
		}
		catch (RuntimeException e) {
			return e;
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
