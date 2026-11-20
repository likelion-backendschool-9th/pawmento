package com.pawmento.pet.adapter.out.records;

import com.pawmento.diary.application.port.in.HasDiaryEntriesQuery;
import com.pawmento.diary.application.port.in.HasDiaryEntriesUseCase;
import com.pawmento.pet.application.port.out.HasPetRecordsPort;
import com.pawmento.question.application.port.in.HasQuestionAnswersQuery;
import com.pawmento.question.application.port.in.HasQuestionAnswersUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PetRecordsAdapter implements HasPetRecordsPort {
  private final HasDiaryEntriesUseCase diaries;
  private final HasQuestionAnswersUseCase answers;

  @Override
  public boolean existsByPetId(Long petId) {
    return diaries.exists(new HasDiaryEntriesQuery(petId))
        || answers.exists(new HasQuestionAnswersQuery(petId));
  }
}
