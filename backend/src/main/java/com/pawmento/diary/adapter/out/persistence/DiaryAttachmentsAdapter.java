package com.pawmento.diary.adapter.out.persistence;

import com.pawmento.diary.application.exception.DiaryPersistenceUnavailableException;
import com.pawmento.diary.application.port.out.DiaryAttachmentsPort;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.dao.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DiaryAttachmentsAdapter implements DiaryAttachmentsPort {
  private final JdbcTemplate jdbc;
  private final NamedParameterJdbcTemplate named;

  public DiaryAttachmentsAdapter(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
    this.named = new NamedParameterJdbcTemplate(jdbc);
  }

  public Map<Long, List<Long>> findAll(List<Long> ids) {
    if (ids.isEmpty()) return Map.of();
    return run(
        () -> {
          Map<Long, List<Long>> result = new HashMap<>();
          named.query(
              "select record_id,asset_id from diary_attachments where record_id in (:ids) order by"
                  + " record_id,sort_order",
              Map.of("ids", ids),
              r -> {
                result.computeIfAbsent(r.getLong(1), k -> new ArrayList<>()).add(r.getLong(2));
              });
          return result;
        });
  }

  public void replace(Long id, List<Long> assets) {
    run(
        () -> {
          jdbc.update("delete from diary_attachments where record_id=?", id);
          for (int i = 0; i < assets.size(); i++)
            jdbc.update(
                "insert into diary_attachments(record_id,asset_id,sort_order) values(?,?,?)",
                id,
                assets.get(i),
                i);
          return null;
        });
  }

  public boolean existsByAssetId(Long id) {
    return run(
        () ->
            Boolean.TRUE.equals(
                jdbc.queryForObject(
                    "select exists(select 1 from diary_attachments where asset_id=?)",
                    Boolean.class,
                    id)));
  }

  private static <T> T run(Supplier<T> action) {
    try {
      return action.get();
    } catch (DataAccessResourceFailureException | TransientDataAccessResourceException e) {
      throw new DiaryPersistenceUnavailableException(e);
    }
  }
}
