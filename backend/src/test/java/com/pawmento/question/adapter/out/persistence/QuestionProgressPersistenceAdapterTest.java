package com.pawmento.question.adapter.out.persistence;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.pawmento.question.application.exception.StoredQuestionInvalidException;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QuestionProgressPersistenceAdapterTest {
  @Test
  @DisplayName("#31-S3 현재 포인터의 회차가 없으면 공통 저장 데이터 오류로 반환한다")
  void missingCurrentPrompt() {
    var prompts = mock(QuestionPromptJpaRepository.class);
    when(prompts.findById(5L)).thenReturn(Optional.empty());
    var adapter =
        new QuestionProgressPersistenceAdapter(
            mock(QuestionProgressJpaRepository.class), prompts, mock(EntityManager.class));
    assertThatThrownBy(() -> adapter.loadPrompt(5L))
        .isInstanceOf(StoredQuestionInvalidException.class)
        .hasCauseInstanceOf(IllegalStateException.class);
  }
}
