package com.pawmento.question.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pawmento.TestcontainersConfiguration;
import com.pawmento.question.application.exception.StoredQuestionInvalidException;
import com.pawmento.question.domain.Question;
import com.pawmento.question.domain.QuestionMode;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.server.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "pawmento.auth.jwt-secret=test-only-jwt-secret-0123456789-abcdefghij",
      "pawmento.media.worker.enabled=false"
    })
@Import({TestcontainersConfiguration.class, QuestionSeedScenarioTest.StartupProbe.class})
class QuestionSeedScenarioTest {
  private static final Resource V1 = new ClassPathResource("db/question-seed-v1.sql");
  @Autowired JdbcTemplate jdbc;
  @Autowired PlatformTransactionManager manager;
  @Autowired QuestionPersistenceAdapter adapter;
  @Autowired AtomicInteger questionsWhenServerStarted;

  @TestConfiguration(proxyBeanMethods = false)
  static class StartupProbe {
    @Bean
    AtomicInteger questionsWhenServerStarted() {
      return new AtomicInteger(-1);
    }

    @Bean
    ApplicationListener<WebServerInitializedEvent> observeServerStart(
        DataSource source, AtomicInteger questionsWhenServerStarted) {
      return event ->
          questionsWhenServerStarted.set(
              new JdbcTemplate(source)
                  .queryForObject("select count(*) from question", Integer.class));
    }
  }

  @BeforeEach
  void prepare() throws Exception {
    jdbc.update("delete from question");
    initialize(V1);
  }

  @AfterEach
  void restore() throws Exception {
    jdbc.update("delete from question");
    initialize(V1);
  }

  private void initialize(Resource seed) throws Exception {
    new QuestionSeedInitializer(jdbc, manager, seed).initialize();
  }

  @Test
  @DisplayName("#31-S21 웹 서버 시작 시 이미 초기 질문이 준비되고 모드별 활성 풀을 조회한다")
  void initializedBeforeServerStarts() {
    assertThat(questionsWhenServerStarted.get()).isEqualTo(4);
    for (var mode : QuestionMode.values()) {
      var questions = adapter.loadByMode(mode);
      assertThat(questions)
          .hasSize(2)
          .allSatisfy(
              question -> {
                assertThat(question.getId()).isPositive();
                assertThat(question.getMode()).isEqualTo(mode);
                assertThat(question.isEnabled()).isTrue();
                assertThat(question.getVersion()).isZero();
                assertThat(question.getContent()).isNotBlank();
                assertThat(question.getCreatedAt()).isEqualTo(question.getUpdatedAt());
              });
    }
    assertThat(jdbc.queryForObject("select count(distinct seed_key) from question", Integer.class))
        .isEqualTo(4);
  }

  @Test
  @DisplayName("#31-S21 재적용은 ID·수정 본문·비활성·버전·일반 질문을 덮어쓰지 않는다")
  void reapplicationPreservesChanges() throws Exception {
    jdbc.update(
        "update question set content='보존할 질문', enabled=false, version=7,"
            + " updated_at=updated_at+interval '1 second' where seed_key='active-play-001'");
    jdbc.update(
        "insert into question(mode,content,enabled,created_at,updated_at,version)"
            + " values('ACTIVE','일반 질문',true,now(),now(),0)");
    var before = jdbc.queryForList("select * from question order by id");
    initialize(V1);
    initialize(V1);
    assertThat(jdbc.queryForList("select * from question order by id")).isEqualTo(before);
    assertThat(adapter.loadByMode(QuestionMode.ACTIVE))
        .extracting(Question::getContent)
        .containsExactly("오늘 우리 아이가 가장 신났던 순간은 언제인가요?", "일반 질문");
  }

  @Test
  @DisplayName("#31-S21 시작 시 모드 충돌은 빈 초기화를 실패시키고 누락 질문도 삽입하지 않는다")
  void modeConflictStopsStartup() {
    jdbc.update("delete from question where seed_key <> 'active-play-001'");
    jdbc.update("update question set mode='GARDEN' where seed_key='active-play-001'");
    var before = jdbc.queryForList("select * from question order by id");
    assertStartupFails(V1);
    assertThat(jdbc.queryForList("select * from question order by id")).isEqualTo(before);
  }

