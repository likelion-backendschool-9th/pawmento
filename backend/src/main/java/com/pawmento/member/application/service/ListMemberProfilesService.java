package com.pawmento.member.application.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pawmento.member.application.port.in.ListMemberProfilesQuery;
import com.pawmento.member.application.port.in.ListMemberProfilesUseCase;
import com.pawmento.member.application.port.in.MemberProfileResult;
import com.pawmento.member.application.port.out.LoadMemberPort;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ListMemberProfilesService implements ListMemberProfilesUseCase {

    private final LoadMemberPort loadMemberPort;

    @Override
    @Transactional(readOnly = true)
    public List<MemberProfileResult> list(ListMemberProfilesQuery query) {
        if (query.memberIds().isEmpty()) {
            return List.of();
        }
        return loadMemberPort.loadAllByIds(query.memberIds()).stream()
                .map(MemberProfileResult::from)
                .toList();
    }

}
