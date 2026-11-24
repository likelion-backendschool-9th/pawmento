package com.pawmento.family;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import com.pawmento.TestcontainersConfiguration;
import com.pawmento.family.application.port.in.CheckPetAccessQuery;
import com.pawmento.family.application.port.in.CheckPetAccessUseCase;
import com.pawmento.family.application.port.out.SavePetFamilyMemberPort;
import com.pawmento.family.domain.PetFamilyMember;
import com.pawmento.pet.application.port.in.CreatePetCommand;
import com.pawmento.pet.application.port.in.CreatePetUseCase;
import com.pawmento.pet.application.port.in.GetPetOwnerQuery;
import com.pawmento.pet.application.port.in.GetPetOwnerResult;
import com.pawmento.pet.application.port.in.GetPetOwnerUseCase;
import com.pawmento.pet.application.port.in.LockPetOwnerQuery;
import com.pawmento.pet.application.port.in.LockPetOwnerUseCase;

// 시나리오는 docs/family/membership.md에 있다. pet과 family의 실제 저장소로 역할 판정을 확인한다
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "pawmento.auth.jwt-secret=test-only-jwt-secret-0123456789-abcdefghij")
class FamilyAccessScenarioTest {

	@Autowired
	private CreatePetUseCase createPet;

	@Autowired
	private SavePetFamilyMemberPort saveFamily;

	@Autowired
	private CheckPetAccessUseCase access;

	@Autowired
	private GetPetOwnerUseCase petOwners;

	@Autowired
	private LockPetOwnerUseCase lockPetOwners;

	@Autowired
	private TransactionTemplate transaction;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	@DisplayName("#133-S1 반려동물의 소유주는 OWNER, 가족은 FAMILY, 관계없는 회원과 없는 반려동물은 NONE이다")
	void decidesRoleFromPetOwnerAndFamilyTable() {
		long owner = uniqueMemberId();
		long family = uniqueMemberId();
		long stranger = uniqueMemberId();
		long pet = createPet.create(new CreatePetCommand(owner, "보리", "DOG", null, null)).petId();
		saveFamily.save(PetFamilyMember.join(pet, family, Instant.now()));

		assertThat(access.check(new CheckPetAccessQuery(pet, owner)).role()).isEqualTo("OWNER");
		assertThat(access.check(new CheckPetAccessQuery(pet, family)).role()).isEqualTo("FAMILY");
		assertThat(access.check(new CheckPetAccessQuery(pet, stranger)).role()).isEqualTo("NONE");
		assertThat(access.check(new CheckPetAccessQuery(Long.MAX_VALUE, owner)).role()).isEqualTo("NONE");
	}

	@Test
	@DisplayName("#133 같은 회원은 같은 반려동물에 두 번 가족이 될 수 없다")
	void sameMemberCannotJoinTwice() {
		long pet = createPet.create(new CreatePetCommand(uniqueMemberId(), "콩이", "CAT", null, null)).petId();
		long family = uniqueMemberId();
		saveFamily.save(PetFamilyMember.join(pet, family, Instant.now()));

		assertThatThrownBy(() -> saveFamily.save(PetFamilyMember.join(pet, family, Instant.now())))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("#133 pet은 요청자 검사 없이 소유주를 알려주고, 잠금 조회는 트랜잭션 밖에서 부를 수 없다")
	void petOwnerPorts() {
		long owner = uniqueMemberId();
		long pet = createPet.create(new CreatePetCommand(owner, "하루", "DOG", null, null)).petId();

		assertThat(petOwners.get(new GetPetOwnerQuery(pet)))
				.hasValueSatisfying(result -> assertThat(result.ownerMemberId()).isEqualTo(owner));
		assertThat(petOwners.get(new GetPetOwnerQuery(Long.MAX_VALUE))).isEmpty();
		assertThatThrownBy(() -> lockPetOwners.lock(new LockPetOwnerQuery(pet)))
				.isInstanceOf(IllegalTransactionStateException.class);
	}

	@Test
	@DisplayName("#133 잠금 조회는 트랜잭션 안에서 소유주를 돌려주고, 없는 반려동물이면 빈 값이다")
	void lockReturnsOwnerInsideTransaction() {
		long owner = uniqueMemberId();
		long pet = createPet.create(new CreatePetCommand(owner, "모카", "DOG", null, null)).petId();

		Optional<GetPetOwnerResult> locked = transaction
			.execute(status -> lockPetOwners.lock(new LockPetOwnerQuery(pet)));
		Optional<GetPetOwnerResult> missing = transaction
			.execute(status -> lockPetOwners.lock(new LockPetOwnerQuery(Long.MAX_VALUE)));

		assertThat(locked).hasValueSatisfying(result -> assertThat(result.ownerMemberId()).isEqualTo(owner));
		assertThat(missing).isEmpty();
	}

	@Test
	@DisplayName("#133 잠금을 쥔 트랜잭션이 끝날 때까지 같은 반려동물의 잠금 조회는 기다리고, 커밋된 최신 소유주를 읽는다")
	void lockWaitsForHolderAndReadsCommittedOwner() throws Exception {
		long owner = uniqueMemberId();
		long newOwner = uniqueMemberId();
		long pet = createPet.create(new CreatePetCommand(owner, "두부", "CAT", null, null)).petId();
		CountDownLatch locked = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);

		try (var executor = Executors.newFixedThreadPool(2)) {
			// 첫 트랜잭션: 잠그고 소유주를 바꾼 뒤, 신호를 받을 때까지 커밋하지 않고 기다린다
			Future<?> holder = executor.submit(() -> transaction.executeWithoutResult(status -> {
				lockPetOwners.lock(new LockPetOwnerQuery(pet));
				jdbc.update("UPDATE pets SET owner_member_id = ? WHERE id = ?", newOwner, pet);
				locked.countDown();
				awaitQuietly(release);
			}));
			assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();

			// 두 번째 트랜잭션: 같은 반려동물을 잠그려다 기다린다
			Future<Optional<GetPetOwnerResult>> waiter = executor
				.submit(() -> transaction.execute(status -> lockPetOwners.lock(new LockPetOwnerQuery(pet))));
			Thread.sleep(300);
			assertThat(waiter.isDone()).isFalse();

			// 첫 트랜잭션이 커밋하면 기다리던 쪽이 바뀐 소유주를 읽는다
			release.countDown();
			holder.get(5, TimeUnit.SECONDS);
			assertThat(waiter.get(5, TimeUnit.SECONDS))
					.hasValueSatisfying(result -> assertThat(result.ownerMemberId()).isEqualTo(newOwner));
		}
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
