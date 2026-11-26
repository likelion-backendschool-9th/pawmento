package com.pawmento.member.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.random.RandomGenerator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VerificationCodesTest {

	@Test
	@DisplayName("#279-S1 인증번호는 항상 앞자리 0을 포함한 6자리 숫자이고, 매번 같은 값만 나오지 않는다")
	void newCodeIsSixDigits() {
		VerificationCodes codes = new VerificationCodes("test-secret");
		Set<String> seen = new HashSet<>();

		for (int i = 0; i < 1_000; i++) {
			String code = codes.newCode();
			assertThat(code).matches("\\d{6}");
			seen.add(code);
		}
		assertThat(seen).hasSizeGreaterThan(900);
	}

	@Test
	@DisplayName("#279-S5 같은 번호는 같은 해시이고, 앞뒤 공백은 지운 뒤 해시한다")
	void sameCodeSameHash() {
		VerificationCodes codes = new VerificationCodes("test-secret");

		assertThat(codes.hash("123456")).isEqualTo(codes.hash("123456"));
		assertThat(codes.hash(" 123456 ")).isEqualTo(codes.hash("123456"));
		assertThat(codes.hash("123457")).isNotEqualTo(codes.hash("123456"));
		assertThat(codes.hash("123456")).hasSize(64).matches("[0-9a-f]{64}");
	}

	@Test
	@DisplayName("#279-S1 해시는 비밀키에서 인증번호 전용 키를 만들어 HMAC-SHA256한 값이다")
	void hashUsesDerivedKey() {
		// 키 = HMAC("test-secret", "pawmento-email-verification"), 값 = HMAC(키, "123456")
		assertThat(new VerificationCodes("test-secret").hash("123456"))
				.isEqualTo("b766dbcbe1184c1528f548d72be07a3d0cb563f83d90879c7d4da655062983c7");
	}

	@Test
	@DisplayName("#279-S1 비밀키가 다르면 해시가 다르고, 초대 코드 키와도 섞이지 않는다")
	void differentSecretDifferentHash() {
		String hash = new VerificationCodes("test-secret").hash("123456");

		assertThat(new VerificationCodes("other-secret").hash("123456")).isNotEqualTo(hash);
		// 같은 비밀키·같은 입력을 초대 코드 전용 키("pawmento-invite-code")로 해시한 값과는 다르다
		assertThat(hash).isNotEqualTo("9cef5a926a927af8a21553bcd4286eb1c85934eb9dfaff14213dbdda877256ba");
	}

	@Test
	@DisplayName("#279-S1 비밀키가 없으면 임시 키를 써서, 서로 다른 인스턴스의 해시가 다르다")
	void blankSecretUsesTemporaryKey() {
		VerificationCodes first = new VerificationCodes("");
		VerificationCodes second = new VerificationCodes(null);

		assertThat(first.hash("123456")).isEqualTo(first.hash("123456"));
		assertThat(first.hash("123456")).isNotEqualTo(second.hash("123456"));
	}

	@Test
	@DisplayName("#279-S1 서버 언어가 아랍어(ar-EG)여도 번호는 0~9 숫자 6자리다")
	void newCodeIgnoresDefaultLocale() {
		Locale original = Locale.getDefault();
		try {
			Locale.setDefault(Locale.forLanguageTag("ar-EG"));
			VerificationCodes codes = new VerificationCodes("test-secret", scripted(42, 999_999));

			assertThat(codes.newCode()).isEqualTo("000042");
			assertThat(codes.newCode()).isEqualTo("999999").matches("[0-9]{6}");
		}
		finally {
			Locale.setDefault(original);
		}
	}

	@Test
	@DisplayName("#279-S3 다시 받을 때 직전 번호가 또 뽑히면 다시 뽑아, 직전 번호와 다른 번호를 준다")
	void newCodeOtherThanSkipsPreviousCode() {
		VerificationCodes codes = new VerificationCodes("test-secret", scripted(123_456, 123_456, 654_321));

		assertThat(codes.newCodeOtherThan(codes.hash("123456"))).isEqualTo("654321");
	}

	@Test
	@DisplayName("#279-S1 처음 보낼 때처럼 직전 번호가 없으면 처음 뽑은 번호를 준다")
	void newCodeOtherThanWithoutPrevious() {
		VerificationCodes codes = new VerificationCodes("test-secret", scripted(123_456));

		assertThat(codes.newCodeOtherThan(null)).isEqualTo("123456");
	}

	// 정해 둔 순서대로 번호를 뽑는 가짜 난수
	private static RandomGenerator scripted(Integer... values) {
		Iterator<Integer> next = List.of(values).iterator();
		return new RandomGenerator() {
			@Override
			public long nextLong() {
				throw new UnsupportedOperationException();
			}

			@Override
			public int nextInt(int bound) {
				return next.next();
			}
		};
	}

}
