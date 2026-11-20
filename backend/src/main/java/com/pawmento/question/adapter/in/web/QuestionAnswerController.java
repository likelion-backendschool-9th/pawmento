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
  ResponseEntity<QuestionAnswerResponse.Created> create(
      @LoginMember Long member,
      @PathVariable Long promptId,
      HttpServletRequest request,
      @RequestBody JsonNode body) {
    var values = QuestionAnswerBody.parse(body, false);
    var result =
        create.create(
            new CreateQuestionAnswerCommand(
                member,
                new QuestionAnswerQuery(request.getQueryString(), "petId").petId(),
                promptId,
                values.content(),
                values.attachmentIds()));
    return ResponseEntity.status(201).body(QuestionAnswerResponse.Created.from(result));
  }

  @GetMapping("/answers")
  QuestionAnswerResponse.Page list(@LoginMember Long member, HttpServletRequest request) {
    return QuestionAnswerResponse.Page.from(
        list.list(
            new QuestionAnswerQuery(
                    request.getQueryString(),
                    "petId",
                    "mode",
                    "promptId",
                    "authorMemberId",
                    "from",
                    "to",
                    "page",
                    "size")
                .list(member)));
  }

  @GetMapping("/answers/{answerId}")
  QuestionAnswerResponse get(
      @LoginMember Long member, @PathVariable Long answerId, HttpServletRequest request) {
    return QuestionAnswerResponse.from(
        get.get(
            new GetQuestionAnswerQuery(
                member,
                new QuestionAnswerQuery(request.getQueryString(), "petId").petId(),
                answerId)));
  }

  @PatchMapping("/answers/{answerId}")
  QuestionAnswerResponse edit(
      @LoginMember Long member,
      @PathVariable Long answerId,
      HttpServletRequest request,
      @RequestBody JsonNode body) {
    var values = QuestionAnswerBody.parse(body, true);
    return QuestionAnswerResponse.from(
        edit.edit(
            new EditQuestionAnswerCommand(
                member,
                new QuestionAnswerQuery(request.getQueryString(), "petId").petId(),
                answerId,
                values.content(),
                values.version(),
                values.attachmentIds())));
  }

  @DeleteMapping("/answers/{answerId}")
  ResponseEntity<Void> delete(
      @LoginMember Long member, @PathVariable Long answerId, HttpServletRequest request) {
    var query = new QuestionAnswerQuery(request.getQueryString(), "petId", "version");
    delete.delete(
        new DeleteQuestionAnswerCommand(member, query.petId(), answerId, query.version()));
    return ResponseEntity.noContent().build();
  }
}
