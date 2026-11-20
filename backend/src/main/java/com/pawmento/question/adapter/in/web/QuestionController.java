package com.pawmento.question.adapter.in.web;

import com.pawmento.global.security.LoginMember;
import com.pawmento.question.application.port.in.CurrentQuestionPromptResult;
import com.pawmento.question.application.port.in.EnsureCurrentQuestionPromptCommand;
import com.pawmento.question.application.port.in.EnsureCurrentQuestionPromptUseCase;
import com.pawmento.question.application.port.in.GetCurrentQuestionPromptQuery;
import com.pawmento.question.application.port.in.GetCurrentQuestionPromptUseCase;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/questions")
@RequiredArgsConstructor
public class QuestionController {
  private final EnsureCurrentQuestionPromptUseCase ensure;
  private final GetCurrentQuestionPromptUseCase get;

  @GetMapping("/prompts/current")
  CurrentQuestionPromptResult current(@LoginMember Long memberId, HttpServletRequest request) {
    return get.get(
        new GetCurrentQuestionPromptQuery(
            memberId, QuestionPetQuery.petId(request.getQueryString())));
  }

  @PostMapping("/prompts/current")
  ResponseEntity<CurrentQuestionPromptResult> ensure(
      @LoginMember Long memberId, HttpServletRequest request) {
    var result =
        ensure.ensure(
            new EnsureCurrentQuestionPromptCommand(
                memberId, QuestionPetQuery.petId(request.getQueryString())));
    return ResponseEntity.status(result.created() ? 201 : 200).body(result.current());
  }
}
