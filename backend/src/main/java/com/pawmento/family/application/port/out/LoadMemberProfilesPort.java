package com.pawmento.family.application.port.out;

import java.util.List;

public interface LoadMemberProfilesPort {

    // 없는 회원은 결과에서 빠진다
    List<FamilyMemberProfile> loadProfiles(List<Long> memberIds);
}