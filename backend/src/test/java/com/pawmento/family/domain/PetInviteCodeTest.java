package com.pawmento.family.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PetInviteCodeTest {

	private static final Instant NOW = Instant.parse("2026-11-24T03:00:00Z");
	private static final String HASH = "a".repeat(64);

	@Test
	@DisplayName("#133-S2 발급한 코드는 지금부터 24시간 뒤에 만료되고, 아직 저장 전이라 id가 없다")
	void issueSetsExpiryAfter24Hours() {
		PetInviteCode code = PetInviteCode.issue(10L, HASH, 1L, NOW);

		assertThat(code.getId()).isNull();
		assertThat(code.getPetId()).isEqualTo(10L);
		assertThat(code.getCodeHash()).isEqualTo(HASH);
		assertThat(code.getIssuerMemberId()).isEqualTo(1L);
		assertThat(code.getCreatedAt()).isEqualTo(NOW);
		assertThat(code.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
	}

	@Test
	@DisplayName("#133-S4 만료 직전까지만 쓸 수 있고, 만료 시각 정각부터는 쓸 수 없다 (DB의 expires_at > now와 같은 기준)")
	void usableOnlyBeforeExpiry() {
		PetInviteCode code = PetInviteCode.issue(10L, HASH, 1L, NOW);
		Instant expiresAt = code.getExpiresAt();

		assertThat(code.isUsableAt(NOW)).isTrue();
		assertThat(code.isUsableAt(expiresAt.minusNanos(1))).isTrue();
		assertThat(code.isUsableAt(expiresAt)).isFalse();
		assertThat(code.isUsableAt(expiresAt.plusSeconds(1))).isFalse();
	}

	@Test
	@DisplayName("#133 반려동물·코드 해시·발급자가 없으면 만들 수 없다")
	void issueRequiresValues() {
		assertThatThrownBy(() -> PetInviteCode.issue(null, HASH, 1L, NOW))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> PetInviteCode.issue(10L, null, 1L, NOW))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> PetInviteCode.issue(10L, " ", 1L, NOW))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> PetInviteCode.issue(10L, HASH, null, NOW))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("#133 restore는 저장된 값을 그대로 되살린다")
	void restoreKeepsStoredValues() {
		Instant expiresAt = NOW.plusSeconds(60);
		PetInviteCode code = PetInviteCode.restore(5L, 10L, HASH, 1L, expiresAt, NOW);

		assertThat(code.getId()).isEqualTo(5L);
		assertThat(code.getExpiresAt()).isEqualTo(expiresAt);
		assertThat(code.getCreatedAt()).isEqualTo(NOW);
	}
}