  @Test
  @DisplayName("#31-S21 삽입 뒤 SQL 오류도 전체 롤백하고 시작을 실패시킨다")
  void sqlFailureRollsBackStartup() throws Exception {
    jdbc.update("delete from question");
    var failing =
        new ByteArrayResource(
            (V1.getContentAsString(StandardCharsets.UTF_8) + "\nSELECT 1/0;")
                .getBytes(StandardCharsets.UTF_8));
    assertStartupFails(failing);
    assertThat(jdbc.queryForObject("select count(*) from question", Integer.class)).isZero();
  }

  private void assertStartupFails(Resource resource) {
    try (var context = new AnnotationConfigApplicationContext()) {
      context.registerBean(
          "questionSeed", InitializingBean.class, () -> () -> initialize(resource));
      assertThatThrownBy(context::refresh).isInstanceOf(BeanCreationException.class);
    }
  }

  @Test
  @DisplayName("#31-S21 동시 초기화는 같은 잠금으로 직렬화되고 먼저 시작한 트랜잭션의 완료를 기다린다")
  void concurrentInitialization() throws Exception {
    jdbc.update("delete from question");
    var lockHeld = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(3)) {
      var owner =
          pool.submit(
              () ->
                  new TransactionTemplate(manager)
                      .executeWithoutResult(
                          status -> {
                            jdbc.execute(
                                "select"
                                    + " pg_advisory_xact_lock(hashtextextended('pawmento-question-seed',0))");
                            lockHeld.countDown();
                            try {
                              if (!release.await(10, TimeUnit.SECONDS))
                                throw new AssertionError("잠금 해제 신호 없음");
                            } catch (InterruptedException e) {
                              Thread.currentThread().interrupt();
                              throw new AssertionError(e);
                            }
                          }));
      assertThat(lockHeld.await(10, TimeUnit.SECONDS)).isTrue();
      var first =
          pool.submit(
              () -> {
                initialize(V1);
                return true;
              });
      var second =
          pool.submit(
              () -> {
                initialize(V1);
                return true;
              });
      try {
        // DB의 실제 advisory 잠금 대기를 확인한다. sleep이나 순차 호출로 경합을 대신하지 않는다.
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (jdbc.queryForObject(
                "select count(*) from pg_locks where locktype='advisory' and not granted and"
                    + " database=(select oid from pg_database where datname=current_database())",
                Integer.class)
            < 2) {
          if (System.nanoTime() > deadline) throw new AssertionError("두 초기화가 advisory 잠금을 기다리지 않음");
          Thread.yield();
        }
        assertThat(first.isDone()).isFalse();
        assertThat(second.isDone()).isFalse();
      } finally {
        release.countDown();
      }
      owner.get(10, TimeUnit.SECONDS);
      assertThat(first.get(10, TimeUnit.SECONDS)).isTrue();
      assertThat(second.get(10, TimeUnit.SECONDS)).isTrue();
    }
    assertThat(jdbc.queryForObject("select count(*) from question", Integer.class)).isEqualTo(4);
    assertThat(jdbc.queryForObject("select count(distinct seed_key) from question", Integer.class))
        .isEqualTo(4);
  }

  @Test
  @DisplayName("#31-S21 seed_key는 유일하지만 일반 질문의 null 키와 중복 본문은 허용한다")
  void seedKeyConstraint() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into"
                        + " question(seed_key,mode,content,enabled,created_at,updated_at,version)"
                        + " values('active-play-001','ACTIVE','질문',true,now(),now(),0)"))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    for (int i = 0; i < 2; i++)
      jdbc.update(
          "insert into question(mode,content,enabled,created_at,updated_at,version)"
              + " values('ACTIVE','같은 질문',true,now(),now(),0)");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from question where seed_key is null", Integer.class))
        .isEqualTo(2);
  }

  @Test
  @DisplayName("#31-S21 손상된 저장 질문은 입력 400 대신 저장 데이터 오류로 드러난다")
  void corruptStoredContentIsNotInputError() {
    jdbc.update("update question set content=' ' where seed_key='active-play-001'");
    assertThatThrownBy(() -> adapter.loadByMode(QuestionMode.ACTIVE))
        .isInstanceOf(StoredQuestionInvalidException.class);
  }
}
