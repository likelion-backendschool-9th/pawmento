package com.pawmento.family.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import com.pawmento.family.application.exception.AlreadyFamilyException;
import com.pawmento.family.application.exception.InviteCodeNotFoundException;
import com.pawmento.family.application.port.in.JoinFamilyCommand;
import com.pawmento.family.application.port.in.JoinFamilyResult;
import com.pawmento.family.application.port.out.ConsumePetInviteCodePort;
import com.pawmento.family.application.port.out.LoadPetFamilyMemberPort;
import com.pawmento.family.application.port.out.LoadPetInviteCodePort;
import com.pawmento.family.application.port.out.LoadPetSummaryPort;
import com.pawmento.family.application.port.out.LockPetOwnerPort;
import com.pawmento.family.application.port.out.PetSummary;
import com.pawmento.family.application.port.out.SavePetFamilyMemberPort;
import com.pawmento.family.domain.PetFamilyMember;
import com.pawmento.family.domain.PetInviteCode;

class JoinFamilyServiceTest {

	private static final Long PET = 10L;
	private static final Long OWNER = 1L;
	private static final Long FAMILY = 2L;
	private static final Long JOINER = 3L;
	private static final String CODE = "K7Q2M9XA";
	private static final Instant NOW = Instant.parse("2026-11-24T03:00:00Z");

	private final InviteCodes inviteCodes = new InviteCodes("test-secret");
	private final String hash = inviteCodes.hash(CODE);

	private final LoadPetInviteCodePort load = mock(LoadPetInviteCodePort.class);
	private final LockPetOwnerPort lock = mock(LockPetOwnerPort.class);
	private final LoadPetFamilyMemberPort families = mock(LoadPetFamilyMemberPort.class);
	private final ConsumePetInviteCodePort consume = mock(ConsumePetInviteCodePort.class);
	private final SavePetFamilyMemberPort save = mock(SavePetFamilyMemberPort.class);
	private final LoadPetSummaryPort pets = mock(LoadPetSummaryPort.class);

	// 테스트 중에 시각을 옮길 수 있게, 시계는 이 값을 읽는다
	private final AtomicReference<Instant> now = new AtomicReference<>(NOW);
	private final Clock clock = mock(Clock.class);

	private final JoinFamilyService service = new JoinFamilyService(
			inviteCodes, load, lock, families, consume, save, pets, clock);

	@BeforeEach
	void setUp() {
		when(clock.instant()).thenAnswer(invocation -> now.get());
		// 한 시간 전에 발급한, 아직 23시간 남은 코드
		when(load.loadByCodeHash(hash))
				.thenReturn(Optional.of(PetInviteCode.issue(PET, hash, OWNER, NOW.minusSeconds(3600))));
		when(lock.lockOwnerId(PET)).thenReturn(Optional.of(OWNER));
		when(consume.consume(any(), any())).thenReturn(true);
		when(pets.loadSummary(PET)).thenReturn(Optional.of(new PetSummary(PET, "보리", "0001")));
	}

	@Test
	@DisplayName("#133-S3 유효한 코드면 반려동물을 잠근 뒤 확인하고, 코드를 소비하고, 가족으로 저장한 뒤 반려동물 이름·태그를 돌려준다")
	void joinsInOrder() {
		JoinFamilyResult result = service.join(new JoinFamilyCommand(JOINER, CODE));

		assertThat(result).isEqualTo(new JoinFamilyResult(PET, "보리", "0001"));
		ArgumentCaptor<PetFamilyMember> saved = ArgumentCaptor.forClass(PetFamilyMember.class);
		InOrder order = inOrder(load, lock, families, consume, save, pets);

		order.verify(load).loadByCodeHash(hash);
		order.verify(lock).lockOwnerId(PET);
		order.verify(families).existsByPetIdAndMemberId(PET, JOINER);
		order.verify(consume).consume(hash, NOW);
		order.verify(save).save(saved.capture());
		order.verify(pets).loadSummary(PET);
		assertThat(saved.getValue().getPetId()).isEqualTo(PET);
		assertThat(saved.getValue().getMemberId()).isEqualTo(JOINER);
		assertThat(saved.getValue().getJoinedAt()).isEqualTo(NOW);
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
	@DisplayName("#133-S3 소문자와 공백이 섞인 입력도 같은 코드로 찾는다")
	void normalizesInput() {
		service.join(new JoinFamilyCommand(JOINER, "  k7q2m9xa "));

		verify(load).loadByCodeHash(hash);
		verify(save).save(any());
	}

	@Test
	@DisplayName("#133-S4 없는 코드면 404이고 반려동물을 잠그지도 않는다")
	void missingCode() {
		assertThatThrownBy(() -> service.join(new JoinFamilyCommand(JOINER, "ZZZZZZZZ")))
				.isInstanceOf(InviteCodeNotFoundException.class);
		verify(lock, never()).lockOwnerId(anyLong());
		verify(save, never()).save(any());
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
	@DisplayName("#133-S4 만료된 코드면 404이고 소비하지도, 가족을 만들지도 않는다")
	void expiredCode() {
		now.set(NOW.plusSeconds(23 * 3600));

		assertThatThrownBy(() -> service.join(new JoinFamilyCommand(JOINER, CODE)))
				.isInstanceOf(InviteCodeNotFoundException.class);
		verify(consume, never()).consume(any(), any());
		verify(save, never()).save(any());
	}

	@Test
	@DisplayName("#133-S4 잠금을 기다리는 사이 만료되면, 잠금 뒤 시각으로 판단해 404다")
	void expiresWhileWaitingForLock() {
		// 잠금이 풀리는 순간 시각이 만료 이후로 넘어간 상황
		when(lock.lockOwnerId(PET)).thenAnswer(invocation -> {
			now.set(NOW.plusSeconds(23 * 3600));
			return Optional.of(OWNER);
		});

		assertThatThrownBy(() -> service.join(new JoinFamilyCommand(JOINER, CODE)))
				.isInstanceOf(InviteCodeNotFoundException.class);
		verify(consume, never()).consume(any(), any());
		verify(save, never()).save(any());
	}

	@Test
	@DisplayName("#133-S4 소유주가 자기 반려동물 코드를 쓰면 409이고 코드는 소비하지 않는다")
	void ownerCannotJoin() {
		assertThatThrownBy(() -> service.join(new JoinFamilyCommand(OWNER, CODE)))
				.isInstanceOf(AlreadyFamilyException.class);
		verify(families, never()).existsByPetIdAndMemberId(any(), any());
		verify(consume, never()).consume(any(), any());
		verify(save, never()).save(any());
	}

	@Test
	@DisplayName("#133-S4 이미 가족인 회원이 코드를 쓰면 409이고 코드는 소비하지 않는다")
	void familyCannotJoinAgain() {
		when(families.existsByPetIdAndMemberId(PET, FAMILY)).thenReturn(true);

		assertThatThrownBy(() -> service.join(new JoinFamilyCommand(FAMILY, CODE)))
				.isInstanceOf(AlreadyFamilyException.class);
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
}
