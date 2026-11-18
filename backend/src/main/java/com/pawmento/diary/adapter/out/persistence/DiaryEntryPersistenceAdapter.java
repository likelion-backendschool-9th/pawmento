package com.pawmento.diary.adapter.out.persistence;

import com.pawmento.diary.application.exception.*;
import com.pawmento.diary.application.port.out.*;
import com.pawmento.diary.domain.DiaryEntry;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.postgresql.util.PSQLException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DiaryEntryPersistenceAdapter
    implements LoadDiaryEntryPort,
        SaveDiaryEntryPort,
        DeleteDiaryEntryPort,
        ExistsDiaryEntriesPort {
  private final DiaryEntryJpaRepository rows;

  public Optional<DiaryEntry> findById(Long id) {
    return rows.findById(id).map(DiaryEntryJpaEntity::toDomain);
  }

  public boolean existsByPetId(Long petId) {
    return rows.existsByPetId(petId);
  }

  public DiaryEntry save(DiaryEntry e) {
    if (e.getId() == null) {
      try {
        return rows.saveAndFlush(DiaryEntryJpaEntity.from(e)).toDomain();
      } catch (DataIntegrityViolationException failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause())
          if (cause instanceof PSQLException sql
              && sql.getServerErrorMessage() != null
              && "fk_diary_entries_pet".equals(sql.getServerErrorMessage().getConstraint())
              && "23503".equals(sql.getSQLState())) throw new DiaryPetNotFoundException(failure);
        throw failure;
      }
    }
    if (rows.updateVersion(
            e.getId(), e.getVersion(), e.getContent(), e.getRecordedDate(), e.getUpdatedAt())
        != 1) throw new DiaryVersionConflictException();
    return DiaryEntry.restore(
        e.getId(),
        e.getPetId(),
        e.getCreatedBy(),
        e.getContent(),
        e.getRecordedDate(),
        e.getCreatedAt(),
        e.getUpdatedAt(),
        e.getVersion() + 1);
  }

  public void delete(Long id, Long version) {
    if (rows.deleteVersion(id, version) != 1) throw new DiaryVersionConflictException();
  }
}
