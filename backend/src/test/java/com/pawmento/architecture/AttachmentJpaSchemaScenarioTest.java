package com.pawmento.architecture;

import static org.assertj.core.api.Assertions.*;

import com.pawmento.TestcontainersConfiguration;
import com.pawmento.diary.application.port.in.*;
import com.pawmento.diary.application.port.out.DiaryAttachmentsPort;
import com.pawmento.media.application.port.out.SaveMediaAssetPort;
import com.pawmento.media.domain.*;
import com.pawmento.member.application.port.in.*;
import com.pawmento.pet.application.port.in.*;
import com.pawmento.question.application.port.in.*;
import com.pawmento.question.application.port.out.QuestionAttachmentsPort;
import com.pawmento.support.SignUpTokens;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.function.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(
    properties = {
      "pawmento.auth.jwt-secret=test-only-jwt-secret-0123456789-abcdefghij",
      "pawmento.media.worker.enabled=false"
    })
@Import(TestcontainersConfiguration.class)
class AttachmentJpaSchemaScenarioTest {
  @Autowired JdbcTemplate jdbc;
  @Autowired SignUpTokens signUpTokens;
  @Autowired TransactionTemplate tx;
  @Autowired CreatePetUseCase pets;
  @Autowired SignUpMemberUseCase members;
  @Autowired CreateDiaryEntryUseCase diaries;
  @Autowired EnsureCurrentQuestionPromptUseCase prompts;
  @Autowired CreateQuestionAnswerUseCase answers;
  @Autowired DiaryAttachmentsPort diaryLinks;
  @Autowired QuestionAttachmentsPort answerLinks;
  @Autowired SaveMediaAssetPort media;
  @Autowired Clock clock;

