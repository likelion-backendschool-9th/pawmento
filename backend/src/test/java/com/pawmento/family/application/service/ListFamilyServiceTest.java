package com.pawmento.family.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.pawmento.family.application.exception.FamilyAccessDeniedException;
import com.pawmento.family.application.exception.FamilyPetNotFoundException;
import com.pawmento.family.application.port.in.ListFamilyQuery;
import com.pawmento.family.application.port.in.ListFamilyResult;
import com.pawmento.family.application.port.out.FamilyMemberProfile;
import com.pawmento.family.application.port.out.LoadMemberProfilesPort;
import com.pawmento.family.application.port.out.LoadPetFamilyMemberPort;
import com.pawmento.family.application.port.out.LoadPetOwnerPort;
import com.pawmento.family.domain.PetFamilyMember;

class ListFamilyServiceTest {

	private static final Long PET = 10L;
	private static final Long OWNER = 1L;
	private static final Long FIRST = 2L;
	private static final Long SECOND = 3L;
	private static final Long STRANGER = 9L;
	private static final Instant EARLY = Instant.parse("2026-11-24T03:00:00Z");
	private static final Instant LATE = Instant.parse("2026-11-25T03:00:00Z");

	private final LoadPetOwnerPort owners = mock(LoadPetOwnerPort.class);
	private final LoadPetFamilyMemberPort families = mock(LoadPetFamilyMemberPort.class);
	private final LoadMemberProfilesPort profiles = mock(LoadMemberProfilesPort.class);
	private final ListFamilyService service = new ListFamilyService(owners, families, profiles);

	@BeforeEach
	void setUp() {
		when(owners.loadOwnerId(PET)).thenReturn(Optional.of(OWNER));
		// 저장소가 가입 순서로 준다
		when(families.loadByPetId(PET)).thenReturn(List.of(
				PetFamilyMember.restore(100L, PET, FIRST, EARLY),
				PetFamilyMember.restore(101L, PET, SECOND, LATE)));
		when(profiles.loadProfiles(List.of(OWNER, FIRST, SECOND))).thenReturn(List.of(
				new FamilyMemberProfile(SECOND, "보라", "7777"),
				new FamilyMemberProfile(OWNER, "다윗", "0427"),
				new FamilyMemberProfile(FIRST, "수민", "1093")));
	}

	@Test
	@DisplayName("#133-S6 소유주는 소유주와 가족을 닉네임·태그와 함께, 가족은 가입 순서대로 본다")
	void ownerSeesFamily() {
		ListFamilyResult result = service.list(new ListFamilyQuery(OWNER, PET));

		assertThat(result.owner()).isEqualTo(new ListFamilyResult.Owner(OWNER, "다윗", "0427"));
		// 프로필이 어떤 순서로 와도, 가족 목록은 가입 순서를 따른다
		assertThat(result.members()).containsExactly(
				new ListFamilyResult.Family(FIRST, "수민", "1093", EARLY),
				new ListFamilyResult.Family(SECOND, "보라", "7777", LATE));
	}

	@Test
	@DisplayName("#133-S6 가족도 같은 목록을 본다")
	void familySeesFamily() {
		ListFamilyResult result = service.list(new ListFamilyQuery(SECOND, PET));

		assertThat(result.owner().memberId()).isEqualTo(OWNER);
		assertThat(result.members()).extracting(ListFamilyResult.Family::memberId).containsExactly(FIRST, SECOND);
	}

	@Test
	@DisplayName("#133-S6 관계없는 회원은 403이고, 회원 정보는 읽지도 않는다")
	void strangerIsDenied() {
		assertThatThrownBy(() -> service.list(new ListFamilyQuery(STRANGER, PET)))
				.isInstanceOf(FamilyAccessDeniedException.class);
		verify(profiles, never()).loadProfiles(any());
	}

	@Test
	@DisplayName("#133-S6 없는 반려동물이면 404다")
	void missingPet() {
		when(owners.loadOwnerId(PET)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.list(new ListFamilyQuery(OWNER, PET)))
				.isInstanceOf(FamilyPetNotFoundException.class);
		verify(families, never()).loadByPetId(any());
	}

	@Test
	@DisplayName("#133-S6 가족이 없으면 소유주만 있고 members는 빈 목록이다")
	void ownerOnly() {
		when(families.loadByPetId(PET)).thenReturn(List.of());
		when(profiles.loadProfiles(List.of(OWNER))).thenReturn(List.of(new FamilyMemberProfile(OWNER, "다윗", "0427")));

		ListFamilyResult result = service.list(new ListFamilyQuery(OWNER, PET));

		assertThat(result.owner().nickname()).isEqualTo("다윗");
		assertThat(result.members()).isEmpty();
	}

	@Test
	@DisplayName("#133-S6 회원 정보를 찾지 못한 사람은 목록에 남기고 닉네임·태그만 비운다")
	void missingProfileKeepsRow() {
		when(profiles.loadProfiles(List.of(OWNER, FIRST, SECOND))).thenReturn(List.of(
				new FamilyMemberProfile(FIRST, "수민", "1093")));

		ListFamilyResult result = service.list(new ListFamilyQuery(OWNER, PET));

		assertThat(result.owner()).isEqualTo(new ListFamilyResult.Owner(OWNER, null, null));
		assertThat(result.members()).containsExactly(
				new ListFamilyResult.Family(FIRST, "수민", "1093", EARLY),
				new ListFamilyResult.Family(SECOND, null, null, LATE));
	}
}
