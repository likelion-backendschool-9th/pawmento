package com.pawmento.postcard.adapter.out.persistence;

import com.pawmento.postcard.application.exception.PostcardPetNotFoundException;
import com.pawmento.postcard.application.port.out.*;
import com.pawmento.postcard.domain.Postcard;
import com.pawmento.postcard.domain.PostcardStatus;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
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
  public List<StoredPostcardFiles> findFiles(Collection<Long> postcardIds) {
    return postcards.findFiles(postcardIds);
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
  public Optional<Long> claimNext(Instant now) {
    var ids =
        jdbc.queryForList(
            "UPDATE postcards SET processing_started_at = ? WHERE id = ("
                + " SELECT id FROM postcards WHERE status = 'PROCESSING'"
                + " AND processing_started_at IS NULL ORDER BY id LIMIT 1 FOR UPDATE SKIP LOCKED)"
                + " AND processing_started_at IS NULL RETURNING id",
            Long.class,
            Timestamp.from(now));
    return ids.stream().findFirst();
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

  // 환불에 쓸 값을 같은 갱신에서 받아, 저장된 값이 깨진 행도 복원하지 않고 실패로 돌린다.
  @Override
  public Optional<PostcardCharge> fail(Long postcardId, String message) {
    return jdbc
        .query(
            "UPDATE postcards SET status = 'FAILED', error_message = ?"
                + " WHERE id = ? AND status = 'PROCESSING'"
                + " RETURNING owner_member_id, requested_on",
            (rs, i) ->
                new PostcardCharge(
                    rs.getLong("owner_member_id"), rs.getObject("requested_on", LocalDate.class)),
            message,
            postcardId)
        .stream()
        .findFirst();
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
