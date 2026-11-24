package com.pawmento.family.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InviteCodesTest {

	private final InviteCodes codes = new InviteCodes();

	@Test
	@DisplayName("#133-S2 코드는 헷갈리는 글자(0·O·1·I·L)가 없는 대문자·숫자 8자리다")
	void newCodeUsesSafeAlphabet() {
		Set<String> seen = new HashSet<>();
		for (int i = 0; i < 1_000; i++) {
			String code = codes.newCode();
			assertThat(code).matches("[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{8}");
			seen.add(code);
		}
		// 31^8 ≈ 8.5e11 가지라 1000개 안에서 겹칠 일은 사실상 없다
		assertThat(seen).hasSizeGreaterThan(990);
	}

	@Test
	@DisplayName("#133 해시는 SHA-256 소문자 16진수 64자이고 같은 코드면 늘 같다")
	void hashIsSha256Hex() {
		// 대문자 "ABC"의 SHA-256. 소문자 "abc"(ba7816bf…)와 다르다
		assertThat(codes.hash("ABC"))
				.isEqualTo("b5d4045c3f466fa91fe2cc6abe79232a1a57cdf104f7a26e716e0a1e2789df78");
		assertThat(codes.hash("K7Q2M9XA")).hasSize(64).matches("[0-9a-f]{64}");
		assertThat(codes.hash("K7Q2M9XA")).isEqualTo(codes.hash("K7Q2M9XA"));
	}

	@Test
	@DisplayName("#133-S3 앞뒤 공백과 소문자는 정리한 뒤 해시하므로, 사용자가 소문자로 입력해도 같은 코드다")
	void hashNormalizesInput() {
		assertThat(codes.hash("  k7q2m9xa ")).isEqualTo(codes.hash("K7Q2M9XA"));
		assertThat(codes.hash("K7Q2M9XB")).isNotEqualTo(codes.hash("K7Q2M9XA"));
	}
}
