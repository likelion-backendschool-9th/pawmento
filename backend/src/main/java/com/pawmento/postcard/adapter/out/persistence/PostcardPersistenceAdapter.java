package com.pawmento.postcard.adapter.out.persistence;

import com.pawmento.postcard.application.exception.PostcardPetNotFoundException;
import com.pawmento.postcard.application.port.out.*;
import com.pawmento.postcard.domain.Postcard;
import com.pawmento.postcard.domain.PostcardStatus;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.postgresql.util.PSQLException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PostcardPersistenceAdapter
    implements SavePostcardPort,
        LoadPostcardPort,
        LoadPostcardsPort,
        DeletePostcardPort,
        ExistsPostcardsPort,
        PostcardWorkPort {
  static final String PET_FK = "fk_postcard_pet";

  private final PostcardJpaRepository postcards;
  private final JdbcTemplate jdbc;

  @Override
  public Long save(Postcard postcard) {
    try {
      var saved = postcards.save(PostcardJpaEntity.from(postcard));
      // 외래 키 위반을 커밋 전이 아니라 여기서 받아 반려동물 없음으로 바꾼다.
      postcards.flush();
      return saved.getId();
    } catch (DataIntegrityViolationException failure) {
      if (violates(failure, PET_FK)) throw new PostcardPetNotFoundException(failure);
      throw failure;
    }
  }

  @Override
  public void attachSourcePhoto(Long postcardId, String key) {
    postcards.attachSourcePhoto(postcardId, key);
  }

  @Override
  public Optional<Postcard> findById(Long postcardId) {
    return postcards.findById(postcardId).map(PostcardJpaEntity::toDomain);
  }

  @Override
  public boolean existsByPetId(Long petId) {
    return postcards.existsByPetId(petId);
  }

  @Override
  public List<Postcard> findAllById(Collection<Long> postcardIds) {
    return postcards.findAllById(postcardIds).stream().map(PostcardJpaEntity::toDomain).toList();
  }

  @Override
  public List<Postcard> findByPet(Long petId, Long cursor, int limit) {
    var rows =
        cursor == null
            ? postcards.findByPetIdOrderByIdDesc(petId, Limit.of(limit))
            : postcards.findByPetIdAndIdLessThanOrderByIdDesc(petId, cursor, Limit.of(limit));
    return rows.stream().map(PostcardJpaEntity::toDomain).toList();
  }

  @Override
  public boolean deleteById(Long postcardId) {
    return postcards.deleteRow(postcardId) == 1;
  }

  // SKIP LOCKED로 서버가 늘어도 같은 엽서를 두 번 선점하지 않는다.
  @Override
  public Optional<Postcard> claimNext(Instant now) {
    var ids =
        jdbc.queryForList(
            "UPDATE postcards SET processing_started_at = ? WHERE id = ("
                + " SELECT id FROM postcards WHERE status = 'PROCESSING'"
                + " AND processing_started_at IS NULL ORDER BY id LIMIT 1 FOR UPDATE SKIP LOCKED)"
                + " AND processing_started_at IS NULL RETURNING id",
            Long.class,
            Timestamp.from(now));
    return ids.stream().findFirst().flatMap(this::findById);
  }

  @Override
  public boolean complete(Long postcardId, String imageKey, String contentType, Instant now) {
    return postcards.complete(
            postcardId,
            imageKey,
            contentType,
            now,
            PostcardStatus.COMPLETED,
            PostcardStatus.PROCESSING)
        == 1;
  }

  @Override
  public boolean fail(Long postcardId, String message) {
    return postcards.fail(postcardId, message, PostcardStatus.FAILED, PostcardStatus.PROCESSING)
        == 1;
  }

  @Override
  public List<Long> startedBefore(Instant cutoff) {
    return postcards.startedBefore(cutoff, PostcardStatus.PROCESSING);
  }

  static boolean violates(Throwable failure, String constraint) {
    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
      if (cause instanceof PSQLException sql
          && sql.getServerErrorMessage() != null
          && constraint.equals(sql.getServerErrorMessage().getConstraint())
          && "23503".equals(sql.getSQLState())) return true;
    }
    return false;
  }
}
