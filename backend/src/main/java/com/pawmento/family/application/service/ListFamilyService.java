package com.pawmento.family.application.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pawmento.family.application.exception.FamilyAccessDeniedException;
import com.pawmento.family.application.exception.FamilyPetNotFoundException;
import com.pawmento.family.application.port.in.ListFamilyQuery;
import com.pawmento.family.application.port.in.ListFamilyResult;
import com.pawmento.family.application.port.in.ListFamilyUseCase;
import com.pawmento.family.application.port.out.FamilyMemberProfile;
import com.pawmento.family.application.port.out.LoadMemberProfilesPort;
import com.pawmento.family.application.port.out.LoadPetFamilyMemberPort;
import com.pawmento.family.application.port.out.LoadPetOwnerPort;
import com.pawmento.family.domain.PetFamilyMember;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ListFamilyService implements ListFamilyUseCase {

    private final LoadPetOwnerPort loadPetOwnerPort;

    private final LoadPetFamilyMemberPort loadPetFamilyMemberPort;

    private final LoadMemberProfilesPort loadMemberProfilesPort;

    @Override
    @Transactional(readOnly = true)
    public ListFamilyResult list(ListFamilyQuery query) {
        Long ownerId = loadPetOwnerPort.loadOwnerId(query.petId()).orElseThrow(FamilyPetNotFoundException::new);
        List<PetFamilyMember> family = loadPetFamilyMemberPort.loadByPetId(query.petId());

        boolean isOwner = ownerId.equals(query.memberId());
        boolean isFamily = family.stream().anyMatch(member -> member.getMemberId().equals(query.memberId()));
        if (!isOwner && !isFamily) {
            throw new FamilyAccessDeniedException();
        }

        List<Long> memberIds = new ArrayList<>();
        memberIds.add(ownerId);
        family.forEach(member -> memberIds.add(member.getMemberId()));
        Map<Long, FamilyMemberProfile> profiles = loadMemberProfilesPort.loadProfiles(memberIds).stream()
                .collect(Collectors.toMap(FamilyMemberProfile::memberId, Function.identity()));

        FamilyMemberProfile ownerProfile = profiles.get(ownerId);
        ListFamilyResult.Owner owner = new ListFamilyResult.Owner(ownerId,
            ownerProfile == null ? null : ownerProfile.nickname(),
            ownerProfile == null ? null : ownerProfile.tag());
        List<ListFamilyResult.Family> members = family.stream()
                .map(member -> {
                    FamilyMemberProfile profile = profiles.get(member.getMemberId());
                    return new ListFamilyResult.Family(member.getMemberId(),
                            profile == null ? null : profile.nickname(),
                            profile == null ? null : profile.tag(),
                            member.getJoinedAt());
                })
                .toList();
        return new ListFamilyResult(owner, members);
    }
}
