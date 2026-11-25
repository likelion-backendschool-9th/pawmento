package com.pawmento.family.application.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pawmento.family.application.port.in.ListMyFamilyPetsQuery;
import com.pawmento.family.application.port.in.ListMyFamilyPetsUseCase;
import com.pawmento.family.application.port.in.MyFamilyPetResult;
import com.pawmento.family.application.port.out.LoadPetFamilyMemberPort;
import com.pawmento.family.application.port.out.LoadPetSummaryPort;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ListMyFamilyPetsService implements ListMyFamilyPetsUseCase {

    private final LoadPetFamilyMemberPort loadPetFamilyMemberPort;

    private final LoadPetSummaryPort loadPetSummaryPort;

    @Override
    @Transactional(readOnly = true)
    public List<MyFamilyPetResult> list(ListMyFamilyPetsQuery query) {
        return loadPetFamilyMemberPort.loadByMemberId(query.memberId()).stream()
                .map(member -> loadPetSummaryPort.loadSummary(member.getPetId())
                        .map(pet -> new MyFamilyPetResult(pet.petId(), pet.name(), pet.tag(), member.getJoinedAt())))
                .flatMap(Optional::stream)
                .toList();
    }
}
