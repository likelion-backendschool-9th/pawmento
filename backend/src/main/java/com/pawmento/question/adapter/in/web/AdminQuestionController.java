package com.pawmento.question.adapter.in.web;

import com.pawmento.global.security.LoginMember;
import com.pawmento.question.application.port.in.CreateAdminQuestionCommand;
import com.pawmento.question.application.port.in.CreateAdminQuestionUseCase;
import com.pawmento.question.application.port.in.GetAdminQuestionQuery;
import com.pawmento.question.application.port.in.GetAdminQuestionUseCase;
import com.pawmento.question.application.port.in.ListAdminQuestionsUseCase;
import com.pawmento.question.application.port.in.UpdateAdminQuestionCommand;
import com.pawmento.question.application.port.in.UpdateAdminQuestionUseCase;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/admin/questions")
@RequiredArgsConstructor
public class AdminQuestionController {
  private final ListAdminQuestionsUseCase list;
  private final GetAdminQuestionUseCase get;
  private final CreateAdminQuestionUseCase create;
  private final UpdateAdminQuestionUseCase update;

  @PatchMapping("/{questionId}")
  AdminQuestionResponse update(@LoginMember Long member, @PathVariable Long questionId,
      @RequestBody JsonNode body) {
    var request = UpdateAdminQuestionRequest.parse(body);
    return AdminQuestionResponse.from(update.update(
        new UpdateAdminQuestionCommand(
            member, questionId, request.content(), request.enabled(), request.version())));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  AdminQuestionResponse create(@LoginMember Long member, @RequestBody JsonNode body) {
    var request = CreateAdminQuestionRequest.parse(body);
    return AdminQuestionResponse.from(
        create.create(new CreateAdminQuestionCommand(member, request.mode(), request.content())));
  }

  @GetMapping
  AdminQuestionResponse.Page list(@LoginMember Long member, HttpServletRequest request) {
    return AdminQuestionResponse.Page.from(
        list.list(QuestionAdminQuery.parse(member, request.getQueryString())));
  }

  @GetMapping("/{questionId}")
  AdminQuestionResponse get(@LoginMember Long member, @PathVariable Long questionId) {
    return AdminQuestionResponse.from(get.get(new GetAdminQuestionQuery(member, questionId)));
  }
}
