package com.pawmento.postcard.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.pawmento.TestcontainersConfiguration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

// 이미 엽서 행이 있는 옛 postcards 테이블(generator 열이 생기기 전)에, 앱과 같은 ddl-auto update가 새 열을
// 안전하게 더하는지 확인한다. 실제 테이블을 건드리지 않도록 임시 스키마에서 같은 엔티티로 업데이트한다.
@Import(TestcontainersConfiguration.class)
@SpringBootTest(
    properties = {
      "pawmento.auth.jwt-secret=test-only-jwt-secret-0123456789-abcdefghij",
      "pawmento.media.worker.enabled=false",
      "pawmento.postcard.worker.enabled=false"
    })
class PostcardSchemaUpgradeTest {
  @Autowired DataSource dataSource;
  @Autowired JdbcTemplate jdbc;

  String schema;

  @BeforeEach
  void createLegacyTable() {
    schema = "legacy_" + UUID.randomUUID().toString().replace("-", "");
    jdbc.execute("create schema " + schema);
    // #157 때의 테이블: 지금 엔티티로 만든 뒤 이번에 더한 열을 지운다.
    runSchemaUpdate();
    jdbc.execute(
        "alter table %s.postcards drop column generator, drop column image_content_type"
            .formatted(schema));
    insert(1, "공원 산책");
    insert(2, "바닷가");
  }

  @AfterEach
  void dropLegacyTable() {
    jdbc.execute("drop schema if exists " + schema + " cascade");
  }

  void insert(long id, String content) {
    jdbc.update(
        """
        insert into %s.postcards (id, pet_id, owner_member_id, source_kind, source_id,
          source_content, source_pet_species, title, text, status, requested_on, created_at)
        values (?, 10, 100, 'DIARY', 7, ?, 'DOG', '콩이와 함께한 작은 순간', ?, 'PROCESSING',
          date '2026-11-21', now())
        """
            .formatted(schema),
        id,
        content,
        content);
  }

  void runSchemaUpdate() {
    var registry =
        new StandardServiceRegistryBuilder()
            .applySetting(AvailableSettings.JAKARTA_NON_JTA_DATASOURCE, dataSource)
            .applySetting(AvailableSettings.DEFAULT_SCHEMA, schema)
            .applySetting(
                AvailableSettings.PHYSICAL_NAMING_STRATEGY,
                CamelCaseToUnderscoresNamingStrategy.class.getName())
            // 앱 설정(spring.jpa.hibernate.ddl-auto: update)과 같은 방식이다.
            .applySetting(AvailableSettings.HBM2DDL_AUTO, "update")
            .build();
    try {
      new MetadataSources(registry)
          .addAnnotatedClass(PostcardJpaEntity.class)
          .buildMetadata()
          .buildSessionFactory()
          .close();
    } finally {
      StandardServiceRegistryBuilder.destroy(registry);
    }
  }

  List<Map<String, Object>> newColumns() {
    return jdbc.queryForList(
        """
        select column_name, is_nullable from information_schema.columns
        where table_schema = ? and table_name = 'postcards'
          and column_name in ('generator', 'image_content_type')
        order by column_name
        """,
        schema);
  }

  Map<Long, String> generators() {
    var result = new java.util.HashMap<Long, String>();
    jdbc.queryForList("select id, generator from %s.postcards".formatted(schema))
        .forEach(r -> result.put(((Number) r.get("id")).longValue(), (String) r.get("generator")));
    return result;
  }

  @Test
  @DisplayName("#103-S4 엽서 행이 있는 옛 테이블에도 생성기 열이 추가되고, 기존 행은 가짜 생성기로 채워진다")
  void existingRowsGetFakeGenerator() {
    assertThat(newColumns()).isEmpty();

    runSchemaUpdate();

    assertThat(newColumns())
        .containsExactly(
            Map.of("column_name", "generator", "is_nullable", "NO"),
            Map.of("column_name", "image_content_type", "is_nullable", "YES"));
    assertThat(generators()).containsOnly(Map.entry(1L, "fake"), Map.entry(2L, "fake"));
    assertThat(
            jdbc.queryForList(
                "select source_content from %s.postcards order by id".formatted(schema),
                String.class))
        .containsExactly("공원 산책", "바닷가");
  }

  @Test
  @DisplayName("#103-S4 스키마 업데이트를 다시 실행해도 오류가 없고, 저장된 생성기 이름을 덮지 않는다")
  void reRunKeepsGenerators() {
    runSchemaUpdate();
    jdbc.update("update %s.postcards set generator = 'cloudflare' where id = 1".formatted(schema));

    runSchemaUpdate();

    assertThat(generators()).containsOnly(Map.entry(1L, "cloudflare"), Map.entry(2L, "fake"));
    assertThat(newColumns()).hasSize(2);
  }
}
