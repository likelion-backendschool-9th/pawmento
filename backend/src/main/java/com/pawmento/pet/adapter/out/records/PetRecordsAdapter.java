package com.pawmento.pet.adapter.out.records;

import com.pawmento.diary.application.port.in.HasDiaryEntriesQuery;
import com.pawmento.diary.application.port.in.HasDiaryEntriesUseCase;
import com.pawmento.pet.application.port.out.HasPetRecordsPort;
import com.pawmento.postcard.application.port.in.HasPostcardsQuery;
import com.pawmento.postcard.application.port.in.HasPostcardsUseCase;
import com.pawmento.question.application.port.in.HasQuestionAnswersQuery;
import com.pawmento.question.application.port.in.HasQuestionAnswersUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PetRecordsAdapter implements HasPetRecordsPort {
  private final HasDiaryEntriesUseCase diaries;
  private final HasQuestionAnswersUseCase answers;
  private final HasPostcardsUseCase postcards;

  @Override
  public boolean existsByPetId(Long petId) {
    return diaries.exists(new HasDiaryEntriesQuery(petId))
        || answers.exists(new HasQuestionAnswersQuery(petId))
        // 일기를 모두 지워도 엽서가 남을 수 있다.
        || postcards.exists(new HasPostcardsQuery(petId));
  }
}
