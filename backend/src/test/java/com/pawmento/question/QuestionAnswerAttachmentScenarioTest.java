package com.pawmento.question;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import com.pawmento.TestcontainersConfiguration;
import com.pawmento.media.application.port.out.*;
import com.pawmento.media.application.service.MediaCleanupTransactions;
import com.pawmento.media.domain.*;
import com.pawmento.member.application.port.in.*;
import com.pawmento.pet.application.port.in.*;
import com.pawmento.question.application.exception.QuestionPersistenceUnavailableException;
import com.pawmento.question.application.port.out.*;
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
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
class QuestionAnswerAttachmentScenarioTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired CreatePetUseCase pets;
  @Autowired ChangePetModeUseCase modes;
  @Autowired DeletePetUseCase deletePet;
  @Autowired com.pawmento.pet.application.port.out.DeletePetPort petStorage;
  @Autowired SignUpMemberUseCase members;
  @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
  @MockitoBean Clock clock;
  @MockitoBean SelectQuestionPort selection;
  @MockitoSpyBean QuestionProgressStorePort progress;
  @MockitoSpyBean QuestionAnswerStorePort answers;
  @MockitoSpyBean GetMemberUseCase memberDetails;
  @Autowired SaveMediaAssetPort saveMedia;
  @Autowired com.pawmento.question.application.port.in.GetQuestionAnswerUseCase detail;
  @MockitoSpyBean QuestionAttachmentsPort links;
  @Autowired MediaCleanupTransactions cleanup;
  @MockitoSpyBean QuestionMediaPort media;
  @MockitoSpyBean ReadMediaFilePort files;
  long member, pet;
  List<Map<String, Object>> pool;

  @BeforeEach
  void setup() {
    when(clock.instant()).thenReturn(Instant.parse("2026-11-20T14:59:00Z"));
    when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));
    when(clock.withZone(any())).thenReturn(clock);
    when(selection.select(anyList())).thenAnswer(inv -> ((List<?>) inv.getArgument(0)).getFirst());
    member =
        members
            .signUp(
                new SignUpMemberCommand(
                    UUID.randomUUID() + "@test.example", "Password123!", "보리 가족"))
            .id();
    pet = pets.create(new CreatePetCommand(member, "보리", "DOG", null, null)).petId();
    pool = jdbc.queryForList("select id,content,enabled from question");
  }

  @AfterEach
  void restorePool() {
    for (var row : pool)
      jdbc.update(
          "update question set content=?,enabled=? where id=?",
          row.get("content"),
          row.get("enabled"),
          row.get("id"));
  }

  ResultActions call(MockHttpServletRequestBuilder req) throws Exception {
    return as(member, req);
  }

  ResultActions as(long actor, MockHttpServletRequestBuilder req) throws Exception {
    return mvc.perform(req.with(jwt().jwt(j -> j.subject("" + actor).claim("role", "USER"))));
  }

  Map<String, Object> json(ResultActions action) throws Exception {
    return JsonPath.read(action.andReturn().getResponse().getContentAsString(), "$");
  }

  long ensure() throws Exception {
    var result =
        json(
            call(post("/api/v1/questions/prompts/current?petId=" + pet))
                .andExpect(status().isCreated()));
    return ((Number) ((Map<?, ?>) result.get("prompt")).get("promptId")).longValue();
  }

  String createPath(long prompt) {
    return "/api/v1/questions/prompts/" + prompt + "/answers?petId=" + pet;
  }

  String answerPath(long id) {
    return "/api/v1/questions/answers/" + id + "?petId=" + pet;
  }

  ResultActions create(long prompt) throws Exception {
    return call(
        post(createPath(prompt))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"content\":\"  공을 쫓으며 신나게 뛰었어요.  \"}"));
  }

  long id(Map<String, Object> result) {
    return ((Number) ((Map<?, ?>) result.get("answer")).get("answerId")).longValue();
  }

  long count(String table) {
    return jdbc.queryForObject(
        "select count(*) from " + table + " where pet_id=?", Long.class, pet);
  }

  void pending(Long expected) {
    assertThat(
            jdbc.queryForObject(
                "select current_prompt_id from question_progress where pet_id=? and mode='ACTIVE'",
                Long.class,
                pet))
        .isEqualTo(expected);
  }

  long asset(boolean audio) {
    return asset(pet, member, audio, "QUESTION_ANSWER_ATTACHMENT", true);
  }

  long asset(long petId, long owner, boolean audio, String purpose, boolean ready) {
    var now = clock.instant().minusSeconds(60);
    var a =
        MediaAsset.create(
            owner,
            petId,
            purpose,
            audio ? "voice-source.webm" : "photo-source.png",
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
    return saveMedia.save(a).getId();
  }

  ResultActions attach(long prompt, Long... ids) throws Exception {
    return call(
        post(createPath(prompt))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"content\":\"공을 쫓은 날\",\"attachmentIds\":" + List.of(ids) + "}"));
  }

  long refs(long id) {
    return jdbc.queryForObject(
        "select reference_count from media_assets where id=?", Long.class, id);
  }

  long jobs(long id) {
    return jdbc.queryForObject(
        "select count(*) from media_validation_jobs where asset_id=? and kind='DELETE'",
        Long.class,
        id);
  }

  @Test
  @DisplayName("#31-S16/S18 사진·음성 연결·목록·순서 교체·첨부만 수정·삭제는 참조와 버전을 함께 갱신하고 등록일·진행을 보존한다")
  void attachmentLifecycle() throws Exception {
    long prompt = ensure(), image = asset(false), audio = asset(true);
    long answer =
        id(
            json(
                attach(prompt, image, audio)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.answer.attachments[0].type").value("IMAGE"))
                    .andExpect(
                        jsonPath("$.answer.attachments[0].filename").value("photo-source.png"))
                    .andExpect(
                        jsonPath("$.answer.attachments[1].filename").value("voice-source.webm"))
                    .andExpect(jsonPath("$.answer.attachments[1].type").value("AUDIO"))
                    .andExpect(jsonPath("$.answer.attachments[0].available").value(true))));
    assertThat(refs(image)).isEqualTo(1);
    assertThat(refs(audio)).isEqualTo(1);
    long next = ensure();
    when(clock.instant()).thenReturn(Instant.parse("2026-11-23T16:00:00Z"));
    call(patch(answerPath(answer))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"version\":0,\"attachmentIds\":[" + audio + "," + image + "]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(1))
        .andExpect(jsonPath("$.answeredDate").value("2026-11-20"))
        .andExpect(jsonPath("$.attachments[0].assetId").value(audio))
        .andExpect(jsonPath("$.attachments[1].sortOrder").value(1))
        .andExpect(jsonPath("$.attachments[0].filename").value("voice-source.webm"));
    call(get(answerPath(answer)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.attachments[1].filename").value("photo-source.png"));
    pending(next);
    call(get("/api/v1/questions/answers?petId=" + pet))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].attachments[0].assetId").value(audio))
        .andExpect(jsonPath("$.items[0].attachments[0].filename").value("voice-source.webm"));
    call(patch(answerPath(answer))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"version\":1,\"content\":\"본문만 변경\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.attachments.length()").value(2));
    call(patch(answerPath(answer))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"version\":2,\"attachmentIds\":[]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.attachments").isEmpty());
    assertThat(refs(image) + refs(audio)).isZero();
    assertThat(jobs(image)).isEqualTo(1);
    assertThat(jobs(audio)).isEqualTo(1);
    call(delete(answerPath(answer) + "&version=3")).andExpect(status().isNoContent());
    pending(next);
  }

  @Test
  @DisplayName("#31-S16 첨부 null·중복·6개·잘못된 ID와 빈 본문은 거부하며 다섯 개와 사진/음성 혼합은 허용한다")
  void validation() throws Exception {
    long prompt = ensure(), a = asset(false);
    for (String ids : List.of("null", "[0]", "[\"1\"]", "[" + a + "," + a + "]", "[1,2,3,4,5,6]"))
      call(post(createPath(prompt))
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"content\":\"답변\",\"attachmentIds\":" + ids + "}"))
          .andExpect(status().isBadRequest());
    call(post(createPath(prompt))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"content\":\" \",\"attachmentIds\":[" + a + "]}"))
        .andExpect(status().isBadRequest());
    Long[] ids = new Long[] {a, asset(true), asset(false), asset(true), asset(false)};
    attach(prompt, ids).andExpect(status().isCreated());
  }

  @Test
  @DisplayName("#31-S16 타인·다른 아이·다이어리 용도·없는 파일은 404이고 미완료·만료 파일은 409로 거부한다")
  void scopeAndState() throws Exception {
    long prompt = ensure(),
        other = pets.create(new CreatePetCommand(member, "다른 아이", "DOG", null, null)).petId();
    for (long id :
        List.of(
            Long.MAX_VALUE,
            asset(pet, member + 1, false, "QUESTION_ANSWER_ATTACHMENT", true),
            asset(other, member, false, "QUESTION_ANSWER_ATTACHMENT", true),
            asset(pet, member, false, "DIARY_ATTACHMENT", true)))
      attach(prompt, id).andExpect(status().isNotFound());
    attach(prompt, asset(pet, member, false, "QUESTION_ANSWER_ATTACHMENT", false))
        .andExpect(status().isConflict());
    long expired = asset(false);
    jdbc.update(
        "update media_assets set unattached_expires_at=? where id=?",
        java.sql.Timestamp.from(clock.instant()),
        expired);
    attach(prompt, expired).andExpect(status().isConflict());
    assertThat(count("question_answer")).isZero();
    pending(prompt);
  }

  @Test
  @DisplayName("#31-S17 참조 획득과 진행 갱신 뒤 실패하면 답변·첨부·참조·완료를 모두 롤백한다")
  void createRollback() throws Exception {
    long prompt = ensure(), a = asset(false);
    doAnswer(
            inv -> {
              inv.callRealMethod();
              throw new QuestionPersistenceUnavailableException(new RuntimeException("injected"));
            })
        .when(progress)
        .saveProgress(any());
    attach(prompt, a).andExpect(status().isServiceUnavailable());
    assertThat(count("question_answer")).isZero();
    assertThat(refs(a)).isZero();
    assertThat(jobs(a)).isZero();
    pending(prompt);
    assertThat(
            jdbc.queryForObject(
                "select completed_at from question_prompt where id=?", Instant.class, prompt))
        .isNull();
  }

  @Test
  @DisplayName("#31-S17/S18 첨부 교체와 삭제 뒤 장애가 나면 원래 본문·연결·참조·삭제 작업·버전을 복구한다")
  void changeRollback() throws Exception {
    long prompt = ensure(),
        a = asset(false),
        b = asset(true),
        answer = id(json(attach(prompt, a).andExpect(status().isCreated())));
    doAnswer(
            inv -> {
              inv.callRealMethod();
              throw new QuestionPersistenceUnavailableException(new RuntimeException("injected"));
            })
        .when(media)
        .change(eq(member), eq(pet), anyList(), anyList());
    call(patch(answerPath(answer))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"version\":0,\"content\":\"바뀐 본문\",\"attachmentIds\":[" + b + "]}"))
        .andExpect(status().isServiceUnavailable());
    assertThat(links.find(answer)).containsExactly(a);
    assertThat(refs(a)).isEqualTo(1);
    assertThat(refs(b)).isZero();
    assertThat(jobs(a)).isZero();
    call(delete(answerPath(answer) + "&version=0")).andExpect(status().isServiceUnavailable());
    call(get(answerPath(answer)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(0))
        .andExpect(jsonPath("$.content").value("공을 쫓은 날"));
    assertThat(links.find(answer)).containsExactly(a);
    assertThat(jobs(a)).isZero();
  }

  @Test
  @DisplayName("#31-S18 부모 권한·연결 검사 뒤 사진·썸네일·음성 Range/HEAD를 비공개로 제공한다")
  void privateFiles() throws Exception {
    long prompt = ensure(),
        image = asset(false),
        audio = asset(true),
        unlinked = asset(false),
        answer = id(json(attach(prompt, image, audio).andExpect(status().isCreated())));
    doAnswer(inv -> new ByteArrayInputStream("0123456789".getBytes()))
        .when(files)
        .open(anyString(), anyString());
    String base = "/api/v1/questions/answers/" + answer + "/attachments/",
        url = base + audio + "/content?petId=" + pet;
    call(get(url).header("Range", "bytes=2-5"))
        .andExpect(status().isPartialContent())
        .andExpect(content().string("2345"));
    call(head(url))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Length", "10"))
        .andExpect(content().string(""));
    call(get(base + image + "/thumbnail?petId=" + pet))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", "image/jpeg"));
    call(get(base + audio + "/thumbnail?petId=" + pet)).andExpect(status().isNotFound());
    call(get(base + image + "/content?petId=" + pet))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "private, no-store"));
    call(get(base + unlinked + "/content?petId=" + pet)).andExpect(status().isNotFound());
    mvc.perform(get(url)).andExpect(status().isUnauthorized());
    as(member + 1, get(url)).andExpect(status().isForbidden());
    call(delete(answerPath(answer) + "&version=0")).andExpect(status().isNoContent());
    call(get(url)).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("#31-S18 실제 첨부가 있으면 참조 수가 0으로 잘못돼도 정리·직접 삭제로부터 보호하고 공유 파일은 마지막 연결 제거 때만 삭제한다")
  void actualLinksAndSharedAssets() throws Exception {
    long a = asset(false),
        first = id(json(attach(ensure(), a).andExpect(status().isCreated()))),
        second = id(json(attach(ensure(), a).andExpect(status().isCreated())));
    assertThat(refs(a)).isEqualTo(2);
    jdbc.update(
        "update media_assets set reference_count=0,unattached_expires_at=? where id=?",
        java.sql.Timestamp.from(clock.instant()),
        a);
    cleanup.schedule(a);
    call(delete("/api/v1/media/" + a + "?petId=" + pet)).andExpect(status().isConflict());
    assertThat(jobs(a)).isZero();
    call(get(answerPath(first)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.attachments[0].available").value(false))
        .andExpect(jsonPath("$.attachments[0].contentUrl").isEmpty());
    call(delete(answerPath(first) + "&version=0")).andExpect(status().isNoContent());
    assertThat(jobs(a)).isZero();
    call(delete(answerPath(second) + "&version=0")).andExpect(status().isNoContent());
    assertThat(jobs(a)).isEqualTo(1);
  }

  @Test
  @DisplayName("#31-S17/S18 정리가 먼저 자산을 잠그면 기다리던 답변 저장은 삭제된 자산을 되살리지 않는다")
  void cleanupWinsRace() throws Exception {
    long prompt = ensure(), a = asset(false);
    jdbc.update(
        "update media_assets set unattached_expires_at=? where id=?",
        java.sql.Timestamp.from(clock.instant()),
        a);
    var scheduled = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var cleaner =
          executor.submit(
              () ->
                  new TransactionTemplate(transactions)
                      .executeWithoutResult(
                          tx -> {
                            cleanup.schedule(a);
                            scheduled.countDown();
                            try {
                              if (!release.await(10, TimeUnit.SECONDS))
                                throw new AssertionError("해제 신호 없음");
                            } catch (InterruptedException ex) {
                              Thread.currentThread().interrupt();
                              throw new AssertionError(ex);
                            }
                          }));
      try {
        assertThat(scheduled.await(10, TimeUnit.SECONDS)).isTrue();
        var writer = executor.submit(() -> attach(prompt, a).andReturn().getResponse().getStatus());
        org.awaitility.Awaitility.await()
            .atMost(Duration.ofSeconds(5))
            .until(
                () ->
                    jdbc.queryForObject(
                            "select count(*) from pg_stat_activity where pid<>pg_backend_pid() and"
                                + " wait_event_type='Lock' and lower(query) like '%media_assets%'",
                            Integer.class)
                        > 0);
        assertThat(writer.isDone()).isFalse();
        release.countDown();
        cleaner.get(10, TimeUnit.SECONDS);
        assertThat(writer.get(10, TimeUnit.SECONDS)).isEqualTo(409);
      } finally {
        release.countDown();
      }
    }
    assertThat(count("question_answer")).isZero();
    assertThat(refs(a)).isZero();
    assertThat(jobs(a)).isEqualTo(1);
    pending(prompt);
  }

  @Test
  @DisplayName("#31-S9/S16 동일 첨부 순서 PATCH는 자산 잠금·행 교체·참조 변경·답변 저장 없이 현재 값만 반환한다")
  void unchangedAttachmentsSkipWrites() throws Exception {
    long a = asset(false),
        b = asset(true),
        answer = id(json(attach(ensure(), a, b).andExpect(status().isCreated())));
    var original = json(call(get(answerPath(answer))).andExpect(status().isOk()));
    clearInvocations(answers, links, media);
    var result =
        json(
            call(patch(answerPath(answer))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"version\":0,\"attachmentIds\":[" + a + "," + b + "]}"))
                .andExpect(status().isOk()));
    assertThat(result).isEqualTo(original);
    verify(answers, never()).save(any());
    verify(links, never()).replace(anyLong(), anyList());
    verify(media, never()).lock(anyLong(), anyLong(), anyList());
    verify(media, never()).change(anyLong(), anyLong(), anyList(), anyList());
    assertThat(refs(a)).isEqualTo(1);
    assertThat(refs(b)).isEqualTo(1);
  }

  @Test
  @DisplayName("#31-S13/S18 파일 쿼리는 petId만 검사하고 미지원 키의 중복·잘못된 값은 무시한다")
  void fileQueryScope() throws Exception {
    long a = asset(false), answer = id(json(attach(ensure(), a).andExpect(status().isCreated())));
    doAnswer(inv -> new ByteArrayInputStream("0123456789".getBytes()))
        .when(files)
        .open(anyString(), anyString());
    String url =
        "/api/v1/questions/answers/" + answer + "/attachments/" + a + "/content?petId=" + pet;
    call(get(url + "&page=bad&page=other&version=x&version=y&mode=unknown"))
        .andExpect(status().isOk());
    call(get(url + "&petId=" + pet)).andExpect(status().isBadRequest());
    call(get(answerPath(answer) + "&version=x&version=y")).andExpect(status().isOk());
    call(get("/api/v1/questions/answers?petId=" + pet + "&version=x&version=y"))
        .andExpect(status().isOk());
    call(get("/api/v1/questions/answers?petId=" + pet + "&page=0&page=1"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("#31-S13/S16 상세의 답변 조회와 첨부 조회 사이 PATCH가 커밋돼도 같은 RR 스냅샷을 반환한다")
  void detailSnapshot() throws Exception {
    long a = asset(false),
        b = asset(true),
        answer = id(json(attach(ensure(), a).andExpect(status().isCreated())));
    var loaded = new CountDownLatch(1);
    var committed = new CountDownLatch(1);
    doAnswer(
            inv -> {
              var result = inv.callRealMethod();
              assertThat(
                      jdbc.queryForObject(
                          "select current_setting('transaction_isolation')", String.class))
                  .isEqualTo("repeatable read");
              assertThat(
                      jdbc.queryForObject(
                          "select current_setting('transaction_read_only')", String.class))
                  .isEqualTo("on");
              loaded.countDown();
              if (!committed.await(10, TimeUnit.SECONDS))
                throw new AssertionError("writer commit timeout");
              return result;
            })
        .when(answers)
        .find(pet, answer);
    try (var executor = Executors.newSingleThreadExecutor()) {
      var reading =
          executor.submit(() -> json(call(get(answerPath(answer))).andExpect(status().isOk())));
      try {
        assertThat(loaded.await(10, TimeUnit.SECONDS)).isTrue();
        call(patch(answerPath(answer))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":0,\"attachmentIds\":[" + b + "]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.version").value(1));
        committed.countDown();
        var result = reading.get(10, TimeUnit.SECONDS);
        assertThat(((Number) result.get("version")).longValue()).isZero();
        var asset = (Map<?, ?>) ((List<?>) result.get("attachments")).getFirst();
        assertThat(((Number) asset.get("assetId")).longValue()).isEqualTo(a);
        assertThat(asset.get("available")).isEqualTo(true);
      } finally {
        committed.countDown();
      }
    }
    call(get(answerPath(answer)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(1))
        .andExpect(jsonPath("$.attachments[0].assetId").value(b));
  }

  @Test
  @DisplayName("#31-S13 기존 READ_COMMITTED 트랜잭션에서 상세를 호출해도 별도 RR 읽기를 사용한다")
  void detailSuspendsOuterTransaction() throws Exception {
    long a = asset(false), answer = id(json(attach(ensure(), a).andExpect(status().isCreated())));
    var outer = new TransactionTemplate(transactions);
    outer.setIsolationLevel(
        org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
    var callerConnection = new java.util.concurrent.atomic.AtomicInteger();
    doAnswer(
            inv -> {
              assertThat(jdbc.queryForObject("select pg_backend_pid()", Integer.class))
                  .isNotEqualTo(callerConnection.get());
              assertThat(
                      jdbc.queryForObject(
                          "select current_setting('transaction_isolation')", String.class))
                  .isEqualTo("repeatable read");
              return inv.callRealMethod();
            })
        .when(answers)
        .find(pet, answer);
    outer.executeWithoutResult(
        tx -> {
          callerConnection.set(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
          var result =
              detail.get(
                  new com.pawmento.question.application.port.in.GetQuestionAnswerQuery(
                      member, pet, answer));
          assertThat(result.version()).isZero();
        });
  }
}
