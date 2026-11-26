package com.pawmento.family.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pawmento.family.application.exception.FamilyAccessDeniedException;
import com.pawmento.family.application.exception.FamilyMemberNotFoundException;
import com.pawmento.family.application.exception.FamilyPetNotFoundException;
import com.pawmento.family.application.exception.OwnerNotRemovableException;
import com.pawmento.family.application.port.in.LeaveFamilyCommand;
import com.pawmento.family.application.port.in.LeaveFamilyUseCase;
import com.pawmento.family.application.port.in.RemoveFamilyMemberCommand;
import com.pawmento.family.application.port.in.RemoveFamilyMemberUseCase;
import com.pawmento.family.application.port.out.DeletePetFamilyMemberPort;
import com.pawmento.family.application.port.out.DeletePetInviteCodesByIssuerPort;
import com.pawmento.family.application.port.out.LockPetOwnerPort;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RemoveFamilyMemberService implements RemoveFamilyMemberUseCase, LeaveFamilyUseCase {

    private final LockPetOwnerPort lockPetOwnerPort;

    private final DeletePetFamilyMemberPort deletePetFamilyMemberPort;

    private final DeletePetInviteCodesByIssuerPort deletePetInviteCodesByIssuerPort;

    // 소유주가 가족을 내보낸다
    @Override
    @Transactional
    public void remove(RemoveFamilyMemberCommand command) {
        Long ownerId = lockPetOwnerPort.lockOwnerId(command.petId()).orElseThrow(FamilyPetNotFoundException::new);
        if (!ownerId.equals(command.requesterId())) {
            throw new FamilyAccessDeniedException();
        }
        if (ownerId.equals(command.targetMemberId())) {
            throw new OwnerNotRemovableException();
        }
        if (!deletePetFamilyMemberPort.delete(command.petId(), command.targetMemberId())) {
            throw new FamilyMemberNotFoundException();
        }
        deletePetInviteCodesByIssuerPort.deleteByIssuer(command.petId(), command.targetMemberId());
    }

    // 가족이 스스로 나간다
    @Override
    @Transactional
    public void leave(LeaveFamilyCommand command) {
        Long ownerId = lockPetOwnerPort.lockOwnerId(command.petId()).orElseThrow(FamilyPetNotFoundException::new);
        if (ownerId.equals(command.memberId())) {
            throw new OwnerNotRemovableException();
        }
        if (!deletePetFamilyMemberPort.delete(command.petId(), command.memberId())) {
            throw new FamilyAccessDeniedException();
        }
        deletePetInviteCodesByIssuerPort.deleteByIssuer(command.petId(), command.memberId());
    }

}