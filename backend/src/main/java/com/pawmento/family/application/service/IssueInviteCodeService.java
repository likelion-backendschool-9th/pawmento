package com.pawmento.family.application.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;

import com.pawmento.family.application.exception.FamilyAccessDeniedException;
import com.pawmento.family.application.exception.FamilyPetNotFoundException;
import com.pawmento.family.application.port.in.IssueInviteCodeCommand;
import com.pawmento.family.application.port.in.IssueInviteCodeResult;
import com.pawmento.family.application.port.in.IssueInviteCodeUseCase;
import com.pawmento.family.application.port.out.DeleteExpiredPetInviteCodesPort;
import com.pawmento.family.application.port.out.LoadPetFamilyMemberPort;
import com.pawmento.family.application.port.out.LockPetOwnerPort;
import com.pawmento.family.application.port.out.SavePetInviteCodePort;
import com.pawmento.family.domain.PetInviteCode;

import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class IssueInviteCodeService implements IssueInviteCodeUseCase {

    private final LockPetOwnerPort lockPetOwnerPort;

    private final LoadPetFamilyMemberPort loadPetFamilyMemberPort;

    private final SavePetInviteCodePort savePetInviteCodePort;

    private final DeleteExpiredPetInviteCodesPort deleteExpiredPetInviteCodesPort;

    private final InviteCodes inviteCodes;

    private final Clock clock;

    @Override
    @Transactional
    public IssueInviteCodeResult issue(IssueInviteCodeCommand command) {
        Long ownerId = lockPetOwnerPort.lockOwnerId(command.petId()).orElseThrow(FamilyPetNotFoundException::new);
        boolean isOwner = ownerId.equals(command.memberId());
        if (!isOwner && !loadPetFamilyMemberPort.existsByPetIdAndMemberId(command.petId(), command.memberId())) {
            throw new FamilyAccessDeniedException();
        }
        Instant now = Instant.now(clock);
        deleteExpiredPetInviteCodesPort.deleteExpired(command.petId(), now);

        String code = inviteCodes.newCode();
        PetInviteCode saved = savePetInviteCodePort.save(
                PetInviteCode.issue(command.petId(), inviteCodes.hash(code), command.memberId(), Instant.now(clock)));
        return new IssueInviteCodeResult(code, saved.getExpiresAt());
    }

}
