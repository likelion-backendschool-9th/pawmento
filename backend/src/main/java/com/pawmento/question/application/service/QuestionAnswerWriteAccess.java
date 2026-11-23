package com.pawmento.question.application.service;

import com.pawmento.question.application.port.out.CheckQuestionPetWriteAccessPort;
import com.pawmento.question.application.port.out.QuestionAnswerStorePort;
import com.pawmento.question.domain.QuestionAnswer;
import com.pawmento.question.domain.exception.QuestionAnswerNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 수정·삭제 트랜잭션 안에서 권한 확인과 답변 행 잠금을 같은 순서로 수행한다. */
@Component
@RequiredArgsConstructor
class QuestionAnswerWriteAccess {
  private final CheckQuestionPetWriteAccessPort access;
  private final QuestionAnswerStorePort answers;

  QuestionAnswer requireEditable(Long memberId, Long petId, Long answerId, long version) {
    var mode = access.currentModeForWrite(memberId, petId);
    var answer = answers.lock(petId, answerId).orElseThrow(QuestionAnswerNotFoundException::new);
    answer.requireEditable(memberId, mode, version);
    return answer;
  }
}
