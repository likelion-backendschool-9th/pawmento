package com.pawmento.family.application.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;

import com.pawmento.family.application.exception.AlreadyFamilyException;
import com.pawmento.family.application.exception.InviteCodeNotFoundException;
import com.pawmento.family.application.port.in.JoinFamilyResult;
import com.pawmento.family.application.port.in.JoinFamilyUseCase;
import com.pawmento.family.application.port.in.JoinFamilyCommand;
import com.pawmento.family.application.port.out.ConsumePetInviteCodePort;
import com.pawmento.family.application.port.out.LoadPetFamilyMemberPort;
import com.pawmento.family.application.port.out.LoadPetInviteCodePort;
import com.pawmento.family.application.port.out.LoadPetSummaryPort;
import com.pawmento.family.application.port.out.LockPetOwnerPort;
import com.pawmento.family.application.port.out.PetSummary;
import com.pawmento.family.application.port.out.SavePetFamilyMemberPort;
import com.pawmento.family.domain.PetFamilyMember;
import com.pawmento.family.domain.PetInviteCode;

import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class JoinFamilyService implements JoinFamilyUseCase {

    private final InviteCodes inviteCodes;

    private final LoadPetInviteCodePort loadPetInviteCodePort;

    private final LockPetOwnerPort lockPetOwnerPort;

    private final LoadPetFamilyMemberPort loadPetFamilyMemberPort;

    private final ConsumePetInviteCodePort consumePetInviteCodePort;

    private final SavePetFamilyMemberPort savePetFamilyMemberPort;

    private final LoadPetSummaryPort loadPetSummaryPort;

    private final Clock clock;

    @Override
    @Transactional
    public JoinFamilyResult join(JoinFamilyCommand command) {
        String codeHash = inviteCodes.hash(command.code());

        PetInviteCode code = loadPetInviteCodePort.loadByCodeHash(codeHash)
                .orElseThrow(InviteCodeNotFoundException::new);
        Long petId = code.getPetId();

        Long ownerId = lockPetOwnerPort.lockOwnerId(petId).orElseThrow(InviteCodeNotFoundException::new);

        // 잠금을 기다리는 사이 코드가 폐기됐을 수 있어, 잠금 뒤에 다시 읽어 아직 쓸 수 있는지 확인한다.
        // 이 확인이 기존 멤버십 검사보다 먼저여야 폐기된 코드가 409가 아니라 404가 된다
        Instant now = Instant.now(clock);
        loadPetInviteCodePort.loadByCodeHash(codeHash)
                .filter(current -> current.isUsableAt(now))
                .orElseThrow(InviteCodeNotFoundException::new);

        boolean alreadyIn = ownerId.equals(command.memberId())
                || loadPetFamilyMemberPort.existsByPetIdAndMemberId(petId, command.memberId());
        if (alreadyIn) {
            throw new AlreadyFamilyException();
        }

        if (!consumePetInviteCodePort.consume(codeHash, now)) {
            throw new InviteCodeNotFoundException();
        }

        savePetFamilyMemberPort.save(PetFamilyMember.join(petId, command.memberId(), now));

        PetSummary pet = loadPetSummaryPort.loadSummary(petId)
                .orElseThrow(() -> new IllegalStateException("잠근 반려동물을 읽지 못했습니다: " + petId));
        return new JoinFamilyResult(petId, pet.name(), pet.tag());
    }

}
