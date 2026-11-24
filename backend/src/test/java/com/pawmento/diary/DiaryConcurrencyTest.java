package com.pawmento.diary;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.jayway.jsonpath.JsonPath;
import com.pawmento.TestcontainersConfiguration;
import com.pawmento.diary.application.exception.DiaryVersionConflictException;
import com.pawmento.diary.application.port.in.*;
import com.pawmento.diary.application.port.out.LoadDiaryEntryPort;
import com.pawmento.pet.application.port.in.*;
import java.time.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(
    properties = {
      "pawmento.auth.jwt-secret=test-only-jwt-secret-0123456789-abcdefghij",
      "pawmento.media.worker.enabled=false"
    })
@Import({TestcontainersConfiguration.class, DiaryScenarioTest.Time.class})
@AutoConfigureMockMvc
class DiaryConcurrencyTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired TransactionTemplate tx;
  @Autowired CreatePetUseCase pets;
  @Autowired CreateDiaryEntryUseCase create;
  @Autowired EditDiaryEntryUseCase edit;
  @Autowired DeleteDiaryEntryUseCase deletion;
  @MockitoSpyBean LoadDiaryEntryPort load;
  @MockitoSpyBean com.pawmento.diary.application.port.out.SaveDiaryEntryPort save;

  long pet() {
    return pets.create(new CreatePetCommand(9001L, "보리", "DOG", null, null)).petId();
  }

  String path(long pet, long id) {
    return "/api/v1/diaries/" + id + "?petId=" + pet;
  }

  String path(long pet) {
    return "/api/v1/diaries?petId=" + pet;
  }

  static void waitFor(CountDownLatch latch) {
    try {
      assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError(e);
    }
  }

  int createRequest(long pet) {
    try {
      return mvc.perform(
              post(path(pet))
                  .with(jwt().jwt(j -> j.subject("9001").claim("role", "USER")))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      "{\"title\":\"테스트 일기\",\"content\":\"첫"
                          + " 일기\",\"recordedDate\":\"2026-11-17\"}"))
          .andReturn()
          .getResponse()
          .getStatus();
    } catch (Exception e) {
      throw new AssertionError(e);
    }
  }

  int deletePetRequest(long pet) {
    try {
      return mvc.perform(
              delete("/api/v1/pets/" + pet)
                  .with(jwt().jwt(j -> j.subject("9001").claim("role", "USER"))))
          .andReturn()
          .getResponse()
          .getStatus();
    } catch (Exception e) {
      throw new AssertionError(e);
    }
  }

  // PostgreSQL이 실제로 상대 트랜잭션을 기다리는지 확인한다.
  void blockedOn(String sql) {
    await()
        .atMost(Duration.ofSeconds(8))
        .until(
            () ->
                jdbc.queryForObject(
                        "select count(*) from pg_stat_activity where pid <> pg_backend_pid() and"
                            + " wait_event_type='Lock' and lower(query) like ?",
                        Long.class,
                        "%" + sql + "%")
                    > 0);
  }

  @Test
  @DisplayName("#32-S22 첫 일기 INSERT가 먼저면 pet 행 잠금을 기다린 삭제는 409다")
  void creationWins() throws Exception {
    long pet = pet();
    var inserted = new CountDownLatch(1);
    var commit = new CountDownLatch(1);
    doAnswer(
            invocation -> {
              Object saved = invocation.callRealMethod();
              inserted.countDown();
              waitFor(commit);
              return saved;
            })
        .when(save)
        .save(any());
    try (var pool = Executors.newFixedThreadPool(2)) {
      try {
        var created = pool.submit(() -> createRequest(pet));
        waitFor(inserted);
        var deleted = pool.submit(() -> deletePetRequest(pet));
        blockedOn("select id from pets where id = % for update");
        commit.countDown();
        assertThat(created.get(10, TimeUnit.SECONDS)).isEqualTo(201);
        assertThat(deleted.get(10, TimeUnit.SECONDS)).isEqualTo(409);
      } finally {
        commit.countDown();
      }
    }
    reset(save);
    assertThat(jdbc.queryForObject("select count(*) from pets where id=?", Long.class, pet))
        .isEqualTo(1L);
    Long id = jdbc.queryForObject("select id from diary_entries where pet_id=?", Long.class, pet);
    var detail =
        mvc.perform(get(path(pet, id)).with(jwt().jwt(j -> j.subject("9001")))).andReturn();
    assertThat(detail.getResponse().getStatus()).isEqualTo(200);
    assertThat(JsonPath.<String>read(detail.getResponse().getContentAsString(), "$.content"))
        .isEqualTo("첫 일기");
  }

  @Test
  @DisplayName("#32-S22 pet DELETE가 먼저면 pet 행 잠금을 기다린 첫 일기 생성은 404다")
  void deletionWins() throws Exception {
    long pet = pet();
    var deleted = new CountDownLatch(1);
    var commit = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      try {
        var removed =
            pool.submit(
                () ->
                    tx.execute(
                        status -> {
                          int result = deletePetRequest(pet);
                          assertThat(result).isEqualTo(204);
                          deleted.countDown();
                          waitFor(commit);
                          return result;
                        }));
        waitFor(deleted);
        var created = pool.submit(() -> createRequest(pet));
        blockedOn("select id from pets where id = % for update");
        commit.countDown();
        assertThat(removed.get(10, TimeUnit.SECONDS)).isEqualTo(204);
        assertThat(created.get(10, TimeUnit.SECONDS)).isEqualTo(404);
      } finally {
        commit.countDown();
      }
    }
    assertThat(jdbc.queryForObject("select count(*) from pets where id=?", Long.class, pet))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from diary_entries where pet_id=?", Long.class, pet))
        .isZero();
  }

  @Test
  @DisplayName("#32-S21 같은 버전을 읽은 실제 동시 수정은 하나만 성공한다")
  void concurrentEdits() throws Exception {
    race(false);
  }

  @Test
  @DisplayName("#32-S21 같은 버전을 읽은 실제 수정·삭제 경합은 하나만 성공한다")
  void editAndDelete() throws Exception {
    race(true);
  }

  void race(boolean delete) throws Exception {
    long pet = pet();
    long id =
        create
            .create(
                new CreateDiaryEntryCommand(9001L, pet, "원본", LocalDate.of(2026, 11, 17), "테스트 일기"))
            .diaryId();
    var barrier = new CyclicBarrier(2);
    doAnswer(
            invocation -> {
              Object entry = invocation.callRealMethod();
              if (org.springframework.transaction.support.TransactionSynchronizationManager
                  .isActualTransactionActive()) barrier.await(8, TimeUnit.SECONDS);
              return entry;
            })
        .when(load)
        .findById(id);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var first =
          pool.submit(
              () ->
                  outcome(
                      () ->
                          edit.edit(new EditDiaryEntryCommand(9001L, pet, id, "첫 변경", null, 0L))));
      var second =
          pool.submit(
              () ->
                  outcome(
                      () -> {
                        if (delete)
                          deletion.delete(new DeleteDiaryEntryCommand(9001L, pet, id, 0L));
                        else
                          edit.edit(new EditDiaryEntryCommand(9001L, pet, id, "둘째 변경", null, 0L));
                      }));
      Throwable a = first.get(12, TimeUnit.SECONDS), b = second.get(12, TimeUnit.SECONDS);
      assertThat((a == null) ^ (b == null)).isTrue();
      assertThat(a == null ? b : a).isInstanceOf(DiaryVersionConflictException.class);
      if (delete && b == null)
        assertThat(
                jdbc.queryForObject(
                    "select count(*) from diary_entries where id=?", Long.class, id))
            .isZero();
      else {
        assertThat(
                jdbc.queryForObject("select version from diary_entries where id=?", Long.class, id))
            .isEqualTo(1L);
        assertThat(
                jdbc.queryForObject(
                    "select content from diary_entries where id=?", String.class, id))
            .isEqualTo(a == null ? "첫 변경" : "둘째 변경");
      }
    }
  }

  static Throwable outcome(Runnable task) {
    try {
      task.run();
      return null;
    } catch (RuntimeException e) {
      return e;
    }
  }
}
