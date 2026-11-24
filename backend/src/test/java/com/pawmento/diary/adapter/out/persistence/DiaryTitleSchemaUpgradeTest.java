package com.pawmento.diary.adapter.out.persistence;

import static org.assertj.core.api.Assertions.*;

import com.pawmento.TestcontainersConfiguration;
import java.util.UUID;
import javax.sql.DataSource;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(
    properties = {
      "pawmento.auth.jwt-secret=test-only-jwt-secret-0123456789-abcdefghij",
      "pawmento.media.worker.enabled=false",
      "pawmento.postcard.worker.enabled=false"
    })
class DiaryTitleSchemaUpgradeTest {
  @Autowired DataSource dataSource;
  @Autowired JdbcTemplate jdbc;
  String schema;

  @BeforeEach
  void setup() {
    schema = "diary_title_" + UUID.randomUUID().toString().replace("-", "");
    jdbc.execute("create schema " + schema);
    try (var factory = updateSchema()) {}
    jdbc.execute("alter table " + schema + ".diary_entries drop column title");
    jdbc.update(
        "insert into "
            + schema
            + ".diary_entries(id,pet_id,created_by,author_name_snapshot,content,recorded_date,created_at,updated_at,version)"
            + " values(1,10,20,'기존 작성자','기존"
            + " 본문','2026-11-17','2026-11-18T00:00:00Z','2026-11-19T00:00:00Z',7)");
  }

  @AfterEach
  void cleanup() {
    jdbc.execute("drop schema if exists " + schema + " cascade");
  }

  SessionFactory updateSchema() {
    var registry =
        new StandardServiceRegistryBuilder()
            .applySetting(AvailableSettings.JAKARTA_NON_JTA_DATASOURCE, dataSource)
            .applySetting(AvailableSettings.DEFAULT_SCHEMA, schema)
            .applySetting(
                AvailableSettings.PHYSICAL_NAMING_STRATEGY,
                CamelCaseToUnderscoresNamingStrategy.class.getName())
            .applySetting(AvailableSettings.HBM2DDL_AUTO, "update")
            .build();
    try {
      return new MetadataSources(registry)
          .addAnnotatedClass(DiaryEntryJpaEntity.class)
          .buildMetadata()
          .buildSessionFactory();
    } catch (RuntimeException failure) {
      StandardServiceRegistryBuilder.destroy(registry);
      throw failure;
    }
  }

  @Test
  @DisplayName("#32-S37 nullable 제목 열을 기존 스키마에 추가하고 재적용해도 기존 값·제목·버전을 보존한다")
  void preservesLegacyRowsAndTitles() {
    var before = jdbc.queryForMap("select * from " + schema + ".diary_entries where id=1");
    try (var factory = updateSchema();
        var session = factory.openSession()) {
      var entry = session.find(DiaryEntryJpaEntity.class, 1L).toDomain();
      assertThat(entry.getTitle().value()).isNull();
      assertThat(entry.getAuthorName().value()).isEqualTo("기존 작성자");
      assertThat(entry.getVersion()).isEqualTo(7);
      entry.edit(20L, "호환되는 수정", null, java.time.Instant.parse("2026-11-20T00:00:00Z"));
      assertThat(entry.getTitle().value()).isNull();
    }
    var after = jdbc.queryForMap("select * from " + schema + ".diary_entries where id=1");
    assertThat(after.remove("title")).isNull();
    assertThat(after).isEqualTo(before);
    assertThat(
            jdbc.queryForObject(
                "select is_nullable from information_schema.columns where table_schema=? and"
                    + " table_name='diary_entries' and column_name='title'",
                String.class,
                schema))
        .isEqualTo("YES");
    assertThat(
            jdbc.queryForObject(
                "select character_maximum_length from information_schema.columns where"
                    + " table_schema=? and table_name='diary_entries' and column_name='title'",
                Integer.class,
                schema))
        .isEqualTo(100);
    jdbc.update("update " + schema + ".diary_entries set title='직접 작성한 제목' where id=1");
    try (var factory = updateSchema();
        var session = factory.openSession()) {
      assertThat(session.find(DiaryEntryJpaEntity.class, 1L).toDomain().getTitle().value())
          .isEqualTo("직접 작성한 제목");
    }
    var repeated = jdbc.queryForMap("select * from " + schema + ".diary_entries where id=1");
    assertThat(repeated.remove("title")).isEqualTo("직접 작성한 제목");
    assertThat(repeated).isEqualTo(before);
  }
}
