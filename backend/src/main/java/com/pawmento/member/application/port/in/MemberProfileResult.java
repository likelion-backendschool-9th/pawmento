package com.pawmento.member.application.port.in;

import com.pawmento.member.domain.Member;

public record MemberProfileResult(Long memberId, String nickname, String tag) {

    public static MemberProfileResult from(Member member) {
        return new MemberProfileResult(member.getId(), member.getNickname(), member.getTag());
    }

}
