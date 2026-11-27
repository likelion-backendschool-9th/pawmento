package com.pawmento.diary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import com.pawmento.TestcontainersConfiguration;
import com.pawmento.diary.application.port.out.DiaryMapPort;
import com.pawmento.global.security.AccessTokenCookie;
import com.pawmento.media.application.port.in.ProcessMediaWorkUseCase;
import com.pawmento.media.application.port.out.LoadMediaAssetPort;
import com.pawmento.media.application.port.out.MediaWorkQueuePort;
import com.pawmento.media.application.port.out.SaveMediaAssetPort;
import com.pawmento.media.domain.MediaAsset;
import com.pawmento.media.domain.MediaUploadStatus;
import com.pawmento.media.domain.StoredUpload;
import com.pawmento.media.domain.VerifiedMedia;
import com.pawmento.pet.application.port.in.LockPetOwnerUseCase;
import com.pawmento.question.application.port.out.QuestionAnswerStorePort;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

// 가족 관계는 저장소 fixture가 아니라 실제 초대·가입·회수 API로 만든다.
// 파일은 READY fixture이며 업로드/코덱 검증을 대신하지 않는다. 읽기·참조·실제 파일 삭제는 실제 어댑터다.
@Import(TestcontainersConfiguration.class)
@SpringBootTest(
    properties = {
      "pawmento.auth.jwt-secret=test-only-jwt-secret-0123456789-abcdefghij",
      "pawmento.media.worker.enabled=false"
    })
@AutoConfigureMockMvc
class OwnerRecordDeletionScenarioTest {
  private static final Path ROOT =
      Path.of(System.getProperty("java.io.tmpdir"), "pawmento-family-api-" + UUID.randomUUID());
  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final byte[] CONTENT = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired SaveMediaAssetPort saveAssets;
  @Autowired LoadMediaAssetPort loadAssets;
  @Autowired ProcessMediaWorkUseCase worker;
  @MockitoSpyBean LockPetOwnerUseCase petLocks;
  @MockitoSpyBean DiaryMapPort map;
  @MockitoSpyBean MediaWorkQueuePort jobs;
  @MockitoSpyBean QuestionAnswerStorePort answerStore;

  @AfterEach
  void resetSpies() {
    Mockito.reset(map, jobs, answerStore, petLocks);
  }

  @DynamicPropertySource
  static void storage(DynamicPropertyRegistry registry) {
    registry.add("pawmento.media.root", ROOT::toString);
  }

  @AfterAll
  static void clean() throws Exception {
    if (Files.exists(ROOT))
      try (var files = Files.walk(ROOT)) {
        for (var file : files.sorted(Comparator.reverseOrder()).toList())
          Files.deleteIfExists(file);
      }
  }

  record Actor(Cookie cookie, long id) {}

  record Family(Actor owner, Actor writer, Actor other, long pet) {}

  record Asset(long id, String key) {}

