package com.pawmento.family.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.pawmento.family.application.port.in.CheckPetAccessQuery;
import com.pawmento.family.application.port.in.CheckPetAccessResult;
import com.pawmento.family.application.port.out.LoadPetFamilyMemberPort;
import com.pawmento.family.application.port.out.LoadPetOwnerPort;

class CheckPetAccessServiceTest {
	private static final Long PET = 10L;
	private static final Long OWNER = 1L;
	private static final Long FAMILY = 2L;
	private static final Long STRANGER = 3L;

	private final LoadPetOwnerPort owners = mock(LoadPetOwnerPort.class);
	private final LoadPetFamilyMemberPort families = mock(LoadPetFamilyMemberPort.class);
	private final CheckPetAccessService service = new CheckPetAccessService(owners, families);

	@Test
	@DisplayName("#133-S1 소유주는 OWNER이고 가족 테이블은 보지 않는다")
	void ownerIsOwner() {
		when(owners.loadOwnerId(PET)).thenReturn(Optional.of(OWNER));

		CheckPetAccessResult result = service.check(new CheckPetAccessQuery(PET, OWNER));

		assertThat(result.role()).isEqualTo("OWNER");
		assertThat(result.isOwner()).isTrue();
		assertThat(result.canRecord()).isTrue();
		verify(families, never()).existsByPetIdAndMemberId(any(), any());
	}

	@Test
	@DisplayName("#133-S1 가족은 FAMILY이고 기록할 수 있지만 소유주는 아니다")
	void familyIsFamily() {
		when(owners.loadOwnerId(PET)).thenReturn(Optional.of(OWNER));
		when(families.existsByPetIdAndMemberId(PET, FAMILY)).thenReturn(true);

		CheckPetAccessResult result = service.check(new CheckPetAccessQuery(PET, FAMILY));

		assertThat(result.role()).isEqualTo("FAMILY");
		assertThat(result.isOwner()).isFalse();
		assertThat(result.canRecord()).isTrue();
	}

	@Test
	@DisplayName("#133-S1 관계없는 회원은 NONE이고 기록할 수 없다")
	void strangerIsNone() {
		when(owners.loadOwnerId(PET)).thenReturn(Optional.of(OWNER));
		when(families.existsByPetIdAndMemberId(PET, STRANGER)).thenReturn(false);

		CheckPetAccessResult result = service.check(new CheckPetAccessQuery(PET, STRANGER));

		assertThat(result.role()).isEqualTo("NONE");
		assertThat(result.canRecord()).isFalse();
	}

	@Test
	@DisplayName("#133-S1 없는 반려동물이면 NONE이다")
	void missingPetIsNone() {
		when(owners.loadOwnerId(PET)).thenReturn(Optional.empty());

		CheckPetAccessResult result = service.check(new CheckPetAccessQuery(PET, OWNER));

		assertThat(result.role()).isEqualTo("NONE");
		verify(families, never()).existsByPetIdAndMemberId(any(), any());
	}
}
