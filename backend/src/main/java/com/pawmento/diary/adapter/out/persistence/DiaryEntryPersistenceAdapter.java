package com.pawmento.diary.adapter.out.persistence;

import com.pawmento.diary.application.exception.*;
import com.pawmento.diary.application.port.out.*;
import com.pawmento.diary.domain.DiaryEntry;
import com.pawmento.diary.domain.DiaryListFilter;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.postgresql.util.PSQLException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DiaryEntryPersistenceAdapter
    implements LoadDiaryEntryPort,
        SaveDiaryEntryPort,
        DeleteDiaryEntryPort,
        ExistsDiaryEntriesPort,
        LoadDiaryEntriesPort,
        CountDiaryEntriesPort,
        LoadDiaryCalendarPort {
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

  @Override
  public List<DiaryEntry> entries(Long petId, DiaryListFilter filter) {
    return read(
        () ->
            rows
                .findPage(petId, filter.fromDate(), filter.toDate(), filter.size(), filter.offset())
                .stream()
                .map(DiaryEntryJpaEntity::toDomain)
                .toList());
  }

  @Override
  public long count(Long petId, DiaryListFilter filter) {
    return read(() -> rows.countEntries(petId, filter.fromDate(), filter.toDate()));
  }

  @Override
  public List<DiaryDayCount> days(Long petId, LocalDate from, LocalDate before) {
    return read(
        () ->
            rows.countDays(petId, from, before).stream()
                .map(day -> new DiaryDayCount(day.getDate(), day.getTotal()))
                .toList());
  }

  private static <T> T read(Supplier<T> query) {
    try {
      return query.get();
    } catch (DataAccessResourceFailureException | TransientDataAccessResourceException failure) {
      throw new DiaryPersistenceUnavailableException(failure);
    }
  }
}
