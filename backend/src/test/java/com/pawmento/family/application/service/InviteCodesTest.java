package com.pawmento.family.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InviteCodesTest {

	private static final String SECRET = "test-secret";

	private final InviteCodes codes = new InviteCodes(SECRET);

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
	@DisplayName("#133 해시는 서버 키로 만든 HMAC-SHA256(16진수 64자)이고, 같은 키·같은 코드면 늘 같다")
	void hashIsHmacWithDerivedKey() {
		// 키 = HMAC(SECRET, "pawmento-invite-code"), 값 = HMAC(키, "ABC"). 파이썬 hmac으로 따로 계산한 값
		assertThat(codes.hash("ABC"))
				.isEqualTo("9e212b5f23b2cb29c856bafa75c746987f8245d0e623464dc8af1776e10d22ed");
		assertThat(codes.hash("K7Q2M9XA")).matches("[0-9a-f]{64}");
		assertThat(codes.hash("K7Q2M9XA")).isEqualTo(new InviteCodes(SECRET).hash("K7Q2M9XA"));
	}

	@Test
	@DisplayName("#133 비밀값 없는 SHA-256과 다르고, 키가 다르면 해시도 달라서 DB만으로는 코드를 되찾을 수 없다")
	void hashDependsOnServerKey() {
		// 대문자 "ABC"의 그냥 SHA-256
		assertThat(codes.hash("ABC"))
				.isNotEqualTo("b5d4045c3f466fa91fe2cc6abe79232a1a57cdf104f7a26e716e0a1e2789df78");
		assertThat(new InviteCodes("other-secret").hash("K7Q2M9XA")).isNotEqualTo(codes.hash("K7Q2M9XA"));
	}

	@Test
	@DisplayName("#133 JWT_SECRET이 비어 있으면 임시 키를 만들어, 실행마다 해시가 달라진다")
	void blankSecretUsesTemporaryKey() {
		InviteCodes first = new InviteCodes("");
		InviteCodes second = new InviteCodes("");

		assertThat(first.hash("K7Q2M9XA")).matches("[0-9a-f]{64}");
		assertThat(first.hash("K7Q2M9XA")).isEqualTo(first.hash("K7Q2M9XA"));
		assertThat(first.hash("K7Q2M9XA")).isNotEqualTo(second.hash("K7Q2M9XA"));
	}

	@Test
	@DisplayName("#133-S3 앞뒤 공백과 소문자는 정리한 뒤 해시하므로, 사용자가 소문자로 입력해도 같은 코드다")
	void hashNormalizesInput() {
		assertThat(codes.hash("  k7q2m9xa ")).isEqualTo(codes.hash("K7Q2M9XA"));
		assertThat(codes.hash("K7Q2M9XB")).isNotEqualTo(codes.hash("K7Q2M9XA"));
	}
}
