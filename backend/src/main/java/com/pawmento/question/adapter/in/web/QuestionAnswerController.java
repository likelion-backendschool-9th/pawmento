package com.pawmento.question.adapter.in.web;

import com.pawmento.global.security.LoginMember;
import com.pawmento.question.application.port.in.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/questions")
@RequiredArgsConstructor
public class QuestionAnswerController {
  private final CreateQuestionAnswerUseCase create;
  private final GetQuestionAnswerUseCase get;
  private final ListQuestionAnswersUseCase list;
  private final EditQuestionAnswerUseCase edit;
  private final DeleteQuestionAnswerUseCase delete;

  @PostMapping("/prompts/{promptId}/answers")
  ResponseEntity<CreateQuestionAnswerResult> create(
      @LoginMember Long member,
      @PathVariable Long promptId,
      HttpServletRequest request,
      @RequestBody JsonNode body) {
    var values = QuestionAnswerBody.parse(body, false);
    var result =
        create.create(
            new CreateQuestionAnswerCommand(
                member,
                new QuestionAnswerQuery(request.getQueryString()).petId(),
                promptId,
                values.content()));
    return ResponseEntity.status(201).body(result);
  }

  @GetMapping("/answers")
  QuestionAnswersPage list(@LoginMember Long member, HttpServletRequest request) {
    return list.list(new QuestionAnswerQuery(request.getQueryString()).list(member));
  }

  @GetMapping("/answers/{answerId}")
  QuestionAnswerResult get(
      @LoginMember Long member, @PathVariable Long answerId, HttpServletRequest request) {
    return get.get(
        new GetQuestionAnswerQuery(
            member, new QuestionAnswerQuery(request.getQueryString()).petId(), answerId));
  }

  @PatchMapping("/answers/{answerId}")
  QuestionAnswerResult edit(
      @LoginMember Long member,
      @PathVariable Long answerId,
      HttpServletRequest request,
      @RequestBody JsonNode body) {
    var values = QuestionAnswerBody.parse(body, true);
    return edit.edit(
        new EditQuestionAnswerCommand(
            member,
            new QuestionAnswerQuery(request.getQueryString()).petId(),
            answerId,
            values.content(),
            values.version()));
  }

  @DeleteMapping("/answers/{answerId}")
  ResponseEntity<Void> delete(
      @LoginMember Long member, @PathVariable Long answerId, HttpServletRequest request) {
    var query = new QuestionAnswerQuery(request.getQueryString());
    delete.delete(
        new DeleteQuestionAnswerCommand(member, query.petId(), answerId, query.version()));
    return ResponseEntity.noContent().build();
  }
}
