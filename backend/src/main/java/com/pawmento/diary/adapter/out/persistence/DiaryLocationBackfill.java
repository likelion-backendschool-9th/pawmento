package com.pawmento.diary.adapter.out.persistence;

import com.pawmento.diary.application.exception.DiaryLocationDataInvalidException;
import com.pawmento.diary.domain.DiaryLocationIntent;
import com.pawmento.diary.domain.exception.InvalidDiaryLocationIntentException;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;

/** 시작 시 미이관 원본만 기존의 정상 핀에서 옮긴다. */
final class DiaryLocationBackfill {
  private record Item(long id, long version, DiaryLocationIntent intent, boolean hasPin) {}

  static void run(JdbcTemplate jdbc) {
    jdbc.execute("LOCK TABLE diary_entries,diary_location IN SHARE ROW EXCLUSIVE MODE");
    if (!jdbc.queryForList(
            "select m.diary_id from diary_location m left join diary_entries e on e.id=m.diary_id"
                + " where e.id is null limit 1")
        .isEmpty()) throw new DiaryLocationDataInvalidException("부모 일기가 없는 기존 지도 위치를 먼저 확인해야 합니다.");
    var items =
        jdbc.query(
            """
            select e.id,e.pet_id,e.created_by,e.version,m.id as pin_id,m.pet_id as pin_pet,m.created_by as pin_author,
              m.latitude,m.longitude,m.place_name,m.source,m.source_asset_id,m.source_revision
            from diary_entries e left join diary_location m on m.diary_id=e.id where e.location_mode is null order by e.id
            """,
            (rs, n) -> {
              long id = rs.getLong("id");
              boolean pin = rs.getObject("pin_id") != null;
              if (!pin)
                return new Item(id, rs.getLong("version"), DiaryLocationIntent.none(), false);
              if (!Objects.equals(rs.getObject("pet_id"), rs.getObject("pin_pet"))
                  || !Objects.equals(rs.getObject("created_by"), rs.getObject("pin_author"))
                  || !"MANUAL".equals(rs.getString("source"))
                  || rs.getObject("source_asset_id") != null
                  || rs.getLong("source_revision") < 0)
                throw new DiaryLocationDataInvalidException("위치 초기 이관이 충돌했습니다. diaryId=" + id);
              try {
                return new Item(
                    id,
                    rs.getLong("version"),
                    DiaryLocationIntent.manual(
                        rs.getDouble("latitude"),
                        rs.getDouble("longitude"),
                        rs.getString("place_name")),
                    true);
              } catch (InvalidDiaryLocationIntentException e) {
                throw new DiaryLocationDataInvalidException(
                    "기존 지도 위치를 이관할 수 없습니다. diaryId=" + id, e);
              }
            });
    // 전체 검증 후 기록한다. 시작 트랜잭션이 스키마와 양쪽 값을 원자적으로 반영한다.
    for (var item : items) {
      var v = item.intent();
      jdbc.update(
          "update diary_entries set"
              + " location_mode=?,location_latitude=?,location_longitude=?,location_place_name=?"
              + " where id=? and location_mode is null",
          v.mode().name(),
          v.latitude(),
          v.longitude(),
          v.placeName(),
          item.id());
      if (item.hasPin())
        jdbc.update(
            "update diary_location set latitude=?,longitude=?,place_name=?,source_revision=? where"
                + " diary_id=?",
            v.latitude(),
            v.longitude(),
            v.placeName(),
            item.version(),
            item.id());
    }
  }
}
