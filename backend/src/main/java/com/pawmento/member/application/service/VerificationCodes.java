package com.pawmento.member.application.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;
import java.util.random.RandomGenerator;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class VerificationCodes {
    private static final int BOUND = 1_000_000;
    private static final String ALGORITHM = "HmacSHA256";
    private static final String PURPOSE = "pawmento-email-verification";

	private final RandomGenerator random;

	private final SecretKeySpec key;

	public VerificationCodes(String secret) {
		this(secret, new SecureRandom());
	}

    // 테스트에서 뽑히는 번호를 정하려고 둔다
	VerificationCodes(String secret, RandomGenerator random) {
		this.random = random;
		this.key = new SecretKeySpec(deriveKey(secret), ALGORITHM);
	}

	// 서버 언어 설정이 바뀌어도 항상 0~9 숫자 6자리다
	public String newCode() {
		return String.format(Locale.ROOT, "%06d", random.nextInt(BOUND));
	}

    // 다시 받을 때 직전 번호와 같은 번호가 뽑히면 다시 뽑는다 (#279-S3)
	public String newCodeOtherThan(String previousCodeHash) {
		String code = newCode();
		while (hash(code).equals(previousCodeHash)) {
			code = newCode();
		}
		return code;
	}

    public String hash(String code) {
        return HexFormat.of().formatHex(hmac(key, code.strip().getBytes(StandardCharsets.UTF_8)));
    }

    private byte[] deriveKey(String secret) {
        if (secret == null || secret.isBlank()) {
            byte[] temporary = new byte[32];
            random.nextBytes(temporary);
            return temporary;
        }
        SecretKeySpec master = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
        return hmac(master, PURPOSE.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] hmac(SecretKeySpec key, byte[] input) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return mac.doFinal(input);
        }
        catch (GeneralSecurityException e) {
            throw new IllegalStateException("인증번호를 해시할 수 없습니다.", e);
        }
    }
}
