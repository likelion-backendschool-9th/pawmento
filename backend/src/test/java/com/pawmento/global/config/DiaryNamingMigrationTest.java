package com.pawmento.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

class DiaryNamingMigrationTest {
 @Test
 @DisplayName("#32-S28 기존 위치·첨부 행과 FK를 유지해 이름을 이관하고 재실행한다")
 void preserveRowsAndRepeat() throws Exception {
  try(var postgres=new PostgreSQLContainer("postgres:18-alpine")) {
   postgres.start();
   try(var connection=DriverManager.getConnection(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword())) {
    var jdbc=new JdbcTemplate(new SingleConnectionDataSource(connection,true));
    jdbc.execute("CREATE TABLE diary_entries(id bigint PRIMARY KEY)");
    jdbc.execute("CREATE TABLE record_location(id bigserial PRIMARY KEY, record_id bigint REFERENCES diary_entries(id), CONSTRAINT uk_record_location_record UNIQUE(record_id))");
    jdbc.execute("CREATE TABLE diary_attachments(id bigint PRIMARY KEY, record_id bigint REFERENCES diary_entries(id))");
    jdbc.execute("INSERT INTO diary_entries VALUES(42)");
    jdbc.execute("INSERT INTO record_location(record_id) VALUES(42)");
    jdbc.execute("INSERT INTO diary_attachments VALUES(7,42)");
    var migration=new ClassPathResource("db/diary-naming-migration.sql").getContentAsString(StandardCharsets.UTF_8);
    jdbc.execute(migration);jdbc.execute(migration);
    assertThat(jdbc.queryForObject("SELECT diary_id FROM diary_location WHERE id=1",Long.class)).isEqualTo(42L);
    assertThat(jdbc.queryForObject("SELECT diary_id FROM diary_attachments WHERE id=7",Long.class)).isEqualTo(42L);
    assertThatThrownBy(()->jdbc.execute("INSERT INTO diary_attachments VALUES(8,999)")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThatThrownBy(()->jdbc.execute("INSERT INTO diary_location(diary_id) VALUES(42)")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    jdbc.execute("INSERT INTO diary_entries VALUES(43)");jdbc.execute("INSERT INTO diary_location(diary_id) VALUES(43)");
    assertThat(jdbc.queryForObject("SELECT id FROM diary_location WHERE diary_id=43",Long.class)).isGreaterThan(1L);
    jdbc.execute("CREATE TABLE record_location(id bigint)");
    assertThatThrownBy(()->jdbc.execute(migration)).isInstanceOf(org.springframework.dao.DataAccessException.class);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM diary_location",Long.class)).isEqualTo(2L);
   }
  }
 }
}
