package com.pawmento.diary.application.port.out;

public interface ExistsDiaryEntriesPort {
  boolean existsByPetId(Long petId);
}
