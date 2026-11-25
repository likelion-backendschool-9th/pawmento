package com.pawmento.family.adapter.in.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.pawmento.family.application.port.in.IssueInviteCodeCommand;
import com.pawmento.family.application.port.in.IssueInviteCodeUseCase;
import com.pawmento.family.application.port.in.JoinFamilyCommand;
import com.pawmento.family.application.port.in.JoinFamilyUseCase;
import com.pawmento.family.application.port.in.ListFamilyQuery;
import com.pawmento.family.application.port.in.ListFamilyUseCase;
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
}
