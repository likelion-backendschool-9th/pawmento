package com.pawmento.question.adapter.in.web;

import com.pawmento.question.application.port.in.*;

record QuestionPromptResponse(
    QuestionPromptHistoryItem prompt, QuestionAnswerResponse.Page answers) {
  static QuestionPromptResponse from(GetQuestionPromptResult result) {
    return new QuestionPromptResponse(
        result.prompt(), QuestionAnswerResponse.Page.from(result.answers()));
  }
}
