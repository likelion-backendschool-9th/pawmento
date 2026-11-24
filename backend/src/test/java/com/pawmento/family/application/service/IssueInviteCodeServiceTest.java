package com.pawmento.family.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.pawmento.family.application.exception.FamilyAccessDeniedException;
import com.pawmento.family.application.exception.FamilyPetNotFoundException;
import com.pawmento.family.application.port.in.IssueInviteCodeCommand;
import com.pawmento.family.application.port.in.IssueInviteCodeResult;
import com.pawmento.family.application.port.out.LoadPetFamilyMemberPort;
import com.pawmento.family.application.port.out.LockPetOwnerPort;
import com.pawmento.family.application.port.out.SavePetInviteCodePort;
import com.pawmento.family.domain.PetInviteCode;

class IssueInviteCodeServiceTest {

	private static final Long PET = 10L;
	private static final Long OWNER = 1L;
	private static final Long FAMILY = 2L;
	private static final Long STRANGER = 3L;
	private static final Instant NOW = Instant.parse("2026-11-24T03:00:00Z");

	private final LockPetOwnerPort lock = mock(LockPetOwnerPort.class);
	private final LoadPetFamilyMemberPort families = mock(LoadPetFamilyMemberPort.class);
	private final SavePetInviteCodePort save = mock(SavePetInviteCodePort.class);
	private final InviteCodes inviteCodes = new InviteCodes();
	private final IssueInviteCodeService service = new IssueInviteCodeService(
			lock, families, save, inviteCodes, Clock.fixed(NOW, ZoneOffset.UTC));

	@BeforeEach
	void saveReturnsArgument() {
		when(save.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
	}

	@Test
	@DisplayName("#133-S2 소유주가 발급하면 원래 코드는 응답으로만 주고, 저장소에는 그 코드의 해시만 넘긴다")
	void ownerIssuesCodeAndOnlyHashIsSaved() {
		when(lock.lockOwnerId(PET)).thenReturn(Optional.of(OWNER));

		IssueInviteCodeResult result = service.issue(new IssueInviteCodeCommand(OWNER, PET));

		ArgumentCaptor<PetInviteCode> saved = ArgumentCaptor.forClass(PetInviteCode.class);
		verify(save).save(saved.capture());
		assertThat(result.code()).matches("[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{8}");
		assertThat(result.expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
		assertThat(saved.getValue().getCodeHash())
				.isEqualTo(inviteCodes.hash(result.code()))
				.isNotEqualTo(result.code());
		assertThat(saved.getValue().getPetId()).isEqualTo(PET);
		assertThat(saved.getValue().getIssuerMemberId()).isEqualTo(OWNER);
		// 소유주면 가족 테이블은 볼 필요가 없다
		verify(families, never()).existsByPetIdAndMemberId(any(), any());
	}

	@Test
	@DisplayName("#133-S2 가족도 코드를 발급할 수 있고, 발급자는 그 가족이다")
	void familyIssuesCode() {
		when(lock.lockOwnerId(PET)).thenReturn(Optional.of(OWNER));
		when(families.existsByPetIdAndMemberId(PET, FAMILY)).thenReturn(true);

		service.issue(new IssueInviteCodeCommand(FAMILY, PET));

		ArgumentCaptor<PetInviteCode> saved = ArgumentCaptor.forClass(PetInviteCode.class);
		verify(save).save(saved.capture());
		assertThat(saved.getValue().getIssuerMemberId()).isEqualTo(FAMILY);
	}

	@Test
	@DisplayName("#133-S2 관계없는 회원이 발급하면 403이고 코드를 저장하지 않는다")
	void strangerIsDenied() {
		when(lock.lockOwnerId(PET)).thenReturn(Optional.of(OWNER));
		when(families.existsByPetIdAndMemberId(PET, STRANGER)).thenReturn(false);

		assertThatThrownBy(() -> service.issue(new IssueInviteCodeCommand(STRANGER, PET)))
				.isInstanceOf(FamilyAccessDeniedException.class);
		verify(save, never()).save(any());
	}

	@Test
	@DisplayName("#133-S2 없는 반려동물이면 404이고 코드를 저장하지 않는다")
	void missingPetIsNotFound() {
		when(lock.lockOwnerId(PET)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.issue(new IssueInviteCodeCommand(OWNER, PET)))
				.isInstanceOf(FamilyPetNotFoundException.class);
		verify(save, never()).save(any());
	}
}
