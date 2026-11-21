package com.pawmento.postcard.adapter.out.persistence;

import com.pawmento.postcard.application.exception.PostcardPetNotFoundException;
import com.pawmento.postcard.application.port.out.ExistsPostcardsPort;
import com.pawmento.postcard.application.port.out.LoadPostcardPort;
import com.pawmento.postcard.application.port.out.SavePostcardPort;
import com.pawmento.postcard.domain.Postcard;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.postgresql.util.PSQLException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PostcardPersistenceAdapter
    implements SavePostcardPort, LoadPostcardPort, ExistsPostcardsPort {
  static final String PET_FK = "fk_postcard_pet";

  private final PostcardJpaRepository postcards;

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