  @ParameterizedTest
  @CsvSource({"ACTIVE,LEAVE", "ACTIVE,REMOVE", "GARDEN,LEAVE", "GARDEN,REMOVE"})
  @DisplayName("#32-S2/S31 #31-S12/S31 #33-S20 소유주는 나간 가족의 첨부 일기·답변을 삭제하고 원 작성자의 핀과 마지막 파일을 정리한다")
  void ownerDeletesDepartedAuthorsRecords(String mode, String removal) throws Exception {
    var f = family(mode);
    var date = LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(1);
    var image = ready(f.writer(), f.pet(), "DIARY_ATTACHMENT", false);
    var audio = ready(f.writer(), f.pet(), "DIARY_ATTACHMENT", true);
    var answerImage = ready(f.writer(), f.pet(), "QUESTION_ANSWER_ATTACHMENT", false);
    var answerAudio = ready(f.writer(), f.pet(), "QUESTION_ANSWER_ATTACHMENT", true);
    var body = new HashMap<String, Object>(diaryBody(date, List.of(image.id(), audio.id())));
    body.put(
        "location",
        Map.of("mode", "MANUAL", "latitude", 37.5, "longitude", 127.0, "placeName", "산책길"));
    long diary =
        number(postJson("/api/v1/diaries?petId=" + f.pet(), f.writer(), body, 201), "$.diaryId");
    long prompt = number(prompt(f.writer(), f.pet()), "$.prompt.promptId");
    long answer =
        number(
            postJson(
                "/api/v1/questions/prompts/" + prompt + "/answers?petId=" + f.pet(),
                f.writer(),
                Map.of(
                    "content",
                    "함께 산책",
                    "attachmentIds",
                    List.of(answerImage.id(), answerAudio.id())),
                201),
            "$.answer.answerId");
    long next = number(prompt(f.owner(), f.pet()), "$.prompt.promptId");
    var diaryPath = "/api/v1/diaries/" + diary + "?petId=" + f.pet();
    var answerPath = "/api/v1/questions/answers/" + answer + "?petId=" + f.pet();
    for (var path : List.of(diaryPath, answerPath)) {
      mvc.perform(
              patch(path)
                  .cookie(f.owner().cookie())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(json(Map.of("version", 0, "content", "타인 수정"))))
          .andExpect(status().isForbidden());
      mvc.perform(delete(path + "&version=0").cookie(f.other().cookie()))
          .andExpect(status().isForbidden());
      mvc.perform(delete(path + "&version=99").cookie(f.owner().cookie()))
          .andExpect(status().isConflict());
    }
    mvc.perform(get(diaryPath).cookie(f.owner().cookie()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.location").isEmpty());
    assertThat(
            jdbc.queryForObject(
                "select created_by from diary_location where diary_id=?", Long.class, diary))
        .isEqualTo(f.writer().id());
    readFiles(
        f.other(), attachmentPaths(f.pet(), diary, answer, image, audio, answerImage, answerAudio));
    if (removal.equals("LEAVE"))
      mvc.perform(
              delete("/api/v1/families/members/me?petId=" + f.pet()).cookie(f.writer().cookie()))
          .andExpect(status().isNoContent());
    else
      mvc.perform(
              delete("/api/v1/families/members/" + f.writer().id() + "?petId=" + f.pet())
                  .cookie(f.owner().cookie()))
          .andExpect(status().isNoContent());
    for (var path : List.of(diaryPath, answerPath)) {
      mvc.perform(delete(path + "&version=0").cookie(f.writer().cookie()))
          .andExpect(status().isForbidden());
      mvc.perform(delete(path + "&version=0").cookie(f.owner().cookie()))
          .andExpect(status().isNoContent());
      mvc.perform(get(path).cookie(f.other().cookie())).andExpect(status().isNotFound());
    }
    assertThat(
            jdbc.queryForObject(
                "select count(*) from diary_location where diary_id=?", Long.class, diary))
        .isZero();
    calendar(f.other(), f.pet(), date, 0);
    assertThat(number(prompt(f.other(), f.pet()), "$.prompt.promptId")).isEqualTo(next);
    for (int i = 0; i < 100 && worker.processNext(); i++) {}
    for (var asset : List.of(image, audio, answerImage, answerAudio)) {
      assertThat(loadAssets.findById(asset.id()).orElseThrow().getStatus())
          .isEqualTo(MediaUploadStatus.DELETED);
      assertThat(ROOT.resolve("files").resolve(asset.key() + ".media")).doesNotExist();
      assertThat(ROOT.resolve("files").resolve(asset.key() + ".thumb")).doesNotExist();
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"ACTIVE", "GARDEN"})
  @DisplayName("#31-S31 모드 전환 뒤 이전 모드 타인 답변 삭제는 409이고 첨부·진행을 보존한다")
  void oldModeCannotBeDeleted(String mode) throws Exception {
    var f = family(mode);
    var asset = ready(f.writer(), f.pet(), "QUESTION_ANSWER_ATTACHMENT", false);
    long prompt = number(prompt(f.writer(), f.pet()), "$.prompt.promptId");
    long answer =
        number(
            postJson(
                "/api/v1/questions/prompts/" + prompt + "/answers?petId=" + f.pet(),
                f.writer(),
                Map.of("content", "함께한 기억", "attachmentIds", List.of(asset.id())),
                201),
            "$.answer.answerId");
    mvc.perform(
            patch("/api/v1/pets/" + f.pet() + "/mode")
                .cookie(f.owner().cookie())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("mode", mode.equals("ACTIVE") ? "GARDEN" : "ACTIVE"))))
        .andExpect(status().isOk());
    long current = number(prompt(f.owner(), f.pet()), "$.prompt.promptId");
    mvc.perform(
            delete("/api/v1/questions/answers/" + answer + "?petId=" + f.pet() + "&version=0")
                .cookie(f.owner().cookie()))
        .andExpect(status().isConflict());
    assertThat(
            jdbc.queryForObject(
                "select count(*) from question_answer where id=?", Long.class, answer))
        .isEqualTo(1L);
    assertThat(loadAssets.findById(asset.id()).orElseThrow().getReferenceCount()).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from media_validation_jobs where asset_id=? and kind='DELETE'",
                Long.class,
                asset.id()))
        .isZero();
    assertThat(number(prompt(f.other(), f.pet()), "$.prompt.promptId")).isEqualTo(current);
  }

  @ParameterizedTest
  @CsvSource({"ACTIVE,DIARY", "GARDEN,DIARY", "ACTIVE,ANSWER", "GARDEN,ANSWER"})
  @DisplayName("#32-S31 #31-S31 #33-S20 텍스트 타인 글과 공유 첨부 삭제는 다른 실제 참조를 보존한다")
  void textAndSharedReferences(String mode, String target) throws Exception {
    var f = family(mode);
    var date = LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(1);
    boolean diary = target.equals("DIARY");
    var asset =
        ready(
            f.writer(), f.pet(), diary ? "DIARY_ATTACHMENT" : "QUESTION_ANSWER_ATTACHMENT", false);
    long first = createRecord(f, diary, List.of(asset.id()));
    long second = createRecord(f, diary, List.of(asset.id()));
    long text = createRecord(f, diary, List.of());
    assertThat(loadAssets.findById(asset.id()).orElseThrow().getReferenceCount()).isEqualTo(2);
    mvc.perform(delete(recordPath(f, diary, first) + "&version=0").cookie(f.owner().cookie()))
        .andExpect(status().isNoContent());
    assertThat(loadAssets.findById(asset.id()).orElseThrow().getReferenceCount()).isEqualTo(1);
    assertThat(ROOT.resolve("files").resolve(asset.key() + ".media")).exists();
    String file =
        (diary ? "/api/v1/diaries/" : "/api/v1/questions/answers/")
            + second
            + "/attachments/"
            + asset.id()
            + (diary ? "" : "/content")
            + "?petId="
            + f.pet();
    mvc.perform(get(file).cookie(f.other().cookie())).andExpect(status().isOk());
    for (long id : List.of(second, text))
      mvc.perform(delete(recordPath(f, diary, id) + "&version=0").cookie(f.owner().cookie()))
          .andExpect(status().isNoContent());
    assertThat(loadAssets.findById(asset.id()).orElseThrow().getReferenceCount()).isZero();
    calendar(f.other(), f.pet(), date, 0);
  }

  @ParameterizedTest
  @ValueSource(strings = {"DIARY_MAP", "DIARY_QUEUE", "ANSWER_QUEUE", "ANSWER_STORE"})
  @DisplayName("#32-S31 #31-S31 첨부 작업·지도·부모 삭제 후 실패는 부모·참조·작업·핀을 함께 롤백한다")
  void ownerDeletionRollsBack(String failure) throws Exception {
    var f = family("ACTIVE");
    boolean diary = failure.startsWith("DIARY");
    var asset =
        ready(
            f.writer(), f.pet(), diary ? "DIARY_ATTACHMENT" : "QUESTION_ANSWER_ATTACHMENT", false);
    long id = createRecord(f, diary, List.of(asset.id()));
    if (diary) {
      mvc.perform(
              patch(recordPath(f, true, id))
                  .cookie(f.writer().cookie())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      json(
                          Map.of(
                              "version",
                              0,
                              "location",
                              Map.of(
                                  "mode",
                                  "MANUAL",
                                  "latitude",
                                  37.5,
                                  "longitude",
                                  127.0,
                                  "placeName",
                                  "공원")))))
          .andExpect(status().isOk());
    }
    var before =
        jdbc.queryForMap(
            "select * from " + (diary ? "diary_entries" : "question_answer") + " where id=?", id);
    var pin =
        diary ? jdbc.queryForMap("select * from diary_location where diary_id=?", id) : Map.of();
    if (failure.endsWith("QUEUE"))
      Mockito.doAnswer(
              inv -> {
                inv.callRealMethod();
                throw new IllegalStateException("injected queue failure");
              })
          .when(jobs)
          .enqueueDeletion(ArgumentMatchers.eq(asset.id()), ArgumentMatchers.any());
    else if (failure.equals("DIARY_MAP"))
      Mockito.doAnswer(
              inv -> {
                inv.callRealMethod();
                throw new IllegalStateException("injected map failure");
              })
          .when(map)
          .reconcile(
              ArgumentMatchers.eq(id),
              ArgumentMatchers.anyLong(),
              ArgumentMatchers.anyLong(),
              ArgumentMatchers.anyLong(),
              ArgumentMatchers.any(),
              ArgumentMatchers.isNull());
    else
      Mockito.doAnswer(
              inv -> {
                inv.callRealMethod();
                throw new IllegalStateException("injected answer failure");
              })
          .when(answerStore)
          .delete(ArgumentMatchers.any());
    // MockMvc may propagate an unhandled injected exception; either way the DB must roll back.
    try {
      var result =
          mvc.perform(
                  delete(recordPath(f, diary, id) + "&version=" + (diary ? 1 : 0))
                      .cookie(f.owner().cookie()))
              .andReturn();
      assertThat(result.getResponse().getStatus()).isGreaterThanOrEqualTo(500);
    } catch (jakarta.servlet.ServletException e) {
      assertThat(e).hasRootCauseInstanceOf(IllegalStateException.class);
    }
    assertThat(
            jdbc.queryForMap(
                "select * from " + (diary ? "diary_entries" : "question_answer") + " where id=?",
                id))
        .isEqualTo(before);
    assertThat(loadAssets.findById(asset.id()).orElseThrow().getReferenceCount()).isEqualTo(1);
    assertThat(loadAssets.findById(asset.id()).orElseThrow().getStatus())
        .isEqualTo(MediaUploadStatus.READY);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from media_validation_jobs where asset_id=? and kind='DELETE'",
                Long.class,
                asset.id()))
        .isZero();
    assertThat(
            jdbc.queryForList(
                "select asset_id from "
                    + (diary
                        ? "diary_attachments where diary_id=?"
                        : "question_answer_attachment where answer_id=?"),
                Long.class,
                id))
        .containsExactly(asset.id());
    if (diary)
      assertThat(jdbc.queryForMap("select * from diary_location where diary_id=?", id))
          .isEqualTo(pin);
  }

  @ParameterizedTest
  @CsvSource({
    "true,true,false",
    "true,false,false",
    "false,true,false",
    "false,false,false",
    "true,true,true",
    "true,false,true",
    "false,true,true",
    "false,false,true"
  })
  @DisplayName("#32-S32 #31-S31 소유주 삭제와 작성자 수정·삭제는 pet 잠금 순서와 버전으로 직렬화된다")
  void ownerDeleteAndAuthorMutationRace(boolean diary, boolean editFirst, boolean competingDelete)
      throws Exception {
    var f = family("ACTIVE");
    var asset =
        ready(
            f.writer(), f.pet(), diary ? "DIARY_ATTACHMENT" : "QUESTION_ANSWER_ATTACHMENT", false);
    long id = createRecord(f, diary, List.of(asset.id()));
    String path = recordPath(f, diary, id);
    var held = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var firstCall = new AtomicBoolean(true);
    var queued = new CountDownLatch(1);
    Mockito.doAnswer(
            inv -> {
              boolean first = firstCall.compareAndSet(true, false);
              if (!first) queued.countDown();
              Object result = inv.callRealMethod();
              if (first) {
                held.countDown();
                assertThat(release.await(15, TimeUnit.SECONDS)).isTrue();
              }
              return result;
            })
        .when(AopTestUtils.<LockPetOwnerUseCase>getUltimateTargetObject(petLocks))
        .lock(ArgumentMatchers.any());
    try (var pool = Executors.newFixedThreadPool(2)) {
      try {
        Callable<Integer> edit =
            () ->
                mvc.perform(
                        patch(path)
                            .cookie(f.writer().cookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("version", 0, "content", "수정한 기억"))))
                    .andReturn()
                    .getResponse()
                    .getStatus();
        Callable<Integer> delete =
            () ->
                mvc.perform(delete(path + "&version=0").cookie(f.owner().cookie()))
                    .andReturn()
                    .getResponse()
                    .getStatus();
        Callable<Integer> writerMutation =
            competingDelete
                ? () ->
                    mvc.perform(delete(path + "&version=0").cookie(f.writer().cookie()))
                        .andReturn()
                        .getResponse()
                        .getStatus()
                : edit;
        boolean edited = editFirst && !competingDelete;
        var first = pool.submit(editFirst ? writerMutation : delete);
        assertThat(held.await(10, TimeUnit.SECONDS)).isTrue();
        var second = pool.submit(editFirst ? delete : writerMutation);
        assertThat(queued.await(10, TimeUnit.SECONDS)).isTrue();
        release.countDown();
        assertThat(first.get(15, TimeUnit.SECONDS)).isEqualTo(edited ? 200 : 204);
        assertThat(second.get(15, TimeUnit.SECONDS)).isEqualTo(edited || diary ? 409 : 404);
        assertThat(loadAssets.findById(asset.id()).orElseThrow().getReferenceCount())
            .isEqualTo(edited ? 1 : 0);
        if (edited)
          mvc.perform(get(path).cookie(f.other().cookie()))
              .andExpect(status().isOk())
              .andExpect(jsonPath("$.content").value("수정한 기억"))
              .andExpect(jsonPath("$.version").value(1));
        else mvc.perform(get(path).cookie(f.other().cookie())).andExpect(status().isNotFound());
      } finally {
        release.countDown();
      }
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"DIARY", "ANSWER"})
  @DisplayName("#32-S31 #31-S31 다른 아이·없는 기록·비가족 관리자 삭제는 거부하고 원본을 보존한다")
  void deletionCannotCrossPetOrAdminBoundary(String target) throws Exception {
    var f = family("ACTIVE");
    boolean diary = target.equals("DIARY");
    long id = createRecord(f, diary, List.of());
    long otherPet =
        number(
            postJson("/api/v1/pets", f.owner(), Map.of("name", "다른 아이", "species", "CAT"), 201),
            "$.petId");
    String base = (diary ? "/api/v1/diaries/" : "/api/v1/questions/answers/");
    mvc.perform(delete(base + id + "?petId=" + otherPet + "&version=0").cookie(f.owner().cookie()))
        .andExpect(status().isNotFound());
    mvc.perform(
            delete(base + Long.MAX_VALUE + "?petId=" + f.pet() + "&version=0")
                .cookie(f.owner().cookie()))
        .andExpect(status().isNotFound());
    var outsider = member("비가족");
    mvc.perform(
            delete(recordPath(f, diary, id) + "&version=0")
                .with(
                    org.springframework.security.test.web.servlet.request
                        .SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(j -> j.subject(Long.toString(outsider.id())))
                        .authorities(
                            new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                "ROLE_ADMIN"))))
        .andExpect(status().isForbidden());
    mvc.perform(get(recordPath(f, diary, id)).cookie(f.writer().cookie()))
        .andExpect(status().isOk());
  }

  long createRecord(Family f, boolean diary, List<Long> assets) throws Exception {
    if (diary)
      return number(
          diary(f.writer(), f.pet(), LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(1), assets),
          "$.diaryId");
    long prompt = number(prompt(f.writer(), f.pet()), "$.prompt.promptId");
    return number(
        postJson(
            "/api/v1/questions/prompts/" + prompt + "/answers?petId=" + f.pet(),
            f.writer(),
            Map.of("content", "함께한 하루", "attachmentIds", assets),
            201),
        "$.answer.answerId");
  }

  String recordPath(Family f, boolean diary, long id) {
    return (diary ? "/api/v1/diaries/" : "/api/v1/questions/answers/") + id + "?petId=" + f.pet();
  }

  Family family(String mode) throws Exception {
    var owner = member("소유주");
    var writer = member("작성 가족");
    var other = member("함께 가족");
    long pet =
        number(
            postJson("/api/v1/pets", owner, Map.of("name", "보리", "species", "DOG"), 201),
            "$.petId");
    mvc.perform(
            patch("/api/v1/pets/" + pet + "/mode")
                .cookie(owner.cookie())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("mode", mode))))
        .andExpect(status().isOk());
    join(owner, writer, pet);
    join(owner, other, pet);
    mvc.perform(get("/api/v1/families?petId=" + pet).cookie(other.cookie()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.members.length()").value(2));
    return new Family(owner, writer, other, pet);
  }

  Actor member(String nickname) throws Exception {
    String email = "family-recording-" + UUID.randomUUID() + "@test.example";
    var credentials = Map.of("email", email, "password", "Password123!");
    postJson(
        "/api/v1/auth/signup",
        null,
        Map.of("email", email, "password", "Password123!", "nickname", nickname),
        201);
    var login = postJson("/api/v1/auth/login", null, credentials, 200);
    var cookie = login.getResponse().getCookie(AccessTokenCookie.NAME);
    assertThat(cookie).isNotNull();
    return new Actor(cookie, number(login, "$.id"));
  }

  void join(Actor issuer, Actor member, long pet) throws Exception {
    var invite = postJson("/api/v1/families/invite-codes", issuer, Map.of("petId", pet), 201);
    postJson("/api/v1/families/members", member, Map.of("code", value(invite, "$.code")), 200);
  }

  MvcResult diary(Actor actor, long pet, LocalDate date, List<Long> attachments) throws Exception {
    return postJson("/api/v1/diaries?petId=" + pet, actor, diaryBody(date, attachments), 201);
  }

  Map<String, Object> diaryBody(LocalDate date, List<Long> attachments) {
    return Map.of(
        "title",
        "함께한 하루",
        "content",
        "가족과 함께 쓴 일기",
        "recordedDate",
        date.toString(),
        "attachmentIds",
        attachments);
  }

  void calendar(Actor actor, long pet, LocalDate date, int count) throws Exception {
    mvc.perform(
            get("/api/v1/diaries/calendar?petId="
                    + pet
                    + "&month="
                    + date.toString().substring(0, 7))
                .cookie(actor.cookie()))
        .andExpect(status().isOk())
        .andExpect(
            count == 0
                ? jsonPath("$.days.length()").value(0)
                : jsonPath("$.days[0].count").value(count));
  }

  MvcResult prompt(Actor actor, long pet) throws Exception {
    var result =
        mvc.perform(post("/api/v1/questions/prompts/current?petId=" + pet).cookie(actor.cookie()))
            .andReturn();
    assertThat(result.getResponse().getStatus()).isIn(200, 201);
    return result;
  }

  MvcResult postJson(String path, Actor actor, Object body, int statusCode) throws Exception {
    var request = post(path).contentType(MediaType.APPLICATION_JSON).content(json(body));
    if (actor != null) request.cookie(actor.cookie());
    return mvc.perform(request).andExpect(status().is(statusCode)).andReturn();
  }

  Object value(MvcResult result, String path) throws Exception {
    return JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), path);
  }

  long number(MvcResult result, String path) throws Exception {
    return ((Number) value(result, path)).longValue();
  }

  String json(Object value) {
    return JSON.writeValueAsString(value);
  }

  Asset ready(Actor actor, long pet, String purpose, boolean audio) throws Exception {
    var now = Instant.now().minusSeconds(60);
    var key = UUID.randomUUID().toString();
    var asset =
        MediaAsset.create(
            actor.id(),
            pet,
            purpose,
            "가족 기록 파일",
            audio ? "audio/webm" : "image/png",
            10,
            UUID.randomUUID().toString(),
            now);
    asset.begin(now);
    asset.received(new StoredUpload(10, "a".repeat(64)), now);
    asset.validated(
        new VerifiedMedia(
            key,
            audio ? "audio/mp4" : "image/png",
            10,
            "a".repeat(64),
            audio ? 1000L : null,
            audio ? null : 1,
            audio ? null : 1,
            audio ? null : 5L),
        now);
    long id = saveAssets.save(asset).getId();
    Files.createDirectories(ROOT.resolve("files"));
    Files.write(ROOT.resolve("files").resolve(key + ".media"), CONTENT);
    if (!audio)
      Files.write(ROOT.resolve("files").resolve(key + ".thumb"), new byte[] {1, 2, 3, 4, 5});
    return new Asset(id, key);
  }

  List<String> attachmentPaths(
      long pet,
      long diary,
      long answer,
      Asset diaryImage,
      Asset diaryAudio,
      Asset answerImage,
      Asset answerAudio) {
    String d = "/api/v1/diaries/" + diary + "/attachments/";
    String a = "/api/v1/questions/answers/" + answer + "/attachments/";
    return List.of(
        d + diaryImage.id() + "?petId=" + pet,
        d + diaryImage.id() + "/thumbnail?petId=" + pet,
        d + diaryAudio.id() + "?petId=" + pet,
        a + answerImage.id() + "/content?petId=" + pet,
        a + answerImage.id() + "/thumbnail?petId=" + pet,
        a + answerAudio.id() + "/content?petId=" + pet);
  }

  void readFiles(Actor actor, List<String> paths) throws Exception {
    for (String path : paths) {
      mvc.perform(get(path).cookie(actor.cookie())).andExpect(status().isOk());
      mvc.perform(head(path).cookie(actor.cookie())).andExpect(status().isOk());
    }
    for (int index : List.of(2, 5))
      mvc.perform(get(paths.get(index)).cookie(actor.cookie()).header("Range", "bytes=0-3"))
          .andExpect(status().isPartialContent())
          .andExpect(header().string("Content-Range", "bytes 0-3/10"))
          .andExpect(content().bytes(new byte[] {1, 2, 3, 4}));
  }
}
