package com.pawmento.postcard.application.service;

import com.pawmento.postcard.application.port.in.*;
import com.pawmento.postcard.application.port.out.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ListPostcardsService implements ListPostcardsUseCase {
  private final LoadPostcardPetPort pets;
  private final LoadPostcardsPort load;

  @Override
  @Transactional(readOnly = true)
  public ListPostcardsResult list(ListPostcardsQuery q) {
    pets.loadOwned(q.memberId(), q.petId());
    // 한 장 더 읽어 다음 페이지가 있는지 안다.
    var rows = load.findByPet(q.petId(), q.cursor(), q.size() + 1);
    boolean more = rows.size() > q.size();
    var page = more ? rows.subList(0, q.size()) : rows;
    return new ListPostcardsResult(
        page.stream().map(GetPostcardResult::from).toList(),
        more ? page.get(page.size() - 1).getId() : null);
  }
}
