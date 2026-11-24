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
import com.pawmento.member.application.port.in.*;
import com.pawmento.pet.application.port.in.*;
import com.pawmento.question.application.port.in.*;
import java.time.*;
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
  @Autowired CreateQuestionAnswerUseCase answers;
  @Autowired EnsureCurrentQuestionPromptUseCase prompts;
  @Autowired CreateMediaUploadUseCase uploads;
  @MockitoSpyBean LockPetOwnerUseCase locks;
  long owner, writer, pet, prompt;

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
    "ASSIGN,false"
  })
  @DisplayName("#32-S32/#31-S31/#33-S20 쓰기와 가족 회수는 pet 행 잠금 순서대로 처리하고 회수 후 저장을 거부한다")
  void revocationRace(String target, boolean writeFirst) throws Exception {
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
              case "DIARY" -> "diary_entries";
              case "ANSWER" -> "question_answer";
              case "UPLOAD" -> "media_assets";
              default -> "question_prompt";
            };
        long expected = target.equals("ASSIGN") ? 1 : writeFirst ? 1 : 0;
        assertThat(
                jdbc.queryForObject(
                    "select count(*) from " + table + " where pet_id=?", Long.class, pet))
            .isEqualTo(expected);
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
      default -> throw new IllegalArgumentException(target);
    }
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
