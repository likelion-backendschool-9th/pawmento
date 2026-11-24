package com.pawmento.member.application.service;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

import com.pawmento.member.application.port.out.ExistsMemberPort;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MemberTagGenerator {

    private static final int TAG_BOUND = 10_000;
    private static final int MAX_ATTEMPTS = 32;

    private final ExistsMemberPort existsMemberPort;
    private final SecureRandom random = new SecureRandom();

    public String next(String nickname) {
        return next(nickname, null);
    }

    public String next(String nickname, String previousTag) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String tag = String.format("%04d", random.nextInt(TAG_BOUND));
            if (tag.equals(previousTag)) {
                continue;
            }
            if (!existsMemberPort.existsByNicknameAndTag(nickname, tag)) {
                return tag;
            }
        }
        throw new IllegalStateException("회원 태그를 발급하지 못했습니다.");
    }
}
