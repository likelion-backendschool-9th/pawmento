package com.pawmento.diary.adapter.out.persistence;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.pawmento.diary.application.exception.*;
import com.pawmento.diary.application.port.out.FindDiaryAuthorNamePort;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

class DiaryAuthorBackfillTest {
  static PostgreSQLContainer postgres;
  JdbcTemplate jdbc;
  TransactionTemplate tx;

  @BeforeAll
  static void start() {
    postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));
    postgres.start();
  }

  @AfterAll
  static void stop() {
    postgres.stop();
  }

  @BeforeEach
  void setup() {
    var ds =
        new DriverManagerDataSource(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    jdbc = new JdbcTemplate(ds);
    tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
    jdbc.execute("drop table if exists diary_entries");
    jdbc.execute(
        "create table diary_entries(id bigint primary key, created_by bigint not null,"
            + " author_name_snapshot varchar(255), content text, version bigint not null,"
            + " created_at timestamptz)");
    jdbc.update(
        "insert into diary_entries values(1,10,null,'원본',7,'2026-11-17T00:00:00Z'),"
            + "(2,10,null,'원본2',2,'2026-11-18T00:00:00Z'),"
            + "(3,20,null,'없는 회원',0,'2026-11-18T00:00:00Z'),"
            + "(4,10,'기존 이름','기존 스냅샷',3,'2026-11-18T00:00:00Z')");
  }

  @Test
  @DisplayName("#32-S30 이관은 회원별로 이름을 조회하고 기존 스냅샷·본문·버전·시각을 보존하며 재실행해도 덮어쓰지 않는다")
  void preservesAndRepeats() {
    var author = mock(FindDiaryAuthorNamePort.class);
    when(author.findName(10L)).thenReturn(Optional.of("현재 이름"));
    when(author.findName(20L)).thenReturn(Optional.empty());
    var before =
        jdbc.queryForList(
            "select id,created_by,content,version,created_at from diary_entries order by id");
    tx.executeWithoutResult(s -> DiaryAuthorBackfill.run(jdbc, author));
    tx.executeWithoutResult(s -> DiaryAuthorBackfill.run(jdbc, author));
    assertThat(
            jdbc.queryForList(
                "select author_name_snapshot from diary_entries order by id", String.class))
        .containsExactly("현재 이름", "현재 이름", "알 수 없는 작성자", "기존 이름");
    assertThat(
            jdbc.queryForList(
                "select id,created_by,content,version,created_at from diary_entries order by id"))
        .isEqualTo(before);
    verify(author, times(1)).findName(10L);
    verify(author, times(1)).findName(20L);
    assertThatThrownBy(
            () -> jdbc.update("insert into diary_entries(id,created_by,version) values(5,10,0)"))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThatThrownBy(
            () -> jdbc.update("update diary_entries set author_name_snapshot=' ' where id=1"))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("#32-S30 이름 조회 장애는 이미 보강한 행까지 롤백하고 재시도할 수 있게 남긴다")
  void rollsBackOnFailure() {
    var failure =
        new DiaryPersistenceUnavailableException(new IllegalStateException("test outage"));
    FindDiaryAuthorNamePort author =
        id -> {
          if (id == 20L) throw failure;
          return Optional.of("조회 이름");
        };
    assertThatThrownBy(() -> tx.executeWithoutResult(s -> DiaryAuthorBackfill.run(jdbc, author)))
        .isSameAs(failure);
    assertThat(
            jdbc.queryForList(
                "select author_name_snapshot from diary_entries order by id", String.class))
        .containsExactly(null, null, null, "기존 이름");
    tx.executeWithoutResult(s -> DiaryAuthorBackfill.run(jdbc, id -> Optional.of("재시도 이름")));
    assertThat(
            jdbc.queryForObject(
                "select count(*) from diary_entries where author_name_snapshot is null",
                Long.class))
        .isZero();
  }

  @Test
  @DisplayName("#32-S30 빈 이름은 회원 부재가 아니므로 이관을 중단하고 기존 데이터를 보존한다")
  void rejectsInvalidNames() {
    assertThatThrownBy(
            () ->
                tx.executeWithoutResult(
                    s -> DiaryAuthorBackfill.run(jdbc, id -> Optional.of("  "))))
        .isInstanceOf(StoredDiaryAuthorInvalidException.class);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from diary_entries where author_name_snapshot is null",
                Long.class))
        .isEqualTo(3L);
  }

  @Test
  @DisplayName("#32-S30 이름 열이 없는 기존 스키마에도 열을 추가해 보강한다")
  void addsMissingColumn() {
    jdbc.execute("alter table diary_entries drop column author_name_snapshot");
    tx.executeWithoutResult(s -> DiaryAuthorBackfill.run(jdbc, id -> Optional.of("작성자")));
    assertThat(jdbc.queryForList("select author_name_snapshot from diary_entries", String.class))
        .containsOnly("작성자")
        .hasSize(4);
  }
}
