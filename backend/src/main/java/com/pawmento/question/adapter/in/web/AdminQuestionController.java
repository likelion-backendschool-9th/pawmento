package com.pawmento.question.adapter.in.web;

import com.pawmento.global.security.LoginMember;
import com.pawmento.question.application.port.in.GetAdminQuestionQuery;
import com.pawmento.question.application.port.in.GetAdminQuestionUseCase;
import com.pawmento.question.application.port.in.ListAdminQuestionsUseCase;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/questions")
@RequiredArgsConstructor
public class AdminQuestionController {
  private final ListAdminQuestionsUseCase list;
  private final GetAdminQuestionUseCase get;

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
