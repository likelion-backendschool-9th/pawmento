package com.pawmento.diary;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.pawmento.TestcontainersConfiguration;
import com.pawmento.diary.application.port.in.*;
import com.pawmento.family.application.port.out.SavePetFamilyMemberPort;
import com.pawmento.family.domain.PetFamilyMember;
import com.pawmento.global.exception.PermissionDeniedException;
import com.pawmento.media.application.port.in.*;
import com.pawmento.media.application.port.out.SaveMediaAssetPort;
import com.pawmento.media.domain.*;
import com.pawmento.member.application.port.in.*;
import com.pawmento.pet.application.port.in.*;
import com.pawmento.question.application.port.in.*;
import java.time.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

// 가족 변경 API가 사용하는 pet 행 잠금 순서를 실제 DB 트랜잭션으로 재현한다.
// 초대·내보내기 API 자체의 통합 검사는 가족 담당 후속 구현 이후에 수행한다.
@SpringBootTest(
    properties = {
      "pawmento.auth.jwt-secret=test-only-jwt-secret-0123456789-abcdefghij",
      "pawmento.media.worker.enabled=false"
    })
@Import(TestcontainersConfiguration.class)
class FamilyRecordingConcurrencyTest {
  @Autowired JdbcTemplate jdbc;
  @Autowired TransactionTemplate tx;
  @Autowired SignUpMemberUseCase members;
  @Autowired CreatePetUseCase pets;
  @Autowired SavePetFamilyMemberPort family;
  @Autowired CreateDiaryEntryUseCase diaries;
  @Autowired EditDiaryEntryUseCase editDiary;
  @Autowired DeleteDiaryEntryUseCase deleteDiary;
  @Autowired EditQuestionAnswerUseCase editAnswer;
  @Autowired DeleteQuestionAnswerUseCase deleteAnswer;
  @Autowired SaveMediaAssetPort media;
  @Autowired CreateQuestionAnswerUseCase answers;
  @Autowired EnsureCurrentQuestionPromptUseCase prompts;
  @Autowired CreateMediaUploadUseCase uploads;
  @MockitoSpyBean LockPetOwnerUseCase locks;
  long owner, writer, pet, prompt, recordId, assetId, replacementId;
  Map<String, Object> original;

  @BeforeEach
  void setup() {
    owner = member();
    writer = member();
    pet = pets.create(new CreatePetCommand(owner, "보리", "DOG", null, null)).petId();
    family.save(PetFamilyMember.join(pet, writer, Instant.now()));
    prompt =
        prompts
            .ensure(new EnsureCurrentQuestionPromptCommand(owner, pet))
            .current()
            .prompt()
            .promptId();
  }

  long member() {
    return members
        .signUp(new SignUpMemberCommand(UUID.randomUUID() + "@test.example", "Password123!", "가족"))
        .id();
  }

