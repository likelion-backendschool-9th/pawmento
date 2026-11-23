package com.pawmento.question.application.service;

import com.pawmento.question.application.port.in.*;
import com.pawmento.question.application.port.out.*;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
@Transactional(
    readOnly = true,
    isolation = Isolation.REPEATABLE_READ,
    propagation = Propagation.REQUIRES_NEW)
public class ListAdminQuestionsService implements ListAdminQuestionsUseCase {
  private final CheckQuestionAdminAccessPort access;
  private final LoadQuestionsPort questions;

  public AdminQuestionsPage list(ListAdminQuestionsQuery query) {
    access.requireAdmin(query.memberId());
    var filter = query.filter();
    long count = questions.count(filter);
    var items = (long) filter.page() * filter.size() >= count
        ? List.<AdminQuestionResult>of()
        : questions.list(filter).stream().map(AdminQuestionResult::from).toList();
    return new AdminQuestionsPage(items, filter.page(), filter.size(), count,
        count / filter.size() + (count % filter.size() == 0 ? 0 : 1));
  }
}
