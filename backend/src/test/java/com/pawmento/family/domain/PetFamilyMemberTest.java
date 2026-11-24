package com.pawmento.family.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PetFamilyMemberTest {
	private static final Instant NOW = Instant.parse("2026-11-24T00:00:00Z");

	@Test
	@DisplayName("#133 가족이 되면 반려동물·회원·가족이 된 시각을 가진다")
	void join() {
		PetFamilyMember member = PetFamilyMember.join(10L, 2L, NOW);

		assertThat(member.getId()).isNull();
		assertThat(member.getPetId()).isEqualTo(10L);
		assertThat(member.getMemberId()).isEqualTo(2L);
		assertThat(member.getJoinedAt()).isEqualTo(NOW);
	}

	@Test
	@DisplayName("#133 반려동물이나 회원 없이 가족을 만들 수 없다")
	void requiresPetAndMember() {
		assertThatThrownBy(() -> PetFamilyMember.join(null, 2L, NOW)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> PetFamilyMember.join(10L, null, NOW)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("#133 기록은 소유주와 가족만 할 수 있다")
	void onlyOwnerAndFamilyCanRecord() {
		assertThat(PetAccess.OWNER.canRecord()).isTrue();
		assertThat(PetAccess.FAMILY.canRecord()).isTrue();
		assertThat(PetAccess.NONE.canRecord()).isFalse();
	}
}
