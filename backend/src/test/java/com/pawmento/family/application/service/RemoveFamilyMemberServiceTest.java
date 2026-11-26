package com.pawmento.family.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.pawmento.family.application.exception.FamilyAccessDeniedException;
import com.pawmento.family.application.exception.FamilyMemberNotFoundException;
import com.pawmento.family.application.exception.FamilyPetNotFoundException;
import com.pawmento.family.application.exception.OwnerNotRemovableException;
import com.pawmento.family.application.port.in.LeaveFamilyCommand;
import com.pawmento.family.application.port.in.RemoveFamilyMemberCommand;
import com.pawmento.family.application.port.out.DeletePetFamilyMemberPort;
import com.pawmento.family.application.port.out.DeletePetInviteCodesByIssuerPort;
import com.pawmento.family.application.port.out.LockPetOwnerPort;

class RemoveFamilyMemberServiceTest {

	private static final Long PET = 10L;
	private static final Long OWNER = 1L;
	private static final Long FAMILY = 2L;
	private static final Long OTHER = 3L;

	private final LockPetOwnerPort lock = mock(LockPetOwnerPort.class);
	private final DeletePetFamilyMemberPort members = mock(DeletePetFamilyMemberPort.class);
	private final DeletePetInviteCodesByIssuerPort codes = mock(DeletePetInviteCodesByIssuerPort.class);
	private final RemoveFamilyMemberService service = new RemoveFamilyMemberService(lock, members, codes);

	@BeforeEach
	void setUp() {
		when(lock.lockOwnerId(PET)).thenReturn(Optional.of(OWNER));
	}

	@Test
	@DisplayName("#133-S10 소유주가 가족을 내보내면 반려동물을 잠근 뒤 멤버십과 그 가족이 만든 코드를 지운다")
	void ownerRemovesFamily() {
		when(members.delete(PET, FAMILY)).thenReturn(true);

		service.remove(new RemoveFamilyMemberCommand(OWNER, PET, FAMILY));

		InOrder order = inOrder(lock, members, codes);
		order.verify(lock).lockOwnerId(PET);
		order.verify(members).delete(PET, FAMILY);
		order.verify(codes).deleteByIssuer(PET, FAMILY);
	}

	@Test
	@DisplayName("#133-S10 소유주가 아닌 회원이 내보내면 대상을 확인하기 전에 403이다")
	void nonOwnerCannotRemove() {
		assertThatThrownBy(() -> service.remove(new RemoveFamilyMemberCommand(FAMILY, PET, OTHER)))
				.isInstanceOf(FamilyAccessDeniedException.class);
		verify(members, never()).delete(anyLong(), anyLong());
		verify(codes, never()).deleteByIssuer(anyLong(), anyLong());
	}

	@Test
	@DisplayName("#133-S10 대상이 가족이 아니면 404이고 코드는 지우지 않는다")
	void targetNotFamily() {
		when(members.delete(PET, OTHER)).thenReturn(false);

		assertThatThrownBy(() -> service.remove(new RemoveFamilyMemberCommand(OWNER, PET, OTHER)))
				.isInstanceOf(FamilyMemberNotFoundException.class);
		verify(codes, never()).deleteByIssuer(anyLong(), anyLong());
	}

	@Test
	@DisplayName("#133-S10 소유주 자신을 내보내려 하면 409이고 아무것도 지우지 않는다")
	void ownerCannotRemoveSelf() {
		assertThatThrownBy(() -> service.remove(new RemoveFamilyMemberCommand(OWNER, PET, OWNER)))
				.isInstanceOf(OwnerNotRemovableException.class);
		verify(members, never()).delete(anyLong(), anyLong());
	}

	@Test
	@DisplayName("#133-S10 없는 반려동물이면 404다")
	void removeMissingPet() {
		when(lock.lockOwnerId(PET)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.remove(new RemoveFamilyMemberCommand(OWNER, PET, FAMILY)))
				.isInstanceOf(FamilyPetNotFoundException.class);
		verify(members, never()).delete(any(), any());
	}

	@Test
	@DisplayName("#133-S11 가족이 나가면 반려동물을 잠근 뒤 멤버십과 자기가 만든 코드를 지운다")
	void familyLeaves() {
		when(members.delete(PET, FAMILY)).thenReturn(true);

		service.leave(new LeaveFamilyCommand(FAMILY, PET));

		InOrder order = inOrder(lock, members, codes);
		order.verify(lock).lockOwnerId(PET);
		order.verify(members).delete(PET, FAMILY);
		order.verify(codes).deleteByIssuer(PET, FAMILY);
	}

	@Test
	@DisplayName("#133-S11 소유주가 나가기를 요청하면 409이고 아무것도 지우지 않는다")
	void ownerCannotLeave() {
		assertThatThrownBy(() -> service.leave(new LeaveFamilyCommand(OWNER, PET)))
				.isInstanceOf(OwnerNotRemovableException.class);
		verify(members, never()).delete(anyLong(), anyLong());
	}

	@Test
	@DisplayName("#133-S11 가족이 아닌 회원이 나가기를 요청하면 403이고 코드는 지우지 않는다")
	void strangerCannotLeave() {
		when(members.delete(PET, OTHER)).thenReturn(false);

		assertThatThrownBy(() -> service.leave(new LeaveFamilyCommand(OTHER, PET)))
				.isInstanceOf(FamilyAccessDeniedException.class);
		verify(codes, never()).deleteByIssuer(anyLong(), anyLong());
	}

	@Test
	@DisplayName("#133-S11 없는 반려동물이면 404다")
	void leaveMissingPet() {
		when(lock.lockOwnerId(PET)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.leave(new LeaveFamilyCommand(FAMILY, PET)))
				.isInstanceOf(FamilyPetNotFoundException.class);
	}
}
