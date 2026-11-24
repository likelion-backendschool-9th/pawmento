package com.pawmento.diary;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import com.pawmento.TestcontainersConfiguration;
import com.pawmento.diary.application.port.in.*;
import com.pawmento.map.application.exception.*;
import com.pawmento.map.application.port.in.*;
import com.pawmento.map.application.port.in.DiaryLocationInput;
import com.pawmento.map.application.port.out.*;
import com.pawmento.media.application.port.out.SaveMediaAssetPort;
import com.pawmento.media.domain.*;
import com.pawmento.pet.application.port.in.*;
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
import org.springframework.transaction.support.*;

@SpringBootTest(
    properties = {
      "pawmento.auth.jwt-secret=test-only-jwt-secret-0123456789-abcdefghij",
      "pawmento.media.worker.enabled=false"
    })
@Import({TestcontainersConfiguration.class, DiaryScenarioTest.Time.class})
@AutoConfigureMockMvc
class DiaryLocationScenarioTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired CreatePetUseCase pets;
  @Autowired CreateDiaryEntryUseCase create;
  @Autowired EditDiaryEntryUseCase edit;
  @Autowired DeleteDiaryEntryUseCase deleteDiary;
  @MockitoSpyBean com.pawmento.diary.application.port.out.LoadDiaryEntryPort load;
  @Autowired TransactionTemplate tx;
  @Autowired ApplyDiaryLocationUseCase apply;
  @Autowired ReconcileDiaryLocationUseCase reconcile;
  @Autowired GetDiaryLocationUseCase read;
  @Autowired SaveMediaAssetPort assets;
  @MockitoBean LoadAddressPort address;
  @MockitoSpyBean DiaryLocationStorePort store;
  @Autowired Clock clock;

  @BeforeEach
  void addressDefault() {
    when(address.loadByCoordinates(anyDouble(), anyDouble())).thenReturn(Optional.empty());
  }

  long pet() {
    return pets.create(new CreatePetCommand(9001L, "보리", "DOG", null, null)).petId();
  }

  String path(long pet) {
    return "/api/v1/diaries?petId=" + pet;
  }

  String path(long pet, long id) {
    return "/api/v1/diaries/" + id + "?petId=" + pet;
  }

  ResultActions call(long member, MockHttpServletRequestBuilder request) throws Exception {
    return mvc.perform(request.with(jwt().jwt(j -> j.subject("" + member).claim("role", "USER"))));
  }

  MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder b, String json) {
    return b.contentType(MediaType.APPLICATION_JSON).content(json);
  }

  long createManual(long p) throws Exception {
    var result =
        call(
                9001,
                body(
                    post(path(p)),
                    "{\"title\":\"테스트"
                        + " 일기\",\"content\":\"원본\",\"recordedDate\":\"2026-11-17\",\"location\":{\"mode\":\"MANUAL\",\"latitude\":0,\"longitude\":-73.5,\"placeName\":\"공원\"}}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.location.mode").value("MANUAL"))
            .andReturn();
    return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.diaryId"))
        .longValue();
  }

  Map<String, Object> pin(long id) {
    return jdbc.queryForMap("select * from diary_location where diary_id=?", id);
  }

  long pins(long id) {
    return jdbc.queryForObject(
        "select count(*) from diary_location where diary_id=?", Long.class, id);
  }

  @Test
  @DisplayName("#44-S4 일기 생성→본인 핀 조회→위치만 수정→본문 수정→NONE→삭제를 실제 API로 연결한다")
  void flow() throws Exception {
    long p = pet(), id = createManual(p);
    verifyNoInteractions(address);
    call(
            9001,
            get(
                "/api/v1/maps/pins?petId="
                    + p
                    + "&minLatitude=-1&maxLatitude=1&minLongitude=-74&maxLongitude=-73"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pins[0].diaryId").value(id));
    when(address.loadByCoordinates(37.5, 127.5))
        .thenAnswer(
            inv -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              return Optional.of(" 주소 ");
            });
    call(
            9001,
            body(
                patch(path(p, id)),
                "{\"version\":0,\"location\":{\"mode\":\"MANUAL\",\"latitude\":37.5,\"longitude\":127.5}}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(1))
        .andExpect(jsonPath("$.location.placeName").value("주소"));
    call(9001, body(patch(path(p, id)), "{\"version\":1,\"content\":\"본문만\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(2))
        .andExpect(jsonPath("$.location.latitude").value(37.5));
    verify(address, times(1)).loadByCoordinates(anyDouble(), anyDouble());
    assertThat(pin(id).get("source_revision")).isEqualTo(2L);
    call(9001, body(patch(path(p, id)), "{\"version\":2,\"location\":{\"mode\":\"NONE\"}}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(3))
        .andExpect(jsonPath("$.location.mode").value("NONE"));
    assertThat(pins(id)).isZero();
    call(9001, delete(path(p, id) + "&version=3")).andExpect(status().isNoContent());
    call(
            9001,
            body(
                patch(path(p, id)),
                "{\"version\":3,\"location\":{\"mode\":\"MANUAL\",\"latitude\":1,\"longitude\":1}}"))
        .andExpect(status().isNotFound());
    assertThat(pins(id)).isZero();
  }

  @Test
  @DisplayName("#44-S7 주소 없음·255자 초과는 이름 없이 저장하고 내부 오류는 저장 전에 전파한다")
  void addresses() throws Exception {
    long p = pet();
    when(address.loadByCoordinates(1, 2)).thenReturn(Optional.of("가".repeat(256)));
    call(
            9001,
            body(
                post(path(p)),
                "{\"title\":\"테스트"
                    + " 일기\",\"content\":\"기억\",\"recordedDate\":\"2026-11-17\",\"location\":{\"mode\":\"MANUAL\",\"latitude\":1,\"longitude\":2}}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.location.placeName").isEmpty());
    long before =
        jdbc.queryForObject("select count(*) from diary_entries where pet_id=?", Long.class, p);
    when(address.loadByCoordinates(1, 2)).thenThrow(new IllegalStateException("programming error"));
    assertThatThrownBy(
            () ->
                call(
                    9001,
                    body(
                        post(path(p)),
                        "{\"title\":\"테스트"
                            + " 일기\",\"content\":\"기억\",\"recordedDate\":\"2026-11-17\",\"location\":{\"mode\":\"MANUAL\",\"latitude\":1,\"longitude\":2}}")))
        .hasRootCauseInstanceOf(IllegalStateException.class);
    assertThat(
            jdbc.queryForObject("select count(*) from diary_entries where pet_id=?", Long.class, p))
        .isEqualTo(before);
  }

  @Test
  @DisplayName("#44-S13 불일치의 생략 수정은 500, 명시적 정정·NONE·일기 삭제는 성공한다")
  void repairsAndDeletes() throws Exception {
    long p = pet(), id = createManual(p);
    jdbc.update(
        "update diary_location set"
            + " source_revision=999,pet_id=9999,created_by=9002,source_asset_id=1 where diary_id=?",
        id);
    call(9001, body(patch(path(p, id)), "{\"version\":0,\"content\":\"생략\"}"))
        .andExpect(status().isInternalServerError());
    assertThat(jdbc.queryForObject("select version from diary_entries where id=?", Long.class, id))
        .isZero();
    call(
            9001,
            body(
                patch(path(p, id)),
                "{\"version\":0,\"location\":{\"mode\":\"MANUAL\",\"latitude\":2,\"longitude\":3,\"placeName\":\"새"
                    + " 공원\"}}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(1));
    assertThat(pin(id).get("pet_id")).isEqualTo(p);
    assertThat(pin(id).get("created_by")).isEqualTo(9001L);
    assertThat(pin(id).get("source_asset_id")).isNull();
    assertThat(pin(id).get("source_revision")).isEqualTo(1L);
    jdbc.update("update diary_location set source_revision=999 where diary_id=?", id);
    call(9001, body(patch(path(p, id)), "{\"version\":1,\"location\":{\"mode\":\"NONE\"}}"))
        .andExpect(status().isOk());
    assertThat(pins(id)).isZero();
    jdbc.update(
        "insert into"
            + " diary_location(diary_id,pet_id,created_by,latitude,longitude,source_revision,created_at,updated_at)"
            + " values(?,?,9002,1,1,999,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
        id,
        p);
    call(9001, delete(path(p, id) + "&version=2")).andExpect(status().isNoContent());
    assertThat(pins(id)).isZero();
  }

  long asset(long p) {
    var now = clock.instant().minusSeconds(60);
    var value =
        MediaAsset.create(
            9001L,
            p,
            "DIARY_ATTACHMENT",
            "voice",
            "audio/webm",
            10,
            UUID.randomUUID().toString(),
            now);
    value.begin(now);
    value.received(new StoredUpload(10, "a".repeat(64)), now);
    value.validated(
        new VerifiedMedia(
            UUID.randomUUID().toString(), "audio/mp4", 10, "a".repeat(64), 1000L, null, null, null),
        now);
    return assets.save(value).getId();
  }

  @Test
  @DisplayName("#44-S7 지도 쓰기가 실제 반영된 뒤 실패해도 일기·첨부·참조·핀은 모두 롤백한다")
  void rollbackCreateAndEdit() throws Exception {
    long p = pet(), a = asset(p);
    doAnswer(
            inv -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
              inv.callRealMethod();
              throw new DiaryLocationPersistenceUnavailableException(
                  new RuntimeException("injected after write"));
            })
        .when(store)
        .write(anyLong(), anyLong(), anyLong(), anyLong(), any());
    call(
            9001,
            body(
                post(path(p)),
                "{\"title\":\"테스트 일기\",\"content\":\"새"
                    + " 기록\",\"recordedDate\":\"2026-11-17\",\"attachmentIds\":["
                    + a
                    + "],\"location\":{\"mode\":\"MANUAL\",\"latitude\":1,\"longitude\":1,\"placeName\":\"공원\"}}"))
        .andExpect(status().isServiceUnavailable());
    assertThat(
            jdbc.queryForObject("select count(*) from diary_entries where pet_id=?", Long.class, p))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from diary_location where pet_id=?", Long.class, p))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "select reference_count from media_assets where id=?", Long.class, a))
        .isZero();
    reset(store);
    long id = createManual(p);
    var original = pin(id);
    doAnswer(
            inv -> {
              inv.callRealMethod();
              throw new DiaryLocationPersistenceUnavailableException(
                  new RuntimeException("injected"));
            })
        .when(store)
        .write(anyLong(), anyLong(), anyLong(), anyLong(), any());
    call(
            9001,
            body(
                patch(path(p, id)),
                "{\"version\":0,\"content\":\"새 본문\",\"attachmentIds\":["
                    + a
                    + "],\"location\":{\"mode\":\"MANUAL\",\"latitude\":1,\"longitude\":1,\"placeName\":\"새"
                    + " 공원\"}}"))
        .andExpect(status().isServiceUnavailable());
    assertThat(pin(id)).isEqualTo(original);
    assertThat(jdbc.queryForObject("select version from diary_entries where id=?", Long.class, id))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from diary_attachments where diary_id=?", Long.class, id))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "select reference_count from media_assets where id=?", Long.class, a))
        .isZero();
  }

  @Test
  @DisplayName("#44-S7 핀 삭제 후 실패하면 일기와 핀 삭제도 함께 취소한다")
  void rollbackDelete() throws Exception {
    long p = pet(), id = createManual(p);
    var original = pin(id);
    doAnswer(
            inv -> {
              inv.callRealMethod();
              throw new DiaryLocationPersistenceUnavailableException(
                  new RuntimeException("injected"));
            })
        .when(store)
        .remove(id);
    call(9001, delete(path(p, id) + "&version=0")).andExpect(status().isServiceUnavailable());
    assertThat(pin(id)).isEqualTo(original);
    assertThat(jdbc.queryForObject("select count(*) from diary_entries where id=?", Long.class, id))
        .isEqualTo(1L);
  }

  @Test
  @DisplayName("#44-S6 정정 중 관측 상태가 달라지면 409와 부모·지도 전체 롤백이다")
  void staleReconcile() throws Exception {
    long p = pet(), id = createManual(p);
    var original = pin(id);
    doAnswer(
            inv -> {
              jdbc.update("update diary_location set source_revision=99 where diary_id=?", id);
              return inv.callRealMethod();
            })
        .when(store)
        .lock(id);
    call(9001, body(patch(path(p, id)), "{\"version\":0,\"location\":{\"mode\":\"NONE\"}}"))
        .andExpect(status().isConflict());
    assertThat(pin(id)).isEqualTo(original);
    assertThat(jdbc.queryForObject("select version from diary_entries where id=?", Long.class, id))
        .isZero();
  }

  @Test
  @DisplayName("#44-S8 타 작성자 일기 응답·목록에는 좌표가 없고 잘못된 버전/권한은 주소 조회 전에 거부한다")
  void authorAndVersion() throws Exception {
    long p = pet(), id = createManual(p);
    jdbc.update("update diary_entries set created_by=9002 where id=?", id);
    call(9001, get(path(p, id)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.location").isEmpty());
    call(9001, get(path(p)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].location").isEmpty());
    call(
            9001,
            body(
                patch(path(p, id)),
                "{\"version\":0,\"location\":{\"mode\":\"MANUAL\",\"latitude\":1,\"longitude\":2}}"))
        .andExpect(status().isForbidden());
    verifyNoInteractions(address);
  }

  @Test
  @DisplayName("#44-S6 같은 일기의 동시 최초 반영은 APPLIED/DUPLICATE로 수렴하고 계약 위반은 500 계열이다")
  void atomicApply() throws Exception {
    long id = 900000 + pet();
    var c = new ApplyDiaryLocationCommand(id, 1L, 9001L, 0, new DiaryLocationInput(1, 2, "공원"));
    var gate = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var a =
          pool.submit(
              () -> {
                gate.await();
                return tx.execute(s -> apply.apply(c));
              });
      var b =
          pool.submit(
              () -> {
                gate.await();
                return tx.execute(s -> apply.apply(c));
              });
      gate.countDown();
      assertThat(List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(
              DiaryLocationApplyResult.APPLIED, DiaryLocationApplyResult.DUPLICATE);
    }
    assertThat(pins(id)).isEqualTo(1);
    assertThat(
            tx.<DiaryLocationApplyResult>execute(
                s ->
                    apply.apply(
                        new ApplyDiaryLocationCommand(
                            id, 1L, 9001L, 1, new DiaryLocationInput(3, 4, "공원")))))
        .isEqualTo(DiaryLocationApplyResult.APPLIED);
    assertThat(tx.<DiaryLocationApplyResult>execute(s -> apply.apply(c)))
        .isEqualTo(DiaryLocationApplyResult.STALE);
    assertThatThrownBy(
            () ->
                tx.execute(
                    s ->
                        apply.apply(
                            new ApplyDiaryLocationCommand(
                                id, 1L, 9001L, 1, new DiaryLocationInput(5, 6, "다름")))))
        .isInstanceOf(DiaryLocationContractViolation.class);
    assertThatThrownBy(
            () ->
                tx.execute(
                    s ->
                        apply.apply(
                            new ApplyDiaryLocationCommand(
                                id, 2L, 9001L, 2, new DiaryLocationInput(5, 6, null)))))
        .isInstanceOf(DiaryLocationContractViolation.class);
  }

  @Test
  @DisplayName("#44-S7 외부 조정 진입점은 이미 열린 트랜잭션을 거부한다")
  void refusesOuterTransaction() {
    long p = pet();
    assertThatThrownBy(
            () ->
                tx.execute(
                    s ->
                        create.create(
                            new CreateDiaryEntryCommand(
                                9001L, p, "기록", LocalDate.of(2026, 11, 17), "테스트 일기"))))
        .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
  }

  @Test
  @DisplayName("#44-S7 주소 조회 중 다른 수정이 커밋되면 오래된 버전을 재검사해 핀과 원본을 보존한다")
  void addressRace() throws Exception {
    long p = pet(), id = createManual(p);
    when(address.loadByCoordinates(1, 2))
        .thenAnswer(
            inv -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              try (var pool = Executors.newSingleThreadExecutor()) {
                pool.submit(
                        () -> {
                          try {
                            call(
                                    9001,
                                    body(
                                        patch(path(p, id)),
                                        "{\"version\":0,\"content\":\"먼저 저장\"}"))
                                .andExpect(status().isOk());
                          } catch (Exception e) {
                            throw new RuntimeException(e);
                          }
                        })
                    .get(10, TimeUnit.SECONDS);
              }
              return Optional.of("주소");
            });
    call(
            9001,
            body(
                patch(path(p, id)),
                "{\"version\":0,\"location\":{\"mode\":\"MANUAL\",\"latitude\":1,\"longitude\":2}}"))
        .andExpect(status().isConflict());
    assertThat(
            jdbc.queryForObject("select content from diary_entries where id=?", String.class, id))
        .isEqualTo("먼저 저장");
    assertThat(pin(id).get("longitude")).isEqualTo(-73.5);
    assertThat(pin(id).get("source_revision")).isEqualTo(1L);
  }

  @Test
  @DisplayName("#44-S8 잘못된 첨부 소유권·상태는 외부 주소 조회 전에 거부한다")
  void invalidAttachmentBeforeAddress() throws Exception {
    long p = pet(), other = pet(), a = asset(other);
    call(
            9001,
            body(
                post(path(p)),
                "{\"title\":\"테스트"
                    + " 일기\",\"content\":\"기록\",\"recordedDate\":\"2026-11-17\",\"attachmentIds\":["
                    + a
                    + "],\"location\":{\"mode\":\"MANUAL\",\"latitude\":1,\"longitude\":2}}"))
        .andExpect(status().isNotFound());
    var pending =
        MediaAsset.create(
            9001L,
            p,
            "DIARY_ATTACHMENT",
            "voice",
            "audio/webm",
            10,
            UUID.randomUUID().toString(),
            clock.instant());
    long id = assets.save(pending).getId();
    call(
            9001,
            body(
                post(path(p)),
                "{\"title\":\"테스트"
                    + " 일기\",\"content\":\"기록\",\"recordedDate\":\"2026-11-17\",\"attachmentIds\":["
                    + id
                    + "],\"location\":{\"mode\":\"MANUAL\",\"latitude\":1,\"longitude\":2}}"))
        .andExpect(status().isConflict());
    verifyNoInteractions(address);
  }

  @Test
  @DisplayName("#44-S7 마지막 첨부 해제·삭제 작업 등록 후 지도 실패도 부모·참조·작업을 모두 롤백한다")
  void deletionJobRollback() throws Exception {
    long p = pet(), a = asset(p);
    long id =
        create
            .create(
                new CreateDiaryEntryCommand(
                    9001L,
                    p,
                    "기록",
                    LocalDate.of(2026, 11, 17),
                    new DiaryWriteFeatures(List.of(a), DiaryLocationChange.manual(0.0, 1.0, "공원")),
                    "테스트 일기"))
            .diaryId();
    var original = pin(id);
    doAnswer(
            inv -> {
              assertThat(
                      jdbc.queryForObject(
                          "select count(*) from media_validation_jobs where asset_id=? and"
                              + " kind='DELETE'",
                          Long.class,
                          a))
                  .isEqualTo(1L);
              inv.callRealMethod();
              throw new DiaryLocationPersistenceUnavailableException(
                  new RuntimeException("after deletion job"));
            })
        .when(store)
        .remove(id);
    call(9001, delete(path(p, id) + "&version=0")).andExpect(status().isServiceUnavailable());
    assertThat(pin(id)).isEqualTo(original);
    assertThat(
            jdbc.queryForObject(
                "select reference_count from media_assets where id=?", Long.class, a))
        .isEqualTo(1L);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from media_validation_jobs where asset_id=? and kind='DELETE'",
                Long.class,
                a))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from diary_attachments where diary_id=?", Long.class, id))
        .isEqualTo(1L);
  }

  @Test
  @DisplayName("#44-S6 실제 수동 핀이 있는 일기의 수정·삭제 경합도 한 결과만 남고 오래된 수정이 핀을 되살리지 않는다")
  void manualEditDeleteRace() throws Exception {
    long p = pet(), id = createManual(p);
    var barrier = new CyclicBarrier(2);
    doAnswer(
            inv -> {
              var value = inv.callRealMethod();
              if (TransactionSynchronizationManager.isActualTransactionActive())
                barrier.await(8, TimeUnit.SECONDS);
              return value;
            })
        .when(load)
        .findById(id);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var a =
          pool.submit(
              () -> {
                try {
                  edit.edit(
                      new EditDiaryEntryCommand(
                          9001L,
                          p,
                          id,
                          null,
                          null,
                          0L,
                          new DiaryWriteFeatures(
                              null, DiaryLocationChange.manual(5.0, 6.0, "새 공원"))));
                  return (Throwable) null;
                } catch (Throwable failure) {
                  return failure;
                }
              });
      var b =
          pool.submit(
              () -> {
                try {
                  deleteDiary.delete(new DeleteDiaryEntryCommand(9001L, p, id, 0L));
                  return (Throwable) null;
                } catch (Throwable failure) {
                  return failure;
                }
              });
      var x = a.get(12, TimeUnit.SECONDS);
      var y = b.get(12, TimeUnit.SECONDS);
      assertThat((x == null) ^ (y == null)).isTrue();
      assertThat(x == null ? y : x)
          .isInstanceOf(
              com.pawmento.diary.application.exception.DiaryVersionConflictException.class);
    } finally {
      reset(load);
    }
    long parents =
        jdbc.queryForObject("select count(*) from diary_entries where id=?", Long.class, id);
    if (parents == 0) assertThat(pins(id)).isZero();
    else {
      assertThat(pin(id).get("latitude")).isEqualTo(5.0);
      assertThat(pin(id).get("source_revision")).isEqualTo(1L);
    }
    call(
            9001,
            body(
                patch(path(p, id)),
                "{\"version\":0,\"location\":{\"mode\":\"MANUAL\",\"latitude\":7,\"longitude\":8,\"placeName\":\"오래된"
                    + " 요청\"}}"))
        .andExpect(status().is((parents == 0) ? 404 : 409));
    assertThat(pins(id)).isEqualTo(parents);
  }
}
