package com.pawmento.diary.adapter.out.persistence;

import static org.assertj.core.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

class DiaryAttachmentLegacySchemaTest {
  @Test
  @DisplayName("#32-S28/#31-S16 #107 첨부 스키마를 #115로 이관·초기화해도 기존 FK·순서 유니크는 하나만 남고 데이터와 삭제 정책을 보존한다")
  void legacyConstraintsAreNotDuplicated() throws Exception {
    try (var postgres = new PostgreSQLContainer("postgres:18-alpine")) {
      postgres.start();
      try (var connection =
          DriverManager.getConnection(
              postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
        var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
        jdbc.execute("create table pets(id bigint primary key)");
        jdbc.execute(
            "create table diary_entries(id bigint primary key,pet_id bigint,recorded_date date)");
        jdbc.execute("create table media_assets(id bigint primary key)");
        // Exact schema from #107 (2e44755), before the record_id -> diary_id migration.
        jdbc.execute(sql("diary-schema-v107.sql"));
        jdbc.execute("insert into pets values(1)");
        jdbc.execute("insert into diary_entries values(42,1,'2026-11-20')");
        jdbc.execute("insert into media_assets values(101),(102),(103)");
        jdbc.execute(
            "insert into diary_attachments(record_id,asset_id,sort_order)"
                + " values(42,101,0),(42,102,1)");
        jdbc.execute(sql("diary-naming-migration.sql"));
        for (int i = 0; i < 2; i++) {
          jdbc.execute(sql("diary-schema.sql"));
          assertThat(
                  jdbc.queryForObject(
                      "select count(*) from pg_constraint where"
                          + " conrelid='diary_attachments'::regclass and contype='f' and"
                          + " confrelid='diary_entries'::regclass",
                      Integer.class))
              .isEqualTo(1);
          assertThat(
                  jdbc.queryForObject(
                      "select count(*) from pg_constraint where"
                          + " conrelid='diary_attachments'::regclass and contype='u'",
                      Integer.class))
              .isEqualTo(1);
        }
        assertThat(
                jdbc.queryForList(
                    "select asset_id from diary_attachments where diary_id=42 order by sort_order",
                    Long.class))
            .containsExactly(101L, 102L);
        assertThat(
                jdbc.queryForObject(
                    "select count(*) from pg_constraint where"
                        + " conrelid='diary_attachments'::regclass and conname in"
                        + " ('diary_attachments_record_id_fkey','diary_attachments_record_id_sort_order_key')",
                    Integer.class))
            .isEqualTo(2);
        assertThatThrownBy(() -> jdbc.update("insert into diary_attachments values(999,103,2)"))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("insert into diary_attachments values(42,103,0)"))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("delete from media_assets where id=101"))
            .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("delete from diary_entries where id=42");
        assertThat(jdbc.queryForObject("select count(*) from diary_attachments", Integer.class))
            .isZero();
        assertThat(jdbc.queryForObject("select count(*) from media_assets", Integer.class))
            .isEqualTo(3);
      }
    }
  }

  private static String sql(String name) throws Exception {
    return new ClassPathResource("db/" + name).getContentAsString(StandardCharsets.UTF_8);
  }
}
