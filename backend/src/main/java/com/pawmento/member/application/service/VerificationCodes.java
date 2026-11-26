package com.pawmento.member.application.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

@Component
public class VerificationCodes {
    private static final int BOUND = 1_000_000;
    private static final String ALGORITHM = "HmacSHA256";
    private static final String PURPOSE = "pawmento-email-verification";

    private final SecureRandom random = new SecureRandom();

    private final SecretKeySpec key;

    public VerificationCodes(@Value("${pawmento.auth.jwt-secret:}") String secret) {
        this.key = new SecretKeySpec(deriveKey(secret), ALGORITHM);
    }

    public String newCode() {
        return String.format("%06d", random.nextInt(BOUND));
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
            throw new IllegalArgumentException("인증번호를 해시할 수 없습니다.", e);
        }
    }
}