  @Test
  @DisplayName("#31-S16/#32-S9 JPA가 먼저 만든 첨부 테이블에도 복합키·FK·순서 유니크·비음수 제약이 남는다")
  void freshSchemaConstraints() {
    for (var definition :
        Map.of("diary_attachments", "diary", "question_answer_attachment", "answer").entrySet()) {
      String table = definition.getKey(), parent = definition.getValue();
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from pg_constraint where conrelid=cast(? as regclass) and"
                      + " contype='p'",
                  Integer.class,
                  table))
          .isEqualTo(1);
      assertThat(
              jdbc.queryForList(
                  "select a.attname::text from pg_constraint c join pg_attribute a on"
                      + " a.attrelid=c.conrelid and a.attnum=any(c.conkey) where c.conrelid=cast(?"
                      + " as regclass) and c.contype='p'",
                  String.class,
                  table))
          .containsExactlyInAnyOrder(parent + "_id", "asset_id");
      assertThat(
              jdbc.queryForObject(
                  "select confdeltype::text from pg_constraint where conrelid=cast(? as regclass)"
                      + " and conname=?",
                  String.class,
                  table,
                  table + "_" + parent + "_id_fkey"))
          .isEqualTo("c");
      assertThat(
              jdbc.queryForObject(
                  "select confdeltype::text from pg_constraint where conrelid=cast(? as regclass)"
                      + " and conname=?",
                  String.class,
                  table,
                  table + "_asset_id_fkey"))
          .isEqualTo("r");
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from pg_constraint where conrelid=cast(? as regclass) and"
                      + " conname in (?,?) and convalidated",
                  Integer.class,
                  table,
                  table + "_" + parent + "_id_sort_order_key",
                  table + "_sort_order_check"))
          .isEqualTo(2);
    }
  }

  @Test
  @DisplayName("#31-S16/S17/#32-S9 같은 TX에서 읽은 연결을 교체·원복하고 초기화 SQL을 재적용해도 기존 ID·순서·참조를 보존한다")
  void identityMapAndSchemaReapplication() throws Exception {
    long member =
        members
            .signUp(
                signUpTokens.command(
                    UUID.randomUUID() + "@test.example", "Password123!", "보리 가족"))
            .id();
    long pet = pets.create(new CreatePetCommand(member, "보리", "DOG", null, null)).petId();
    var diaryIds =
        List.of(asset(member, pet, "DIARY_ATTACHMENT"), asset(member, pet, "DIARY_ATTACHMENT"));
    var answerIds =
        List.of(
            asset(member, pet, "QUESTION_ANSWER_ATTACHMENT"),
            asset(member, pet, "QUESTION_ANSWER_ATTACHMENT"));
    long diary =
        diaries
            .create(
                new CreateDiaryEntryCommand(
                    member,
                    pet,
                    "다이어리",
                    LocalDate.now(clock),
                    new DiaryWriteFeatures(diaryIds, false),
                    "테스트 일기"))
            .diaryId();
    long prompt =
        prompts
            .ensure(new EnsureCurrentQuestionPromptCommand(member, pet))
            .current()
            .prompt()
            .promptId();
    long answer =
        answers
            .create(new CreateQuestionAnswerCommand(member, pet, prompt, "질문 답변", answerIds))
            .answer()
            .answerId();
    // Seed the existing JDBC storage shape before exercising the JPA mapping.
    tx.executeWithoutResult(
        status -> {
          jdbc.update("delete from diary_attachments where diary_id=?", diary);
          jdbc.update("delete from question_answer_attachment where answer_id=?", answer);
          for (int i = 0; i < diaryIds.size(); i++)
            jdbc.update(
                "insert into diary_attachments(diary_id,asset_id,sort_order) values(?,?,?)",
                diary,
                diaryIds.get(i),
                i);
          for (int i = 0; i < answerIds.size(); i++)
            jdbc.update(
                "insert into question_answer_attachment(answer_id,asset_id,sort_order)"
                    + " values(?,?,?)",
                answer,
                answerIds.get(i),
                i);
        });
    exercise(diaryIds, () -> diaryLinks.find(diary), ids -> diaryLinks.replace(diary, ids));
    exercise(answerIds, () -> answerLinks.find(answer), ids -> answerLinks.replace(answer, ids));
    for (int i = 0; i < 2; i++) {
      for (String name : List.of("diary-schema.sql", "question-progress-schema.sql")) {
        String sql = new ClassPathResource("db/" + name).getContentAsString(StandardCharsets.UTF_8);
        tx.executeWithoutResult(s -> jdbc.execute(sql));
      }
    }
    assertThat(diaryLinks.find(diary)).containsExactlyElementsOf(diaryIds);
    assertThat(answerLinks.find(answer)).containsExactlyElementsOf(answerIds);
    for (long id : java.util.stream.Stream.concat(diaryIds.stream(), answerIds.stream()).toList())
      assertThat(
              jdbc.queryForObject(
                  "select reference_count from media_assets where id=?", Long.class, id))
          .isEqualTo(1);
  }

  void exercise(List<Long> ids, Supplier<List<Long>> read, Consumer<List<Long>> replace) {
    tx.executeWithoutResult(
        s -> {
          assertThat(read.get()).containsExactlyElementsOf(ids);
          replace.accept(List.of(ids.get(1), ids.get(0)));
          assertThat(read.get()).containsExactly(ids.get(1), ids.get(0));
          replace.accept(ids);
          assertThat(read.get()).containsExactlyElementsOf(ids);
        });
    assertThatThrownBy(
            () ->
                tx.executeWithoutResult(
                    s -> {
                      read.get();
                      replace.accept(List.of());
                      throw new IllegalStateException("rollback fixture");
                    }))
        .isInstanceOf(IllegalStateException.class);
    assertThat(read.get()).containsExactlyElementsOf(ids);
  }

  long asset(long member, long pet, String purpose) {
    var now = clock.instant();
    var asset =
        MediaAsset.create(
            member, pet, purpose, "photo.png", "image/png", 10, UUID.randomUUID().toString(), now);
    asset.begin(now);
    asset.received(new StoredUpload(10, "a".repeat(64)), now);
    asset.validated(
        new VerifiedMedia(
            UUID.randomUUID().toString(), "image/png", 10, "a".repeat(64), null, 1, 1, 5L),
        now);
    return media.save(asset).getId();
  }
}
