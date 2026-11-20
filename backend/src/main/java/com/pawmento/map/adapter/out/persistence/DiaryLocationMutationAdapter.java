package com.pawmento.map.adapter.out.persistence;

import com.pawmento.map.application.exception.DiaryLocationPersistenceUnavailableException;
import com.pawmento.map.application.port.in.*;
import com.pawmento.map.application.port.out.DiaryLocationStorePort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class DiaryLocationMutationAdapter implements DiaryLocationStorePort {
  private final JdbcTemplate jdbc;
  private static final String SELECT =
      "select"
          + " id,diary_id,pet_id,created_by,latitude,longitude,place_name,source,source_asset_id,source_revision"
          + " from diary_location where diary_id=?";

  public Optional<DiaryLocationState> read(Long id) {
    return run(() -> jdbc.query(SELECT, (rs, n) -> state(rs), id).stream().findFirst());
  }

  public Optional<DiaryLocationState> lock(Long id) {
    return run(
        () -> {
          // 행이 없는 최초 삽입도 같은 일기 단위로 직렬화한다. 잠금은 현재 DB 트랜잭션이 끝날 때 풀린다.
          jdbc.queryForList(
              "select pg_advisory_xact_lock(hashtextextended(?,0))", "pawmento-map-location:" + id);
          return jdbc.query(SELECT + " for update", (rs, n) -> state(rs), id).stream().findFirst();
        });
  }

  public void write(Long id, Long pet, Long author, long revision, DiaryLocationValue v) {
    run(
        () ->
            jdbc.update(
                """
                insert into diary_location(diary_id,pet_id,created_by,latitude,longitude,place_name,source,source_asset_id,source_revision,created_at,updated_at)
                values(?,?,?,?,?,?,'MANUAL',null,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                on conflict(diary_id) do update set pet_id=excluded.pet_id,created_by=excluded.created_by,
                  latitude=excluded.latitude,longitude=excluded.longitude,place_name=excluded.place_name,
                  source='MANUAL',source_asset_id=null,source_revision=excluded.source_revision,updated_at=CURRENT_TIMESTAMP
                """,
                id,
                pet,
                author,
                v.latitude(),
                v.longitude(),
                v.placeName(),
                revision));
  }

  public void remove(Long id) {
    run(() -> jdbc.update("delete from diary_location where diary_id=?", id));
  }

  private static DiaryLocationState state(ResultSet rs) throws SQLException {
    return new DiaryLocationState(
        rs.getLong("id"),
        rs.getLong("diary_id"),
        rs.getLong("pet_id"),
        rs.getLong("created_by"),
        rs.getDouble("latitude"),
        rs.getDouble("longitude"),
        rs.getString("place_name"),
        rs.getString("source"),
        rs.getObject("source_asset_id", Long.class),
        rs.getLong("source_revision"));
  }

  private static <T> T run(Supplier<T> action) {
    return MapPersistenceOperations.execute(
        action, DiaryLocationPersistenceUnavailableException::new);
  }
}
