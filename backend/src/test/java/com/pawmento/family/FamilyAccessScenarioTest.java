package com.pawmento.family;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.IllegalTransactionStateException;

import com.pawmento.TestcontainersConfiguration;
import com.pawmento.family.application.port.in.CheckPetAccessQuery;
import com.pawmento.family.application.port.in.CheckPetAccessUseCase;
import com.pawmento.family.application.port.out.SavePetFamilyMemberPort;
import com.pawmento.family.domain.PetFamilyMember;
import com.pawmento.pet.application.port.in.CreatePetCommand;
import com.pawmento.pet.application.port.in.CreatePetUseCase;
import com.pawmento.pet.application.port.in.GetPetOwnerQuery;
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

	// 회원 테이블과 외래 키가 없어, 겹치지 않는 아무 회원 id면 된다
	private long uniqueMemberId() {
		return ThreadLocalRandom.current().nextLong(1_000_000_000L, Long.MAX_VALUE / 2);
	}
}