  @ParameterizedTest
  @CsvSource({
    "DIARY,true",
    "DIARY,false",
    "ANSWER,true",
    "ANSWER,false",
    "UPLOAD,true",
    "UPLOAD,false",
    "ASSIGN,true",
    "ASSIGN,false",
    "DIARY_EDIT,true",
    "DIARY_EDIT,false",
    "DIARY_DELETE,true",
    "DIARY_DELETE,false",
    "ANSWER_EDIT,true",
    "ANSWER_EDIT,false",
    "ANSWER_DELETE,true",
    "ANSWER_DELETE,false",
    "ANSWER_REPLACE,true",
    "ANSWER_REPLACE,false"
  })
  @DisplayName("#32-S32/#31-S31/#33-S20 쓰기와 가족 회수는 pet 행 잠금 순서대로 처리하고 회수 후 저장을 거부한다")
  void revocationRace(String target, boolean writeFirst) throws Exception {
    prepareMutation(target);
    var holding = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    if (writeFirst) {
      doAnswer(
              inv -> {
                Object result = inv.callRealMethod();
                holding.countDown();
                waitFor(release);
                return result;
              })
          .when(
              org.springframework.test.util.AopTestUtils
                  .<LockPetOwnerUseCase>getUltimateTargetObject(locks))
          .lock(any());
    }
    try (var pool = Executors.newFixedThreadPool(2)) {
      try {
        Future<Throwable> written;
        Future<?> revoked;
        if (writeFirst) {
          written = pool.submit(() -> outcome(() -> write(target)));
          waitFor(holding);
          revoked = pool.submit(() -> revoke(null, null));
        } else {
          revoked = pool.submit(() -> revoke(holding, release));
          waitFor(holding);
          written = pool.submit(() -> outcome(() -> write(target)));
        }
        await()
            .atMost(Duration.ofSeconds(8))
            .until(
                () ->
                    jdbc.queryForObject(
                            "select count(*) from pg_stat_activity where pid <> pg_backend_pid()"
                                + " and wait_event_type='Lock' and lower(query) like '%from"
                                + " pets%for update%'",
                            Long.class)
                        > 0);
        assertThat(written.isDone()).isFalse();
        assertThat(revoked.isDone()).isFalse();
        release.countDown();
        Throwable failure = written.get(10, TimeUnit.SECONDS);
        revoked.get(10, TimeUnit.SECONDS);
        if (writeFirst) assertThat(failure).isNull();
        else assertThat(failure).isInstanceOf(PermissionDeniedException.class);
        assertThatThrownBy(() -> write(target)).isInstanceOf(PermissionDeniedException.class);
        String table =
            switch (target) {
              case "DIARY", "DIARY_EDIT", "DIARY_DELETE" -> "diary_entries";
              case "ANSWER", "ANSWER_EDIT", "ANSWER_DELETE", "ANSWER_REPLACE" -> "question_answer";
              case "UPLOAD" -> "media_assets";
              default -> "question_prompt";
            };
        boolean mutation = target.contains("_");
        long expected =
            target.equals("ASSIGN")
                ? 1
                : mutation ? (writeFirst && target.endsWith("DELETE") ? 0 : 1) : writeFirst ? 1 : 0;
        assertThat(
                jdbc.queryForObject(
                    "select count(*) from " + table + " where pet_id=?", Long.class, pet))
            .isEqualTo(expected);
        if (mutation) verifyMutation(target, writeFirst);
      } finally {
        release.countDown();
      }
    }
  }

  void revoke(CountDownLatch holding, CountDownLatch release) {
    tx.executeWithoutResult(
        status -> {
          jdbc.queryForObject("select id from pets where id=? for update", Long.class, pet);
          jdbc.update("delete from pet_family_members where pet_id=? and member_id=?", pet, writer);
          if (holding != null) {
            holding.countDown();
            waitFor(release);
          }
        });
  }

  void write(String target) {
    switch (target) {
      case "DIARY" ->
          diaries.create(
              new CreateDiaryEntryCommand(writer, pet, "함께한 하루", LocalDate.of(2026, 11, 17)));
      case "ANSWER" -> answers.create(new CreateQuestionAnswerCommand(writer, pet, prompt, "산책"));
      case "UPLOAD" ->
          uploads.create(
              new CreateMediaUploadCommand(
                  writer, pet, "DIARY_ATTACHMENT", "산책.png", "image/png", 10, false));
      case "ASSIGN" -> prompts.ensure(new EnsureCurrentQuestionPromptCommand(writer, pet));
      case "DIARY_EDIT" ->
          editDiary.edit(new EditDiaryEntryCommand(writer, pet, recordId, "변경", null, 0L));
      case "DIARY_DELETE" ->
          deleteDiary.delete(new DeleteDiaryEntryCommand(writer, pet, recordId, 0L));
      case "ANSWER_EDIT" ->
          editAnswer.edit(new EditQuestionAnswerCommand(writer, pet, recordId, "변경", 0L));
      case "ANSWER_DELETE" ->
          deleteAnswer.delete(new DeleteQuestionAnswerCommand(writer, pet, recordId, 0L));
      // 답변은 유지하고 첨부만 바꾼다. 위 ANSWER_EDIT는 첨부 목록을 넘기지 않아 연결 교체를 타지 않는다.
      case "ANSWER_REPLACE" ->
          editAnswer.edit(
              new EditQuestionAnswerCommand(
                  writer, pet, recordId, "변경", 0L, List.of(replacementId)));
      default -> throw new IllegalArgumentException(target);
    }
  }

  void prepareMutation(String target) {
    if (!target.contains("_")) return;
    boolean diary = target.startsWith("DIARY");
    String purpose = diary ? "DIARY_ATTACHMENT" : "QUESTION_ANSWER_ATTACHMENT";
    assetId = readyAsset(purpose);
    if (target.equals("ANSWER_REPLACE")) replacementId = readyAsset(purpose);
    recordId =
        diary
            ? diaries
                .create(
                    new CreateDiaryEntryCommand(
                        writer,
                        pet,
                        "원본",
                        LocalDate.of(2026, 11, 17),
                        new DiaryWriteFeatures(
                            List.of(assetId), DiaryLocationChange.manual(37.5, 127.0, "산책길"))))
                .diaryId()
            : answers
                .create(
                    new CreateQuestionAnswerCommand(writer, pet, prompt, "원본", List.of(assetId)))
                .answer()
                .answerId();
    original =
        jdbc.queryForMap(
            "select * from " + (diary ? "diary_entries" : "question_answer") + " where id=?",
            recordId);
  }

