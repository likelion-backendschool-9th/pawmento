package com.pawmento.family.adapter.in.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.pawmento.family.application.port.in.IssueInviteCodeCommand;
import com.pawmento.family.application.port.in.IssueInviteCodeUseCase;
import com.pawmento.family.application.port.in.JoinFamilyCommand;
import com.pawmento.family.application.port.in.JoinFamilyUseCase;
import com.pawmento.family.application.port.in.LeaveFamilyCommand;
import com.pawmento.family.application.port.in.LeaveFamilyUseCase;
import com.pawmento.family.application.port.in.ListFamilyQuery;
import com.pawmento.family.application.port.in.ListFamilyUseCase;
import com.pawmento.family.application.port.in.ListMyFamilyPetsQuery;
import com.pawmento.family.application.port.in.ListMyFamilyPetsUseCase;
import com.pawmento.family.application.port.in.RemoveFamilyMemberCommand;
import com.pawmento.family.application.port.in.RemoveFamilyMemberUseCase;
import com.pawmento.global.security.LoginMember;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;


@RestController
@RequestMapping("/families")
@RequiredArgsConstructor
public class FamilyController {

    private final IssueInviteCodeUseCase issueInviteCodeUseCase;

    private final JoinFamilyUseCase joinFamilyUseCase;

    private final ListFamilyUseCase listFamilyUseCase;

    private final ListMyFamilyPetsUseCase listMyFamilyPetsUseCase;

    private final RemoveFamilyMemberUseCase removeFamilyMemberUseCase;

    private final LeaveFamilyUseCase leaveFamilyUseCase;

    @PostMapping("/invite-codes")
    @ResponseStatus(HttpStatus.CREATED)
    InviteCodeResponse issueInviteCode(@LoginMember Long memberId, @Valid @RequestBody IssueInviteCodeRequest request) {
        return InviteCodeResponse.from(
                issueInviteCodeUseCase.issue(new IssueInviteCodeCommand(memberId, request.petId())));
    }

    @PostMapping("/members")
    JoinFamilyResponse join(@LoginMember Long memberId, @Valid @RequestBody JoinFamilyRequest request) {
        return JoinFamilyResponse.from(joinFamilyUseCase.join(new JoinFamilyCommand(memberId, request.code())));
    }

    @GetMapping
    FamilyResponse list(@LoginMember Long memberId, @Valid ListFamilyRequest request) {
        return FamilyResponse.from(listFamilyUseCase.list(new ListFamilyQuery(memberId, request.petId())));
    }

    @GetMapping("/pets")
    List<MyFamilyPetResponse> myFamilyPets(@LoginMember Long memberId) {
        return listMyFamilyPetsUseCase.list(new ListMyFamilyPetsQuery(memberId)).stream()
                .map(MyFamilyPetResponse::from)
                .toList();
    }

    @DeleteMapping("/members/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void leave(@LoginMember Long memberId, @Valid FamilyPetRequest request) {
        leaveFamilyUseCase.leave(new LeaveFamilyCommand(memberId, request.petId()));
    }

    @DeleteMapping("/members/{memberId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(@LoginMember Long requesterId, @PathVariable("memberId") Long targetMemberId,
            @Valid FamilyPetRequest request) {
        removeFamilyMemberUseCase.remove(new RemoveFamilyMemberCommand(requesterId, request.petId(), targetMemberId));
    }
}
