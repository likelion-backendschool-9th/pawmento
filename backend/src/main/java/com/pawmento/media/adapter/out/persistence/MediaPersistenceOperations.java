package com.pawmento.media.adapter.out.persistence;

import com.pawmento.media.application.exception.*;
import jakarta.persistence.*;
import java.util.function.Supplier;
import org.springframework.dao.*;

final class MediaPersistenceOperations {
  private MediaPersistenceOperations() {}

  static <T> T execute(Supplier<T> action) {
    try {
      return action.get();
    } catch (OptimisticLockException | OptimisticLockingFailureException e) {
      throw new MediaConcurrentModificationException(e);
    } catch (PersistenceException | DataAccessException e) {
      throw new MediaPersistenceUnavailableException(e);
    }
  }

  static void execute(Runnable action) {
    execute(
        () -> {
          action.run();
          return null;
        });
  }
}
