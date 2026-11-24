package com.pawmento.pet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.pawmento.TestcontainersConfiguration;
import com.pawmento.pet.application.exception.PetConcurrentModificationException;
import com.pawmento.pet.application.port.in.ChangePetOwnerCommand;
import com.pawmento.pet.application.port.in.ChangePetOwnerUseCase;
import com.pawmento.pet.application.port.out.LoadPetPort;
import com.pawmento.pet.application.port.out.SavePetPort;
import com.pawmento.pet.domain.Pet;
import com.pawmento.pet.domain.PetSpecies;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "pawmento.auth.jwt-secret=test-only-jwt-secret-0123456789-abcdefghij")
class PetOwnerTransferScenarioTest {

	private static final AtomicLong NEXT_MEMBER_ID = new AtomicLong(133_500);

	private final Long ownerId = NEXT_MEMBER_ID.incrementAndGet();
	private final Long recipientId = NEXT_MEMBER_ID.incrementAndGet();

	@Autowired
	private ChangePetOwnerUseCase changePetOwner;

	@Autowired
	private SavePetPort savePet;

	@MockitoSpyBean
	private LoadPetPort loadPet;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private TransactionTemplate transaction;

	@Test
	@DisplayName("#133-S13 pet 포트가 소유주를 변경하고 충돌하지 않는 태그와 프로필을 보존한다")
	void changesOwnerAndKeepsAvailableTag() {
		Long petId = savePet(ownerId, "keep");

		transfer(petId, ownerId);

		Pet pet = loadPet.findById(petId).orElseThrow();
		assertThat(pet.getOwnerMemberId()).isEqualTo(recipientId);
		assertThat(pet.getTag()).isEqualTo("keep");
		assertThat(pet.getName()).isEqualTo("보리");
		assertThat(pet.getBirthYear()).isEqualTo(2020);
	}

	@Test
	@DisplayName("#133-S15 새 소유주에게 이미 같은 태그가 있으면 이전되는 반려동물만 재발급한다")
	void reissuesTagWhenAlreadyTaken() {
		Long petId = savePet(ownerId, "same");
		Long existingPetId = savePet(recipientId, "same");

		transfer(petId, ownerId);

		Pet transferred = loadPet.findById(petId).orElseThrow();
		assertThat(transferred.getOwnerMemberId()).isEqualTo(recipientId);
		assertThat(transferred.getTag()).matches("[a-z0-9]{4}").isNotEqualTo("same");
		assertThat(loadPet.findById(existingPetId).orElseThrow().getTag()).isEqualTo("same");
	}

	@Test
	@DisplayName("#133-S13 pet 포트 방어 확인: 기대 소유주가 다르면 409로 거절한다")
	void rejectsStaleOwner() {
		Long petId = savePet(ownerId, "old1");

		assertThatThrownBy(() -> transfer(petId, recipientId))
			.isInstanceOf(PetConcurrentModificationException.class);
		Pet pet = loadPet.findById(petId).orElseThrow();
		assertThat(pet.getOwnerMemberId()).isEqualTo(ownerId);
		assertThat(pet.getTag()).isEqualTo("old1");
	}

	@Test
	@DisplayName("#133-S16 호출자 트랜잭션이 실패하면 pet 포트가 저장한 소유주도 롤백된다")
	void rollsBackWithCaller() {
		Long petId = savePet(ownerId, "roll");

		transaction.executeWithoutResult(status -> {
			changePetOwner.changeOwner(new ChangePetOwnerCommand(petId, ownerId, recipientId));
			assertThat(loadPet.findById(petId).orElseThrow().getOwnerMemberId()).isEqualTo(recipientId);
			status.setRollbackOnly();
		});

		assertThat(loadPet.findById(petId).orElseThrow().getOwnerMemberId()).isEqualTo(ownerId);
	}

	@Test
	@DisplayName("#133-S15 태그 확인 직후 같은 태그의 등록이 커밋되면 이전은 409로 롤백된다")
	void rejectsTagTakenAfterAvailabilityCheck() {
		Long petId = savePet(ownerId, "race");
		TransactionTemplate registration = new TransactionTemplate(transactionManager);
		registration.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		doAnswer(invocation -> {
			boolean alreadyTaken = (boolean) invocation.callRealMethod();
			assertThat(alreadyTaken).isFalse();
			registration.executeWithoutResult(status -> savePet(recipientId, "race"));
			return alreadyTaken;
		}).when(loadPet).existsByOwnerMemberIdAndTag(recipientId, "race");

		assertThatThrownBy(() -> transfer(petId, ownerId))
			.isInstanceOf(PetConcurrentModificationException.class)
			.hasRootCauseInstanceOf(PSQLException.class);
		Pet pet = loadPet.findById(petId).orElseThrow();
		assertThat(pet.getOwnerMemberId()).isEqualTo(ownerId);
		assertThat(pet.getTag()).isEqualTo("race");
		assertThat(loadPet.findByOwnerMemberId(recipientId)).anyMatch(registered -> registered.getTag().equals("race"));
	}

	@Test
	@DisplayName("#133-S16 pet 소유주 변경은 호출자 트랜잭션 밖에서 실행할 수 없다")
	void requiresCallerTransaction() {
		Long petId = savePet(ownerId, "tx01");

		assertThatThrownBy(() -> changePetOwner.changeOwner(new ChangePetOwnerCommand(petId, ownerId, recipientId)))
			.isInstanceOf(IllegalTransactionStateException.class);
	}

	private Long savePet(Long owner, String tag) {
		return transaction.execute(status -> savePet.save(
			Pet.create(owner, "보리", tag, PetSpecies.DOG, null, null, 2020, LocalDate.of(2026, 11, 24))));
	}

	private void transfer(Long petId, Long currentOwner) {
		transaction.executeWithoutResult(status ->
			changePetOwner.changeOwner(new ChangePetOwnerCommand(petId, currentOwner, recipientId)));
	}

}
