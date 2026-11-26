package com.pawmento.family.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.pawmento.family.application.exception.InviteCodeNotFoundException;
import com.pawmento.family.application.port.in.JoinFamilyCommand;
import com.pawmento.family.application.port.out.ConsumePetInviteCodePort;
import com.pawmento.family.application.port.out.LoadPetFamilyMemberPort;
import com.pawmento.family.application.port.out.LoadPetInviteCodePort;
import com.pawmento.family.application.port.out.LoadPetSummaryPort;
import com.pawmento.family.application.port.out.LockPetOwnerPort;
import com.pawmento.family.application.port.out.PetSummary;
import com.pawmento.family.application.port.out.SavePetFamilyMemberPort;
import com.pawmento.family.domain.PetInviteCode;

/**
 * 정상 가입과 404·409 거절은 {@code FamilyInviteScenarioTest}가 실제 DB와 요청으로 확인한다.
 * 여기에는 요청으로 고정하기 어려운 분기(코드는 남았는데 반려동물이 사라짐, 요약 조회 실패, 다른 요청이 먼저 코드를 소비함,
 * 잠금을 기다리는 사이 코드가 폐기되거나 다른 행으로 바뀜)만 남긴다.
 */
class JoinFamilyServiceTest {

	private static final Long PET = 10L;
	private static final Long OWNER = 1L;
	private static final Long FAMILY = 2L;
	private static final Long JOINER = 3L;
	private static final String CODE = "K7Q2M9XA";
	private static final Instant NOW = Instant.parse("2026-11-24T03:00:00Z");
	private static final Long CODE_ID = 77L;

	private final InviteCodes inviteCodes = new InviteCodes("test-secret");
	private final String hash = inviteCodes.hash(CODE);

	private final LoadPetInviteCodePort load = mock(LoadPetInviteCodePort.class);
	private final LockPetOwnerPort lock = mock(LockPetOwnerPort.class);
	private final LoadPetFamilyMemberPort families = mock(LoadPetFamilyMemberPort.class);
	private final ConsumePetInviteCodePort consume = mock(ConsumePetInviteCodePort.class);
	private final SavePetFamilyMemberPort save = mock(SavePetFamilyMemberPort.class);
	private final LoadPetSummaryPort pets = mock(LoadPetSummaryPort.class);
	private final Clock clock = mock(Clock.class);

	private final JoinFamilyService service = new JoinFamilyService(
			inviteCodes, load, lock, families, consume, save, pets, clock);

	@BeforeEach
	void setUp() {
		when(clock.instant()).thenReturn(NOW);
		when(load.loadByCodeHash(hash)).thenReturn(Optional.of(storedCode(CODE_ID, PET)));
		when(lock.lockOwnerId(PET)).thenReturn(Optional.of(OWNER));
		when(consume.consume(any(), any())).thenReturn(true);
		when(pets.loadSummary(PET)).thenReturn(Optional.of(new PetSummary(PET, "보리", "0001")));
	}

	@Test
	@DisplayName("#133-S3 잠근 반려동물의 요약을 읽지 못하면 코드 문제(404)가 아니라 서버 오류로 드러낸다")
	void missingSummaryIsServerError() {
		when(pets.loadSummary(PET)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.join(new JoinFamilyCommand(JOINER, CODE)))
				.isInstanceOf(IllegalStateException.class)
				.isNotInstanceOf(InviteCodeNotFoundException.class);
	}

	@Test
	@DisplayName("#133-S4 코드의 반려동물이 사라졌으면 코드 없음과 같은 404다")
	void petGone() {
		when(lock.lockOwnerId(PET)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.join(new JoinFamilyCommand(JOINER, CODE)))
				.isInstanceOf(InviteCodeNotFoundException.class);
		verify(consume, never()).consume(any(), any());
		verify(save, never()).save(any());
	}

	@Test
	@DisplayName("#133-S18 잠금을 기다리는 사이 코드가 폐기되면, 이미 가족이어도 409가 아니라 404다")
	void revokedWhileWaitingForLock() {
		// 처음 읽을 때는 있었지만, 잠금을 얻고 다시 읽으면 내보내기가 이미 지웠다
		when(load.loadByCodeHash(hash)).thenReturn(
				Optional.of(storedCode(CODE_ID, PET)),
				Optional.empty());
		when(families.existsByPetIdAndMemberId(PET, FAMILY)).thenReturn(true);

		assertThatThrownBy(() -> service.join(new JoinFamilyCommand(FAMILY, CODE)))
				.isInstanceOf(InviteCodeNotFoundException.class);
		verify(families, never()).existsByPetIdAndMemberId(any(), any());
		verify(consume, never()).consume(any(), any());
		verify(save, never()).save(any());
	}

	@Test
	@DisplayName("#133-S18 잠금 뒤 다시 읽은 코드가 같은 해시의 다른 행이면 404이고, 코드도 멤버십도 건드리지 않는다")
	void sameHashDifferentRowAfterLock() {
		// 처음 읽은 코드는 지워지고, 같은 값이 새 행으로 다시 발급된 상황
		when(load.loadByCodeHash(hash)).thenReturn(
				Optional.of(storedCode(CODE_ID, PET)),
				Optional.of(storedCode(CODE_ID + 1, PET)));

		assertThatThrownBy(() -> service.join(new JoinFamilyCommand(JOINER, CODE)))
				.isInstanceOf(InviteCodeNotFoundException.class);
		verify(families, never()).existsByPetIdAndMemberId(any(), any());
		verify(consume, never()).consume(any(), any());
		verify(save, never()).save(any());
	}

	@Test
	@DisplayName("#133-S18 잠금 뒤 다시 읽은 코드가 다른 반려동물의 것이면 404이고, 코드도 멤버십도 건드리지 않는다")
	void codeOfOtherPetAfterLock() {
		when(load.loadByCodeHash(hash)).thenReturn(
				Optional.of(storedCode(CODE_ID, PET)),
				Optional.of(storedCode(CODE_ID, PET + 1)));

		assertThatThrownBy(() -> service.join(new JoinFamilyCommand(JOINER, CODE)))
				.isInstanceOf(InviteCodeNotFoundException.class);
		verify(consume, never()).consume(any(), any());
		verify(save, never()).save(any());
	}

	@Test
	@DisplayName("#133-S5 소비가 실패하면(다른 요청이 먼저 썼음) 404이고 가족을 만들지 않는다")
	void consumedByOther() {
		when(consume.consume(any(), any())).thenReturn(false);

		assertThatThrownBy(() -> service.join(new JoinFamilyCommand(JOINER, CODE)))
				.isInstanceOf(InviteCodeNotFoundException.class);
		verify(save, never()).save(any());
	}

	// DB에서 읽어 온 코드처럼 id가 있다. 한 시간 전에 발급해 아직 23시간 남았다
	private PetInviteCode storedCode(Long id, Long petId) {
		Instant issuedAt = NOW.minusSeconds(3600);
		return PetInviteCode.restore(id, petId, hash, OWNER, issuedAt.plus(PetInviteCode.VALID_FOR), issuedAt);
	}
}
