package com.pawmento.diary;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.pawmento.TestcontainersConfiguration;
import com.pawmento.diary.adapter.out.persistence.DiaryEntryPersistenceAdapter;
import com.pawmento.diary.application.port.in.*;
import com.pawmento.diary.application.port.out.CheckDiaryPetAccessPort;
import com.pawmento.pet.application.port.in.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(
    properties = {
      "pawmento.auth.jwt-secret=test-only-jwt-secret-0123456789-abcdefghij",
      "pawmento.media.worker.enabled=false"
    })
@Import({TestcontainersConfiguration.class, DiaryScenarioTest.Time.class})
class DiaryListSnapshotTest {
  @Autowired ListDiaryEntriesUseCase lists;
  @Autowired CreatePetUseCase pets;
  @Autowired JdbcTemplate jdbc;
  @Autowired PlatformTransactionManager manager;
  @MockitoSpyBean CheckDiaryPetAccessPort access;
  @MockitoSpyBean DiaryEntryPersistenceAdapter persistence;

  record Snapshot(int connection, String isolation, boolean readOnly) {}

  Snapshot snapshot() {
    return jdbc.queryForObject(
        "select pg_backend_pid(), current_setting('transaction_isolation'),"
            + " current_setting('transaction_read_only')",
        (rs, n) -> new Snapshot(rs.getInt(1), rs.getString(2), "on".equals(rs.getString(3))));
  }

  long seed(long pet, String text) {
    return jdbc.queryForObject(
        "insert into"
            + " diary_entries(pet_id,created_by,content,recorded_date,created_at,updated_at,version,author_name_snapshot)"
            + " values(?,9001,?,'2026-11-17','2026-11-17T15:00:00Z','2026-11-17T15:00:00Z',0,'기존"
            + " 작성자') returning id",
        Long.class,
        pet,
        text);
  }

  static void waitFor(CountDownLatch latch) {
    try {
      assertThat(latch.await(8, TimeUnit.SECONDS)).isTrue();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError(e);
    }
  }

  @Test
  @DisplayName("#32-S26 실제 UseCase 진입은 병렬 추가 중에도 목록·count를 같은 RR 스냅샷으로 조회한다")
  void insert() throws Exception {
    verifySnapshot(false, false);
  }

  @Test
  @DisplayName("#32-S26 실제 UseCase 진입은 병렬 삭제 중에도 목록·count를 같은 RR 스냅샷으로 조회한다")
  void delete() throws Exception {
    verifySnapshot(false, true);
  }

  @Test
  @DisplayName("#32-S26 기존 RC 안에서 호출해도 독립 RR 읽기와 미커밋 쓰기 미노출을 보장한다: 추가")
  void nestedInsert() throws Exception {
    verifySnapshot(true, false);
  }

  @Test
  @DisplayName("#32-S26 기존 RC 안에서 호출해도 독립 RR 읽기와 미커밋 쓰기 미노출을 보장한다: 삭제")
  void nestedDelete() throws Exception {
    verifySnapshot(true, true);
  }

  void verifySnapshot(boolean nested, boolean delete) throws Exception {
    long pet = pets.create(new CreatePetCommand(9001L, "보리", "DOG", null, null)).petId();
    long original = seed(pet, "원본");
    var pageRead = new CountDownLatch(1);
    var committed = new CountDownLatch(1);
    Map<String, Snapshot> observed = new ConcurrentHashMap<>();
    doAnswer(
            invocation -> {
              invocation.callRealMethod();
              observed.put("access", snapshot());
              return null;
            })
        .when(access)
        .requireAccess(9001L, pet);
    doAnswer(
            invocation -> {
              var result = invocation.callRealMethod();
              observed.put("page", snapshot());
              pageRead.countDown();
              waitFor(committed);
              return result;
            })
        .when(persistence)
        .entries(eq(pet), any());
    doAnswer(
            invocation -> {
              observed.put("count", snapshot());
              return invocation.callRealMethod();
            })
        .when(persistence)
        .count(eq(pet), any());
    var query = ListDiaryEntriesQuery.of(9001L, pet, null, null, null, 0, 20);
    ListDiaryEntriesResult result;
    try (var pool = Executors.newSingleThreadExecutor()) {
      var writer =
          pool.submit(
              () -> {
                try {
                  waitFor(pageRead);
                  if (delete) jdbc.update("delete from diary_entries where id=?", original);
                  else seed(pet, "다른 연결의 커밋");
                } finally {
                  committed.countDown();
                }
              });
      if (nested) {
        var outer = new TransactionTemplate(manager);
        outer.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        result =
            outer.execute(
                status -> {
                  Snapshot before = snapshot();
                  assertThat(before.isolation()).isEqualTo("read committed");
                  seed(pet, "바깥 트랜잭션의 미커밋 일기");
                  var page = lists.list(query); // 직접 만든 RR가 아니라 서비스 프록시로 진입한다.
                  assertThat(observed.get("page").connection()).isNotEqualTo(before.connection());
                  assertThat(snapshot().connection()).isEqualTo(before.connection());
                  status.setRollbackOnly();
                  return page;
                });
      } else result = lists.list(query);
      writer.get(10, TimeUnit.SECONDS);
    }
    assertThat(result.items()).extracting(GetDiaryEntryResult::diaryId).containsExactly(original);
    assertThat(result.totalElements()).isEqualTo(1);
    assertThat(observed.keySet()).containsExactlyInAnyOrder("access", "page", "count");
    Snapshot expected = observed.get("page");
    assertThat(expected.isolation()).isEqualTo("repeatable read");
    assertThat(expected.readOnly()).isTrue();
    assertThat(observed.values()).containsOnly(expected);
    reset(access, persistence);
    assertThat(lists.list(query).totalElements()).isEqualTo(delete ? 0 : 2);
  }
}
