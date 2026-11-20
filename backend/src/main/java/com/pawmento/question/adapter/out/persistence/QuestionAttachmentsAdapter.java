package com.pawmento.question.adapter.out.persistence;

import com.pawmento.question.application.port.out.QuestionAttachmentsPort;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class QuestionAttachmentsAdapter implements QuestionAttachmentsPort {
  private final JdbcTemplate jdbc;
  private final NamedParameterJdbcTemplate named;

  public QuestionAttachmentsAdapter(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
    this.named = new NamedParameterJdbcTemplate(jdbc);
  }

  public Map<Long, List<Long>> findAll(List<Long> ids) {
    if (ids.isEmpty()) return Map.of();
    return run(
        () -> {
          Map<Long, List<Long>> result = new HashMap<>();
          named.query(
              "select answer_id,asset_id from question_answer_attachment where answer_id in (:ids)"
                  + " order by answer_id,sort_order",
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
          jdbc.update("delete from question_answer_attachment where answer_id=?", id);
          for (int i = 0; i < assets.size(); i++)
            jdbc.update(
                "insert into question_answer_attachment(answer_id,asset_id,sort_order)"
                    + " values(?,?,?)",
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
                    "select exists(select 1 from question_answer_attachment where asset_id=?)",
                    Boolean.class,
                    id)));
  }

  private static <T> T run(Supplier<T> action) {
    return QuestionPersistenceOperations.execute(action);
  }
}
