package com.pawmento.family.adapter.out.member;

import java.util.List;

import org.springframework.stereotype.Component;

import com.pawmento.family.application.port.out.FamilyMemberProfile;
import com.pawmento.family.application.port.out.LoadMemberProfilesPort;
import com.pawmento.member.application.port.in.ListMemberProfilesQuery;
import com.pawmento.member.application.port.in.ListMemberProfilesUseCase;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
class MemberProfileAdapter implements LoadMemberProfilesPort {

    private final ListMemberProfilesUseCase listMemberProfiles;

    @Override
    public List<FamilyMemberProfile> loadProfiles(List<Long> memberIds) {
        return listMemberProfiles.list(new ListMemberProfilesQuery(memberIds)).stream()
                .map(profile -> new FamilyMemberProfile(profile.memberId(), profile.nickname(), profile.tag()))
                .toList();
    }

}
