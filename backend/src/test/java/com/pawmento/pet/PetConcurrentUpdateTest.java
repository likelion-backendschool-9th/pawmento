package com.pawmento.pet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

import com.pawmento.TestcontainersConfiguration;
import com.pawmento.pet.application.exception.PetConcurrentModificationException;
import com.pawmento.pet.application.port.in.ChangePetModeCommand;
import com.pawmento.pet.application.port.in.ChangePetModeUseCase;
import com.pawmento.pet.application.port.in.CreatePetCommand;
import com.pawmento.pet.application.port.in.CreatePetUseCase;
import com.pawmento.pet.application.port.out.LoadPetPort;
import com.pawmento.pet.application.port.out.SavePetPort;
import com.pawmento.pet.domain.Pet;
import com.pawmento.pet.domain.PetMode;

// #46 이름 수정과 모드 변경이 겹칠 때 완료된 모드가 덮이지 않는지 확인한다
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "pawmento.auth.jwt-secret=test-only-jwt-secret-0123456789-abcdefghij")
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
class PetConcurrentUpdateTest {

	@Autowired
	private org.springframework.test.web.servlet.MockMvc mvc;

	@Autowired
	private CreatePetUseCase createPetUseCase;

	@Autowired
	private ChangePetModeUseCase changePetModeUseCase;

	@org.springframework.test.context.bean.override.mockito.MockitoSpyBean
	private LoadPetPort loadPetPort;

	@Autowired
	private SavePetPort savePetPort;

	@Autowired
	private TransactionTemplate transactionTemplate;

	@Test
	@DisplayName("#46 이름 수정이 조회한 뒤 커밋된 모드 변경을 덮어쓰지 않고 409로 거절한다")
	void editDoesNotClobberCommittedModeChange() throws Exception {
		Long memberId = 9001L;
		Long petId = createPetUseCase
			.create(new CreatePetCommand(memberId, "콩이", "DOG", "코기", LocalDate.of(2015, 3, 2)))
			.petId();

		CountDownLatch editLoaded = new CountDownLatch(1);
		CountDownLatch modeCommitted = new CountDownLatch(1);
		AtomicReference<Throwable> editError = new AtomicReference<>();
		CyclicBarrier started = new CyclicBarrier(2);

		Thread editThread = new Thread(() -> {
			try {
				started.await(5, TimeUnit.SECONDS);
				transactionTemplate.executeWithoutResult(status -> {
					Pet pet = loadPetPort.findById(petId).orElseThrow();
					assertThat(pet.getMode()).isEqualTo(PetMode.ACTIVE);
					editLoaded.countDown();
					await(modeCommitted);
					pet.edit("콩콩", null, null, null, LocalDate.of(2026, 11, 17));
					savePetPort.save(pet);
				});
			}
			catch (Throwable e) {
				editError.set(e);
			}
		}, "pet-edit");

		Thread modeThread = new Thread(() -> {
			try {
				started.await(5, TimeUnit.SECONDS);
				await(editLoaded);
				changePetModeUseCase.changeMode(new ChangePetModeCommand(memberId, petId, "GARDEN"));
				assertThat(loadPetPort.findById(petId).orElseThrow().getMode()).isEqualTo(PetMode.GARDEN);
				modeCommitted.countDown();
			}
			catch (Throwable e) {
				editError.compareAndSet(null, e);
				modeCommitted.countDown();
			}
		}, "pet-mode");

		editThread.start();
		modeThread.start();
		editThread.join(10_000);
		modeThread.join(10_000);

		assertThat(editError.get()).isInstanceOf(PetConcurrentModificationException.class);

		Pet saved = loadPetPort.findById(petId).orElseThrow();
		assertThat(saved.getMode()).isEqualTo(PetMode.GARDEN);
		assertThat(saved.getName()).isEqualTo("콩이");
	}

	@Test
	@DisplayName("#46 오래된 버전으로 저장하면 충돌하고 DB 모드는 그대로다")
	void staleVersionSaveIsRejected() {
		Long memberId = 9002L;
		Long petId = createPetUseCase.create(new CreatePetCommand(memberId, "보리", "DOG", null, null)).petId();
		Pet stale = loadPetPort.findById(petId).orElseThrow();

		changePetModeUseCase.changeMode(new ChangePetModeCommand(memberId, petId, "GARDEN"));

		stale.edit("보보리", null, null, null, LocalDate.of(2026, 11, 17));
		assertThatThrownBy(() -> savePetPort.save(stale)).isInstanceOf(PetConcurrentModificationException.class);

		Pet saved = loadPetPort.findById(petId).orElseThrow();
		assertThat(saved.getMode()).isEqualTo(PetMode.GARDEN);
		assertThat(saved.getName()).isEqualTo("보리");
	}

	@Test
	@DisplayName("#72-S5 오래된 DB 버전의 HTTP 수정은 409·CONFLICT·기존 문구이며 완료된 모드를 보존한다")
	void staleHttpEditReturnsConflict() throws Exception {
		Long memberId = 9003L;
		Long petId = createPetUseCase.create(new CreatePetCommand(memberId, "콩이", "DOG", null, null)).petId();
		Pet stale = loadPetPort.findById(petId).orElseThrow();
		changePetModeUseCase.changeMode(new ChangePetModeCommand(memberId, petId, "GARDEN"));
		// 먼저 읽은 스냅샷을 서비스에 제공한다. 저장·flush·DB 버전 검사·웹 예외 처리는 실제 경로다.
		doReturn(java.util.Optional.of(stale)).when(loadPetPort).findById(petId);
		try {
			mvc.perform(patch("/api/v1/pets/" + petId).with(jwt().jwt(token -> token.subject(memberId.toString())))
				.contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"name\":\"늦은수정\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CONFLICT"))
				.andExpect(jsonPath("$.message").value("다른 요청에서 반려동물을 먼저 수정했습니다. 다시 불러와 주세요."));
		} finally {
			reset(loadPetPort);
		}
		Pet saved = loadPetPort.findById(petId).orElseThrow();
		assertThat(saved.getMode()).isEqualTo(PetMode.GARDEN);
		assertThat(saved.getName()).isEqualTo("콩이");
	}

	private static void await(CountDownLatch latch) {
		try {
			assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		}
	}

}
