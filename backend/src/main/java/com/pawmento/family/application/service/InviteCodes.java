package com.pawmento.family.application.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class InviteCodes {

    private static final char[] ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int LENGTH = 8;
    private static final String ALGORITHM = "HmacSHA256";
    // JWT 서명 키를 그대로 쓰지 않고, 이 이름을 섞어 초대 코드 전용 키를 따로 만든다
    private static final String PURPOSE = "pawmento-invite-code";

    private final SecureRandom random = new SecureRandom();

    private final SecretKeySpec key;

    public InviteCodes(@Value("${pawmento.auth.jwt-secret:}") String secret) {
        this.key = new SecretKeySpec(deriveKey(secret), ALGORITHM);
    }

    public String newCode() {
        char[] chars = new char[LENGTH];
        for (int i = 0; i < LENGTH; i++) {
            chars[i] = ALPHABET[random.nextInt(ALPHABET.length)];
        }
        return new String(chars);
    }

    // 서버만 아는 키로 해시한다. DB가 유출돼도 키 없이는 코드를 되찾을 수 없다
    public String hash(String code) {
        String normalized = code.strip().toUpperCase(Locale.ROOT);
        return HexFormat.of().formatHex(hmac(key, normalized.getBytes(StandardCharsets.UTF_8)));
    }

    private byte[] deriveKey(String secret) {
        if (secret == null || secret.isBlank()) {
            // 로컬처럼 JWT_SECRET이 없으면 임시 키를 쓴다. 재시작하면 기존 코드는 쓸 수 없다
            byte[] temporary = new byte[32];
            random.nextBytes(temporary);
            return temporary;
        }
        SecretKeySpec master = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
        return hmac(master, PURPOSE.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] hmac(SecretKeySpec key, byte[] input) {
        try {
            // Mac은 여러 스레드가 같이 쓰면 안 되므로 부를 때마다 만든다
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return mac.doFinal(input);
        }
        catch (GeneralSecurityException e) {
            throw new IllegalStateException("초대 코드를 해시할 수 없습니다.", e);
        }
    }
}