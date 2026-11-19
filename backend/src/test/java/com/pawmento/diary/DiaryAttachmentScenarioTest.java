package com.pawmento.diary;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.pawmento.TestcontainersConfiguration;
import com.pawmento.diary.application.port.in.*;
import com.pawmento.diary.application.port.out.*;
import com.pawmento.media.application.port.out.*;
import com.pawmento.media.application.service.MediaCleanupTransactions;
import com.pawmento.media.domain.*;
import com.pawmento.pet.application.port.in.*;
import java.io.ByteArrayInputStream;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(
    properties = {
      "pawmento.auth.jwt-secret=test-only-jwt-secret-0123456789-abcdefghij",
      "pawmento.media.worker.enabled=false"
    })
@Import({TestcontainersConfiguration.class, DiaryScenarioTest.Time.class})
@AutoConfigureMockMvc
class DiaryAttachmentScenarioTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired CreatePetUseCase pets;
  @MockitoSpyBean SaveMediaAssetPort save;
  @Autowired CreateDiaryEntryUseCase create;
  @Autowired EditDiaryEntryUseCase edit;
  @Autowired DeleteDiaryEntryUseCase deleteDiary;
  @Autowired GetDiaryEntryUseCase get;
  @Autowired MediaCleanupTransactions cleanup;
  @Autowired TransactionTemplate tx;
  @MockitoSpyBean ReadMediaFilePort files;
  @MockitoSpyBean DiaryAttachmentsPort links;
  @Autowired Clock clock;

  long pet() {
    return pets.create(new CreatePetCommand(9001L, "보리", "DOG", null, null)).petId();
  }

  long asset(long pet, boolean audio) {
    return asset(pet, 9001L, audio, "DIARY_ATTACHMENT", true);
  }

  long asset(long pet, long owner, boolean audio, String purpose, boolean ready) {
    var now = clock.instant().minusSeconds(60);
    var a =
        MediaAsset.create(
            owner,
            pet,
            purpose,
            "file",
            audio ? "audio/webm" : "image/png",
            10,
            UUID.randomUUID().toString(),
            now);
    if (ready) {
      a.begin(now);
      a.received(new StoredUpload(10, "a".repeat(64)), now);
      a.validated(
          new VerifiedMedia(
              UUID.randomUUID().toString(),
              audio ? "audio/mp4" : "image/png",
              10,
              "a".repeat(64),
              audio ? 1000L : null,
              audio ? null : 1,
              audio ? null : 1,
              audio ? null : 5L),
          now);
    }
    return save.save(a).getId();
  }

  DiaryWriteFeatures features(Long... ids) {
    return new DiaryWriteFeatures(List.of(ids), false);
  }

  GetDiaryEntryResult create(long pet, String content, Long... ids) {
    return create.create(
        new CreateDiaryEntryCommand(
            9001L, pet, content, LocalDate.of(2026, 11, 17), features(ids)));
  }

  GetDiaryEntryResult change(long pet, long id, long version, String content, Long... ids) {
    return edit.edit(
        new EditDiaryEntryCommand(9001L, pet, id, content, null, version, features(ids)));
  }

  String path(long pet) {
    return "/api/v1/diaries?petId=" + pet;
  }

  String path(long pet, long record) {
    return "/api/v1/diaries/" + record + "?petId=" + pet;
  }

  ResultActions call(long member, MockHttpServletRequestBuilder req) throws Exception {
    return mvc.perform(req.with(jwt().jwt(j -> j.subject("" + member))));
  }

  MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder req, String body) {
    return req.contentType(MediaType.APPLICATION_JSON).content(body);
  }

  long refs(long asset) {
    return jdbc.queryForObject(
        "select reference_count from media_assets where id=?", Long.class, asset);
  }

  String state(long asset) {
    return jdbc.queryForObject("select status from media_assets where id=?", String.class, asset);
  }

  long jobs(long asset) {
    return jdbc.queryForObject(
        "select count(*) from media_validation_jobs where asset_id=? and kind='DELETE'",
        Long.class,
        asset);
  }

  List<Long> ids(long record) {
    return links.find(record);
  }

  @Test
  @DisplayName("#32-S3 #32-S9 JSON 생략·null·공백과 첨부 전용 전환·첨부만 PATCH를 구분한다")
  void jsonContracts() throws Exception {
    long p = pet(), a = asset(p, false), b = asset(p, true);
    for (String prefix : List.of("", "\"content\":\"\",", "\"content\":\"  \",")) {
      call(
              9001,
              json(
                  post(path(p)),
                  "{" + prefix + "\"recordedDate\":\"2026-11-17\",\"attachmentIds\":[" + a + "]}"))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.content").value(""));
    }
    for (String body :
        List.of(
            "{\"content\":null,\"attachmentIds\":[" + a + "]}",
            "{}",
            "{\"content\":\"  \"}",
            "{\"content\":\"x\",\"attachmentIds\":null}",
            "{\"attachmentIds\":[" + a + "," + a + "]}",
            "{\"attachmentIds\":[0]}"))
      call(
              9001,
              json(
                  post(path(p)),
                  body.substring(0, body.length() - 1) + ",\"recordedDate\":\"2026-11-17\"}"))
          .andExpect(status().isBadRequest());
    var r = create(p, "일기");
    call(
            9001,
            json(
                patch(path(p, r.recordId())),
                "{\"version\":0,\"content\":\"\",\"attachmentIds\":[" + a + "]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(1));
    call(9001, json(patch(path(p, r.recordId())), "{\"version\":1,\"attachmentIds\":[" + b + "]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").value(""))
        .andExpect(jsonPath("$.recordedDate").value("2026-11-17"))
        .andExpect(jsonPath("$.version").value(2));
    call(9001, json(patch(path(p, r.recordId())), "{\"version\":2,\"content\":null}"))
        .andExpect(status().isBadRequest());
    call(9001, json(patch(path(p, r.recordId())), "{\"version\":2,\"attachmentIds\":[]}"))
        .andExpect(status().isBadRequest());
    assertThat(ids(r.recordId())).containsExactly(b);
    assertThat(refs(b)).isEqualTo(1);
    assertThat(jobs(b)).isZero();
    call(9001, json(patch(path(p, r.recordId())), "{\"version\":2,\"content\":\"본문\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.attachments[0].assetId").value(b));
    call(9001, json(patch(path(p, r.recordId())), "{\"version\":3}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("#32-S9 순서·교집합·동일 배열 변경은 유지 자산의 참조·상태·삭제 작업을 바꾸지 않는다")
  void orderedReplacement() throws Exception {
    long p = pet(), a = asset(p, false), b = asset(p, true), c = asset(p, false);
    var r = create(p, "", a, b);
    change(p, r.recordId(), 0, null, b, a);
    assertThat(ids(r.recordId())).containsExactly(b, a);
    assertThat(refs(a)).isEqualTo(1);
    assertThat(refs(b)).isEqualTo(1);
    assertThat(state(a)).isEqualTo("READY");
    assertThat(jobs(a) + jobs(b)).isZero();
    change(p, r.recordId(), 1, null, b, c);
    assertThat(ids(r.recordId())).containsExactly(b, c);
    assertThat(refs(a)).isZero();
    assertThat(state(a)).isEqualTo("DELETING");
    assertThat(jobs(a)).isEqualTo(1);
    assertThat(refs(b)).isEqualTo(1);
    assertThat(refs(c)).isEqualTo(1);
    assertThat(jobs(b) + jobs(c)).isZero();
    change(p, r.recordId(), 2, null, b, c);
    assertThat(refs(b)).isEqualTo(1);
    assertThat(refs(c)).isEqualTo(1);
    call(9001, get(path(p)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].attachments[0].assetId").value(b));
  }

  @Test
  @DisplayName("#32-S10 잘못된 업로더·아이·용도·미완료·만료·삭제 상태는 원자적으로 거부한다")
  void rejectsInvalidAssets() {
    long p = pet(), valid = asset(p, false), otherPet = pet();
    long expired = asset(p, false), deleting = asset(p, false);
    jdbc.update(
        "update media_assets set unattached_expires_at=? where id=?",
        java.sql.Timestamp.from(clock.instant()),
        expired);
    jdbc.update("update media_assets set status='DELETING' where id=?", deleting);
    var invalid =
        List.of(
            asset(p, 9002L, false, "DIARY_ATTACHMENT", true),
            asset(otherPet, false),
            asset(p, 9001L, false, "QUESTION_ANSWER_ATTACHMENT", true),
            asset(p, 9001L, false, "DIARY_ATTACHMENT", false),
            expired,
            deleting);
    for (long bad : invalid) {
      assertThatThrownBy(() -> create(p, "본문", valid, bad)).isInstanceOf(RuntimeException.class);
      assertThat(refs(valid)).isZero();
      assertThat(jobs(valid)).isZero();
    }
    assertThat(
            jdbc.queryForObject("select count(*) from diary_entries where pet_id=?", Long.class, p))
        .isZero();
  }

  @Test
  @DisplayName("#32-S8 #32-S10 첨부 관계 변경·참조 변경 중 실패와 버전 충돌은 전체 원본을 보존한다")
  void rollbackAndVersion() {
    long p = pet(), a = asset(p, false), b = asset(p, false);
    var r = create(p, "", a);
    doAnswer(
            i -> {
              i.callRealMethod();
              throw new IllegalStateException("injected after relation insert");
            })
        .when(links)
        .replace(r.recordId(), List.of(b));
    assertThatThrownBy(() -> change(p, r.recordId(), 0, null, b))
        .isInstanceOf(IllegalStateException.class);
    assertThat(ids(r.recordId())).containsExactly(a);
    assertThat(refs(a)).isEqualTo(1);
    assertThat(refs(b)).isZero();
    assertThat(jobs(a)).isZero();
    reset(links);
    // a를 해제하고 b를 획득하다가 뒤의 미완료 자산 검증에서 실패한다.
    long pending = asset(p, 9001L, false, "DIARY_ATTACHMENT", false);
    assertThatThrownBy(() -> change(p, r.recordId(), 0, null, b, pending))
        .isInstanceOf(RuntimeException.class);
    assertThat(ids(r.recordId())).containsExactly(a);
    assertThat(refs(a)).isEqualTo(1);
    assertThat(refs(b)).isZero();
    assertThat(state(a)).isEqualTo("READY");
    assertThat(jobs(a)).isZero();
    assertThat(get.get(new GetDiaryEntryQuery(9001L, p, r.recordId())).version()).isZero();
    change(p, r.recordId(), 0, null, a);
    assertThatThrownBy(() -> change(p, r.recordId(), 0, null, b))
        .isInstanceOf(com.pawmento.diary.application.exception.DiaryVersionConflictException.class);
    assertThat(ids(r.recordId())).containsExactly(a);
    assertThat(refs(a)).isEqualTo(1);
  }

  @Test
  @DisplayName("#32-S11 #32-S13 재사용 파일은 다른 일기 삭제에서 보존하고 마지막 참조만 삭제 예약한다")
  void reuseAndDelete() {
    long p = pet(), a = asset(p, false);
    var first = create(p, "", a);
    var second = create(p, "", a);
    deleteDiary.delete(new DeleteDiaryEntryCommand(9001L, p, first.recordId(), 0L));
    assertThat(refs(a)).isEqualTo(1);
    assertThat(state(a)).isEqualTo("READY");
    assertThat(jobs(a)).isZero();
    deleteDiary.delete(new DeleteDiaryEntryCommand(9001L, p, second.recordId(), 0L));
    assertThat(refs(a)).isZero();
    assertThat(state(a)).isEqualTo("DELETING");
    assertThat(jobs(a)).isEqualTo(1);
    assertThat(ids(first.recordId())).isEmpty();
    assertThat(ids(second.recordId())).isEmpty();
  }

  @Test
  @DisplayName("#32-S11 부모 권한·실제 연결을 검사하고 이미지/음성 GET·HEAD·Range를 제공한다")
  void fileAuthorization() throws Exception {
    long p = pet(), image = asset(p, false), audio = asset(p, true), unlinked = asset(p, false);
    var r = create(p, "", image, audio);
    var other = create(p, "본문");
    doAnswer(i -> new ByteArrayInputStream("0123456789".getBytes()))
        .when(files)
        .open(anyString(), anyString());
    String base = "/api/v1/diaries/" + r.recordId() + "/attachments/";
    String url = base + audio + "?petId=" + p;
    call(9001, get(url).header("Range", "bytes=2-5"))
        .andExpect(status().isPartialContent())
        .andExpect(content().string("2345"));
    call(9001, head(url))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Length", "10"))
        .andExpect(content().string(""));
    call(9001, get(url).header("Range", "bytes=100-200"))
        .andExpect(status().isRequestedRangeNotSatisfiable());
    call(9001, get(base + image + "?petId=" + p))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "private, no-store"));
    call(9001, get(base + audio + "/thumbnail?petId=" + p)).andExpect(status().isNotFound());
    call(9001, get(base + image + "/thumbnail?petId=" + p))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", "image/jpeg"));
    mvc.perform(get(url)).andExpect(status().isUnauthorized());
    call(9002, get(url)).andExpect(status().isForbidden());
    call(9001, get(base + unlinked + "?petId=" + p)).andExpect(status().isNotFound());
    call(9001, get("/api/v1/diaries/" + other.recordId() + "/attachments/" + image + "?petId=" + p))
        .andExpect(status().isNotFound());
    call(9001, get("/api/v1/media/" + image + "/preview?petId=" + p))
        .andExpect(status().isConflict());
    deleteDiary.delete(new DeleteDiaryEntryCommand(9001L, p, r.recordId(), 0L));
    call(9001, get(url)).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("#32-S10 #32-S11 실제 참조는 count 불일치 시에도 만료 정리·직접 삭제로부터 보호한다")
  void actualReferenceProtection() throws Exception {
    long p = pet(), a = asset(p, false);
    var r = create(p, "", a);
    jdbc.update(
        "update media_assets set reference_count=0,unattached_expires_at=? where id=?",
        java.sql.Timestamp.from(clock.instant()),
        a);
    cleanup.schedule(a);
    call(9001, delete("/api/v1/media/" + a + "?petId=" + p)).andExpect(status().isConflict());
    assertThat(ids(r.recordId())).containsExactly(a);
    assertThat(state(a)).isEqualTo("READY");
    assertThat(jobs(a)).isZero();
  }

  @Test
  @DisplayName("#32-S10 동시 두 일기의 동일 자산 획득은 둘 다 성공하고 정리 선점은 연결을 거부한다")
  void concurrentAcquisitionAndCleanup() throws Exception {
    long p = pet(), a = asset(p, false);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var start = new CountDownLatch(1);
      var first =
          pool.submit(
              () -> {
                start.await();
                return create(p, "", a);
              });
      var second =
          pool.submit(
              () -> {
                start.await();
                return create(p, "", a);
              });
      start.countDown();
      first.get(10, TimeUnit.SECONDS);
      second.get(10, TimeUnit.SECONDS);
    }
    assertThat(refs(a)).isEqualTo(2);
    assertThat(jobs(a)).isZero();
    long expired = asset(p, false);
    jdbc.update(
        "update media_assets set unattached_expires_at=? where id=?",
        java.sql.Timestamp.from(clock.instant()),
        expired);
    cleanup.schedule(expired);
    assertThat(state(expired)).isEqualTo("DELETING");
    assertThatThrownBy(() -> create(p, "", expired)).isInstanceOf(RuntimeException.class);
    assertThat(refs(expired)).isZero();
  }

  @Test
  @DisplayName("#32-S10 참조 획득이 먼저 커밋되면 기다리던 만료 정리는 연결 파일을 보존한다")
  void acquisitionWinsCleanupRace() throws Exception {
    long p = pet(), a = asset(p, false);
    var acquired = new CountDownLatch(1);
    var commit = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      try {
        var write =
            pool.submit(
                () ->
                    tx.execute(
                        status -> {
                          var r = create(p, "", a);
                          jdbc.update(
                              "update media_assets set unattached_expires_at=? where id=?",
                              java.sql.Timestamp.from(clock.instant()),
                              a);
                          acquired.countDown();
                          try {
                            if (!commit.await(10, TimeUnit.SECONDS))
                              throw new AssertionError("commit timeout");
                          } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new AssertionError(e);
                          }
                          return r;
                        }));
        assertThat(acquired.await(10, TimeUnit.SECONDS)).isTrue();
        var cleaner = pool.submit(() -> cleanup.schedule(a));
        org.awaitility.Awaitility.await()
            .atMost(Duration.ofSeconds(8))
            .until(
                () ->
                    jdbc.queryForObject(
                            "select count(*) from pg_stat_activity where pid<>pg_backend_pid() and"
                                + " wait_event_type='Lock' and lower(query) like '%media_assets%'",
                            Long.class)
                        > 0);
        commit.countDown();
        var r = write.get(10, TimeUnit.SECONDS);
        cleaner.get(10, TimeUnit.SECONDS);
        assertThat(ids(r.recordId())).containsExactly(a);
        assertThat(refs(a)).isEqualTo(1);
        assertThat(state(a)).isEqualTo("READY");
        assertThat(jobs(a)).isZero();
      } finally {
        commit.countDown();
      }
    }
  }

  @Test
  @DisplayName("#33-S15 첨부 최대 5개는 허용하고 여섯 번째는 저장 전에 거부한다")
  void attachmentCountLimit() {
    long p = pet();
    Long[] allowed = new Long[5];
    for (int i = 0; i < 5; i++) allowed[i] = asset(p, false);
    var r = create(p, "", allowed);
    assertThat(ids(r.recordId())).hasSize(5);
    var tooMany = new java.util.ArrayList<Long>(List.of(allowed));
    tooMany.add(asset(p, false));
    assertThatThrownBy(() -> new DiaryWriteFeatures(tooMany, false))
        .isInstanceOf(com.pawmento.diary.domain.exception.InvalidDiaryEntryException.class);
    for (long id : allowed) assertThat(refs(id)).isEqualTo(1);
  }

  @Test
  @DisplayName("#32-S10 만료 정리가 먼저 잠그면 기다리던 새 첨부는 삭제 상태를 되살리지 않는다")
  void cleanupWinsAcquisitionRace() throws Exception {
    long p = pet(), a = asset(p, false);
    jdbc.update(
        "update media_assets set unattached_expires_at=? where id=?",
        java.sql.Timestamp.from(clock.instant()),
        a);
    var scheduled = new CountDownLatch(1);
    var commit = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      try {
        var cleaner =
            pool.submit(
                () ->
                    tx.executeWithoutResult(
                        status -> {
                          cleanup.schedule(a);
                          scheduled.countDown();
                          try {
                            if (!commit.await(10, TimeUnit.SECONDS))
                              throw new AssertionError("commit timeout");
                          } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new AssertionError(e);
                          }
                        }));
        assertThat(scheduled.await(10, TimeUnit.SECONDS)).isTrue();
        var write = pool.submit(() -> create(p, "", a));
        org.awaitility.Awaitility.await()
            .atMost(Duration.ofSeconds(8))
            .until(
                () ->
                    jdbc.queryForObject(
                            "select count(*) from pg_stat_activity where pid<>pg_backend_pid() and"
                                + " wait_event_type='Lock' and lower(query) like '%media_assets%'",
                            Long.class)
                        > 0);
        commit.countDown();
        cleaner.get(10, TimeUnit.SECONDS);
        assertThatThrownBy(() -> write.get(10, TimeUnit.SECONDS))
            .isInstanceOf(ExecutionException.class)
            .hasCauseInstanceOf(
                com.pawmento.media.domain.exception.MediaStateConflictException.class);
        assertThat(refs(a)).isZero();
        assertThat(state(a)).isEqualTo("DELETING");
        assertThat(jobs(a)).isEqualTo(1);
        assertThat(
                jdbc.queryForObject(
                    "select count(*) from diary_entries where pet_id=?", Long.class, p))
            .isZero();
      } finally {
        commit.countDown();
      }
    }
  }

  @Test
  @DisplayName("#32-S13 일기 삭제 뒤 자산 저장 실패는 일기·첨부·참조·삭제 작업 모두 롤백한다")
  void deletionRollback() {
    long p = pet(), a = asset(p, false);
    var r = create(p, "", a);
    doAnswer(
            invocation -> {
              MediaAsset changed = invocation.getArgument(0);
              if (changed.getId().equals(a) && changed.getStatus() == MediaUploadStatus.DELETING)
                throw new com.pawmento.media.application.exception
                    .MediaPersistenceUnavailableException(new java.io.IOException("injected"));
              return invocation.callRealMethod();
            })
        .when(save)
        .save(any(MediaAsset.class));
    assertThatThrownBy(
            () -> deleteDiary.delete(new DeleteDiaryEntryCommand(9001L, p, r.recordId(), 0L)))
        .isInstanceOf(
            com.pawmento.media.application.exception.MediaPersistenceUnavailableException.class);
    assertThat(ids(r.recordId())).containsExactly(a);
    assertThat(refs(a)).isEqualTo(1);
    assertThat(state(a)).isEqualTo("READY");
    assertThat(jobs(a)).isZero();
    assertThat(get.get(new GetDiaryEntryQuery(9001L, p, r.recordId())).version()).isZero();
  }
}
