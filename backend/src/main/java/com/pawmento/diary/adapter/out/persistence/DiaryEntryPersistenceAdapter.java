package com.pawmento.diary.adapter.out.persistence;

import com.pawmento.diary.application.exception.*;
import com.pawmento.diary.application.port.out.*;
import com.pawmento.diary.domain.DiaryEntry;
import com.pawmento.diary.domain.DiaryListFilter;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.postgresql.util.PSQLException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class DiaryEntryPersistenceAdapter
    implements LoadDiaryEntryPort,
        SaveDiaryEntryPort,
        DeleteDiaryEntryPort,
        ExistsDiaryEntriesPort,
        LoadDiaryEntriesPort,
        CountDiaryEntriesPort,
        LoadDiaryCalendarPort,
        DiaryAttachmentsPort {
  private final DiaryEntryJpaRepository rows;
  private final DiaryAttachmentJpaRepository attachmentRows;
  @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;

  public Optional<DiaryEntry> findForUpdate(Long id) {
    try {
      return read(
          () ->
              rows.findForUpdate(id)
                  .map(
                      row -> {
                        entityManager.refresh(
                            row, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
                        return row.toDomain();
                      }));
    } catch (org.springframework.dao.OptimisticLockingFailureException
        | jakarta.persistence.OptimisticLockException failure) {
      throw new DiaryVersionConflictException(failure);
    }
  }

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
    var location = e.getLocationIntent();
    if (rows.updateVersion(
            e.getId(),
            e.getVersion(),
            e.getContent(),
            e.getRecordedDate(),
            e.getUpdatedAt(),
            location == null ? null : location.mode(),
            location == null ? null : location.latitude(),
            location == null ? null : location.longitude(),
            location == null ? null : location.placeName())
        != 1) throw new DiaryVersionConflictException();
    return DiaryEntry.restore(
        e.getId(),
        e.getPetId(),
        e.getCreatedBy(),
        e.getContent(),
        e.getRecordedDate(),
        e.getCreatedAt(),
        e.getUpdatedAt(),
        e.getVersion() + 1,
        e.getLocationIntent());
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

  public Map<Long, List<Long>> findAll(List<Long> ids) {
    if (ids.isEmpty()) return Map.of();
    return read(
        () -> {
          Map<Long, List<Long>> result = new HashMap<>();
          for (var row : attachmentRows.findForParents(ids))
            result
                .computeIfAbsent(row.getId().getDiaryId(), k -> new ArrayList<>())
                .add(row.getId().getAssetId());
          return result;
        });
  }

  @Transactional
  public void replace(Long id, List<Long> assets) {
    read(
        () -> {
          // Remove managed links and flush before inserting reused composite IDs/order slots.
          // Bulk delete would leave previously loaded link entities stale in this transaction.
          attachmentRows.deleteById_DiaryId(id);
          attachmentRows.flush();
          attachmentRows.saveAllAndFlush(
              IntStream.range(0, assets.size())
                  .mapToObj(i -> DiaryAttachmentJpaEntity.from(id, assets.get(i), i))
                  .toList());
          return null;
        });
  }

  public boolean existsByAssetId(Long id) {
    return read(() -> attachmentRows.existsById_AssetId(id));
  }

  private static <T> T read(Supplier<T> query) {
    try {
      return query.get();
    } catch (DataAccessResourceFailureException | TransientDataAccessResourceException failure) {
      throw new DiaryPersistenceUnavailableException(failure);
    }
  }
}
