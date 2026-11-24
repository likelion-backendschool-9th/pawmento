package com.pawmento.diary.adapter.out.persistence;

import com.pawmento.diary.application.port.out.LoadDiaryAuthorNameForBackfillPort;
import com.pawmento.diary.domain.DiaryAuthorName;
import org.springframework.jdbc.core.JdbcTemplate;

// 시작 시 마이그레이션에서만 JDBC를 쓴다. 일반 일기 CRUD는 기존 JPA를 유지한다.
final class DiaryAuthorBackfill {
  private static final DiaryAuthorName UNKNOWN = new DiaryAuthorName("알 수 없는 작성자");

  private DiaryAuthorBackfill() {}

  static void run(JdbcTemplate jdbc, LoadDiaryAuthorNameForBackfillPort authors) {
    jdbc.queryForObject(
        "select true from pg_advisory_xact_lock(hashtextextended(?,0))",
        Boolean.class,
        "diary-author-snapshot-backfill");
    jdbc.execute(
        "alter table diary_entries add column if not exists author_name_snapshot varchar(255)");
    while (true) {
      var ids =
          jdbc.queryForList(
              "select distinct created_by from diary_entries"
                  + " where author_name_snapshot is null order by created_by limit 200",
              Long.class);
      if (ids.isEmpty()) break;
      for (var id : ids) {
        var name = authors.loadNameForBackfill(id).orElse(UNKNOWN).value();
        jdbc.update(
            "update diary_entries set author_name_snapshot=?"
                + " where created_by=? and author_name_snapshot is null",
            name,
            id);
      }
    }
    jdbc.execute("alter table diary_entries alter column author_name_snapshot set not null");
    jdbc.execute(
        """
        DO $$ BEGIN
          IF NOT EXISTS (SELECT 1 FROM pg_constraint
              WHERE conrelid='diary_entries'::regclass AND conname='ck_diary_author_name') THEN
            ALTER TABLE diary_entries ADD CONSTRAINT ck_diary_author_name
              CHECK (char_length(btrim(author_name_snapshot)) BETWEEN 1 AND 255);
          END IF;
        END $$
        """);
  }
}