  long readyAsset(String purpose) {
    var now = Instant.now().minusSeconds(60);
    var asset =
        MediaAsset.create(
            writer, pet, purpose, "사진.png", "image/png", 10, UUID.randomUUID().toString(), now);
    asset.begin(now);
    asset.received(new StoredUpload(10, "a".repeat(64)), now);
    asset.validated(
        new VerifiedMedia(
            UUID.randomUUID().toString(), "image/png", 10, "a".repeat(64), null, 1, 1, 5L),
        now);
    return media.save(asset).getId();
  }

  void verifyMutation(String target, boolean writeFirst) {
    boolean diary = target.startsWith("DIARY");
    boolean deleted = writeFirst && target.endsWith("DELETE");
    String table = diary ? "diary_entries" : "question_answer";
    String links = diary ? "diary_attachments" : "question_answer_attachment";
    String parent = diary ? "diary_id" : "answer_id";
    if (!deleted) {
      var current = jdbc.queryForMap("select * from " + table + " where id=?", recordId);
      if (!writeFirst) assertThat(current).isEqualTo(original);
      else {
        assertThat(current.get("content")).isEqualTo("변경");
        assertThat(((Number) current.get("version")).longValue()).isEqualTo(1L);
        for (String field :
            diary
                ? List.of("pet_id", "created_by", "recorded_date", "created_at")
                : List.of(
                    "pet_id",
                    "prompt_id",
                    "author_member_id",
                    "author_name_snapshot",
                    "answered_at",
                    "answered_date"))
          assertThat(current.get(field)).as(field).isEqualTo(original.get(field));
      }
    }
    // 첨부 교체가 먼저 끝나면 기존 사진은 연결·참조가 풀리고 삭제 작업이 생긴다. 회수가 먼저면 둘 다 그대로다.
    boolean replaced = writeFirst && target.equals("ANSWER_REPLACE");
    boolean released = deleted || replaced;
    assertThat(
            jdbc.queryForList(
                "select asset_id from " + links + " where " + parent + "=?",
                Long.class,
                recordId))
        .containsExactlyElementsOf(
            deleted ? List.of() : List.of(replaced ? replacementId : assetId));
    assertAsset(assetId, released ? 0L : 1L, released);
    if (target.equals("ANSWER_REPLACE")) assertAsset(replacementId, replaced ? 1L : 0L, false);
    if (diary) {
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from diary_location where diary_id=?", Long.class, recordId))
          .isEqualTo(deleted ? 0L : 1L);
      if (!deleted) {
        var pin =
            jdbc.queryForMap(
                "select created_by,latitude,longitude,source_revision from diary_location where"
                    + " diary_id=?",
                recordId);
        assertThat(((Number) pin.get("created_by")).longValue()).isEqualTo(writer);
        assertThat(((Number) pin.get("latitude")).doubleValue()).isEqualTo(37.5);
        assertThat(((Number) pin.get("longitude")).doubleValue()).isEqualTo(127.0);
        assertThat(((Number) pin.get("source_revision")).longValue())
            .isEqualTo(writeFirst ? 1L : 0L);
      }
    } else {
      assertThat(
              jdbc.queryForObject(
                  "select current_prompt_id from question_progress where pet_id=? and"
                      + " mode='ACTIVE'",
                  Long.class,
                  pet))
          .isNull();
      assertThat(
              jdbc.queryForObject(
                  "select completed_at from question_prompt where id=?",
                  java.sql.Timestamp.class,
                  prompt))
          .isNotNull();
    }
  }

  void assertAsset(long id, long references, boolean deletionQueued) {
    assertThat(
            jdbc.queryForObject(
                "select reference_count from media_assets where id=?", Long.class, id))
        .as("reference_count of %d", id)
        .isEqualTo(references);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from media_validation_jobs where asset_id=? and kind='DELETE'",
                Long.class,
                id))
        .as("deletion job of %d", id)
        .isEqualTo(deletionQueued ? 1L : 0L);
  }

  static void waitFor(CountDownLatch latch) {
    try {
      assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError(e);
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
