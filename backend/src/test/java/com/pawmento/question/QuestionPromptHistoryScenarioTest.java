package com.pawmento.question;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.pawmento.TestcontainersConfiguration;
import com.pawmento.family.application.port.out.SavePetFamilyMemberPort;
import com.pawmento.family.domain.PetFamilyMember;
import com.pawmento.media.application.port.out.SaveMediaAssetPort;
import com.pawmento.media.domain.*;
import com.pawmento.member.application.port.in.*;
import com.pawmento.pet.application.port.in.*;
import com.pawmento.question.application.port.in.*;
import com.pawmento.question.application.port.out.*;
import com.pawmento.question.domain.QuestionMode;
import com.pawmento.question.domain.exception.InvalidQuestionAnswerException;
import jakarta.persistence.EntityManagerFactory;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(
    properties = {
      "pawmento.auth.jwt-secret=test-only-jwt-secret-0123456789-abcdefghij",
      "pawmento.media.worker.enabled=false",
      "spring.jpa.properties.hibernate.generate_statistics=true",
      "logging.level.org.hibernate.stat=OFF",
      "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF"
    })
@Import({TestcontainersConfiguration.class, QuestionPromptHistoryScenarioTest.Time.class})
@AutoConfigureMockMvc
class QuestionPromptHistoryScenarioTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired SignUpMemberUseCase members;
  @Autowired CreatePetUseCase pets;
  @Autowired ChangePetModeUseCase modes;
  @Autowired SavePetFamilyMemberPort families;
  @Autowired EnsureCurrentQuestionPromptUseCase ensure;
  @Autowired CreateQuestionAnswerUseCase create;
  @Autowired DeleteQuestionAnswerUseCase delete;
  @Autowired ListQuestionPromptsUseCase lists;
  @Autowired GetQuestionPromptUseCase details;
  @Autowired SaveMediaAssetPort assets;
  @Autowired PlatformTransactionManager manager;
  @Autowired EntityManagerFactory emf;
  @Autowired TestClock clock;
  @MockitoSpyBean LoadQuestionPromptHistoryPort history;
  @MockitoSpyBean CheckQuestionPetAccessPort access;
  @MockitoSpyBean QuestionAnswerStorePort answers;
  @MockitoSpyBean QuestionMediaPort media;
  long owner, writer, pet;

  @TestConfiguration
  static class Time {
    @Bean
    @Primary
    TestClock historyClock() {
      return new TestClock();
    }
  }

  static class TestClock extends Clock {
    final AtomicReference<Instant> now =
        new AtomicReference<>(Instant.parse("2026-11-24T00:00:00Z"));

    public Instant instant() {
      return now.get();
    }

    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    public Clock withZone(ZoneId zone) {
      return Clock.fixed(instant(), zone);
    }
  }

  @BeforeEach
  void setup() {
    clock.now.set(Instant.parse("2026-11-24T00:00:00Z"));
    owner = member("보호자");
    writer = member("함께하는 가족");
    pet = pets.create(new CreatePetCommand(owner, "보리", "DOG", null, null)).petId();
    families.save(PetFamilyMember.join(pet, writer, clock.instant()));
  }

  long member(String name) {
    return members
        .signUp(new SignUpMemberCommand(UUID.randomUUID() + "@test.example", "Password123!", name))
        .id();
  }

  long prompt() {
    return ensure
        .ensure(new EnsureCurrentQuestionPromptCommand(owner, pet))
        .current()
        .prompt()
        .promptId();
  }

  QuestionAnswerResult answer(long actor, long prompt) {
    return create.create(new CreateQuestionAnswerCommand(actor, pet, prompt, "함께한 기억")).answer();
  }

  ListQuestionPromptsQuery query(long actor, QuestionMode mode, int page, int size) {
    return new ListQuestionPromptsQuery(actor, pet, mode, page, size);
  }

  GetQuestionPromptQuery detail(long actor, long prompt, int page, int size) {
    return new GetQuestionPromptQuery(actor, pet, prompt, page, size);
  }

  ResultActions read(long actor, String suffix) throws Exception {
    return mvc.perform(
        get("/api/v1/questions/prompts" + suffix).with(jwt().jwt(j -> j.subject("" + actor))));
  }

  String item(long prompt) {
    return "/" + prompt + "?petId=" + pet;
  }

  @Test
  @DisplayName("#31-S32 모드별 회차·미답변·배정시각과 ID 정렬·빈 페이지·long offset을 조회한다")
  void pagesAndModes() throws Exception {
    assertThat(lists.list(query(owner, null, 0, 20)).items()).isEmpty();
    long first = prompt();
    answer(owner, first);
    long second = prompt();
    modes.changeMode(new ChangePetModeCommand(owner, pet, "GARDEN"));
    long garden = prompt();
    // 같은 배정 시각에서도 모드별 sequence가 아닌 ID로 정렬한다.
    var all = lists.list(query(writer, null, 0, 2));
    assertThat(all.items())
        .extracting(QuestionPromptHistoryItem::promptId)
        .containsExactly(garden, second);
    assertThat(all.totalElements()).isEqualTo(3);
    assertThat(all.totalPages()).isEqualTo(2);
    assertThat(all.items().getFirst().answerCount()).isZero();
    assertThat(all.items().getFirst().completedAt()).isNull();
    assertThat(lists.list(query(owner, QuestionMode.ACTIVE, 0, 20)).items())
        .extracting(QuestionPromptHistoryItem::promptId)
        .containsExactly(second, first);
    var differentPet = pets.create(new CreatePetCommand(owner, "다른 아이", "DOG", null, null)).petId();
    ensure.ensure(new EnsureCurrentQuestionPromptCommand(owner, differentPet));
    read(owner, "?petId=" + pet + "&page=2147483647&size=100")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isEmpty())
        .andExpect(jsonPath("$.totalElements").value(3))
        .andExpect(jsonPath("$.totalPages").value(1))
        .andExpect(jsonPath("$.page").value(Integer.MAX_VALUE))
        .andExpect(jsonPath("$.size").value(100));
    read(owner, "?petId=" + pet + "&mode=ACTIVE&page=1&size=1")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].promptId").value(first))
        .andExpect(jsonPath("$.totalElements").value(2));
    assertThat(lists.list(query(writer, null, 10, 1)).totalElements()).isEqualTo(3);
  }

  @Test
  @DisplayName("#31-S33 가족별 내 답변·과거 추가 답변·삭제 후 재작성·반대 모드 작성 불가를 계산한다")
  void personalState() {
    long first = prompt();
    var original = answer(owner, first);
    long next = prompt();
    var mine = details.get(detail(owner, first, 0, 20)).prompt();
    var family = details.get(detail(writer, first, 0, 20)).prompt();
    assertThat(mine.myAnswerId()).isEqualTo(original.answerId());
    assertThat(mine.canAnswer()).isFalse();
    assertThat(family.myAnswerId()).isNull();
    assertThat(family.canAnswer()).isTrue();
    var added = answer(writer, first);
    assertThat(details.get(detail(writer, first, 0, 20)).prompt().answerCount()).isEqualTo(2);
    delete.delete(new DeleteQuestionAnswerCommand(writer, pet, added.answerId(), 0L));
    assertThat(details.get(detail(writer, first, 0, 20)).prompt().canAnswer()).isTrue();
    answer(writer, first);
    modes.changeMode(new ChangePetModeCommand(owner, pet, "GARDEN"));
    var old = details.get(detail(owner, next, 0, 20)).prompt();
    assertThat(old.myAnswerId()).isNull();
    assertThat(old.canAnswer()).isFalse();
    assertThat(lists.list(query(writer, null, 0, 20)).items()).allMatch(p -> !p.canAnswer());
  }

  @Test
  @DisplayName("#31-S34/S35 답변 페이지 밖 내 답변과 떠난 가족의 이름·사진·음성을 보존하고 접근 회수는 구분한다")
  void detailPaginationAndDepartedAuthor() throws Exception {
    long prompt = prompt(), photo = asset(writer, false), audio = asset(writer, true);
    var old =
        create
            .create(
                new CreateQuestionAnswerCommand(
                    writer, pet, prompt, "떠난 가족의 기억", List.of(photo, audio)))
            .answer();
    clock.now.set(clock.instant().plusSeconds(10));
    var recent = answer(owner, prompt);
    jdbc.update("delete from pet_family_members where pet_id=? and member_id=?", pet, writer);
    var page = details.get(detail(owner, prompt, 1, 1));
    assertThat(page.prompt().myAnswerId()).isEqualTo(recent.answerId());
    assertThat(page.prompt().answerCount()).isEqualTo(2);
    assertThat(page.answers().totalElements()).isEqualTo(2);
    assertThat(page.answers().items())
        .extracting(QuestionAnswerResult::answerId)
        .containsExactly(old.answerId());
    var saved = page.answers().items().getFirst();
    assertThat(saved.author().displayName()).isEqualTo("함께하는 가족");
    assertThat(saved.attachments())
        .extracting(QuestionAnswerResult.Attachment::assetId)
        .containsExactly(photo, audio);
    read(owner, item(prompt) + "&page=1&size=1")
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.answers.items[0].attachments[0].contentUrl")
                .value(
                    "/api/v1/questions/answers/"
                        + old.answerId()
                        + "/attachments/"
                        + photo
                        + "/content?petId="
                        + pet));
    read(owner, item(prompt) + "&page=2147483647&size=100")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.prompt.answerCount").value(2))
        .andExpect(jsonPath("$.prompt.myAnswerId").value(recent.answerId()))
        .andExpect(jsonPath("$.answers.items").isEmpty())
        .andExpect(jsonPath("$.answers.totalElements").value(2));
    read(writer, item(prompt)).andExpect(status().isForbidden());
    read(writer, "?petId=" + pet).andExpect(status().isForbidden());
    for (long id : List.of(photo, audio)) {
      mvc.perform(
              head("/api/v1/questions/answers/"
                      + old.answerId()
                      + "/attachments/"
                      + id
                      + "/content?petId="
                      + pet)
                  .with(jwt().jwt(j -> j.subject("" + writer))))
          .andExpect(status().isForbidden());
      // 실제 파일 읽기를 대체하는 포트 스텁. 접근 검사와 부모·미디어 메타데이터는 실제 경로를 탄다.
      doReturn(
              new GetQuestionFileResult(
                  "기록",
                  id == photo ? "image/png" : "audio/mp4",
                  3,
                  () -> new java.io.ByteArrayInputStream(new byte[] {1, 2, 3})))
          .when(media)
          .file(eq(pet), eq(id), eq(false));
      mvc.perform(
              head("/api/v1/questions/answers/"
                      + old.answerId()
                      + "/attachments/"
                      + id
                      + "/content?petId="
                      + pet)
                  .with(jwt().jwt(j -> j.subject("" + owner))))
          .andExpect(status().isOk());
      assertThat(
              jdbc.queryForObject(
                  "select reference_count from media_assets where id=?", Long.class, id))
          .isEqualTo(1L);
    }
  }

  @Test
  @DisplayName("#31-S35 웹과 직접 입력에서 권한·없는 회차·ID·중복/인코딩/범위 검증과 current 경로를 유지한다")
  void boundaries() throws Exception {
    long prompt = prompt(), stranger = member("관리자");
    jdbc.update("update member set role='ADMIN' where id=?", stranger);
    for (String suffix : List.of("?petId=" + pet, item(prompt))) {
      read(stranger, suffix).andExpect(status().isForbidden());
      mvc.perform(get("/api/v1/questions/prompts" + suffix)).andExpect(status().isUnauthorized());
    }
    assertThatThrownBy(() -> lists.list(query(stranger, null, 0, 20)))
        .isInstanceOf(com.pawmento.global.exception.PermissionDeniedException.class);
    assertThatThrownBy(() -> details.get(detail(stranger, prompt, 0, 20)))
        .isInstanceOf(com.pawmento.global.exception.PermissionDeniedException.class);
    read(owner, "/" + Long.MAX_VALUE + "?petId=" + pet).andExpect(status().isNotFound());
    read(owner, "?petId=" + Long.MAX_VALUE).andExpect(status().isNotFound());
    long different = pets.create(new CreatePetCommand(owner, "다른 아이", "DOG", null, null)).petId();
    long foreign =
        ensure
            .ensure(new EnsureCurrentQuestionPromptCommand(owner, different))
            .current()
            .prompt()
            .promptId();
    read(owner, item(foreign)).andExpect(status().isNotFound());
    for (String invalid :
        List.of(
            "mode=",
            "mode=active",
            "mode=null",
            "mode=OTHER",
            "mode=ACTIVE&mode=GARDEN",
            "page=",
            "page=-1",
            "page=1.0",
            "page=null",
            "page=2147483648",
            "page=0&page=1",
            "size=0",
            "size=101",
            "size=",
            "size=1&size=2",
            "page=%ZZ")) {
      read(owner, "?petId=" + pet + "&" + invalid).andExpect(status().isBadRequest());
      if (!invalid.startsWith("mode"))
        read(owner, item(prompt) + "&" + invalid).andExpect(status().isBadRequest());
    }
    for (String suffix : List.of("", "?petId=", "?petId=0", "?petId=" + pet + "&petId=" + pet)) {
      read(owner, suffix).andExpect(status().isBadRequest());
      read(owner, "/" + prompt + suffix).andExpect(status().isBadRequest());
    }
    for (String id :
        List.of("0", "0x10", "+1", "-1", "1 2", " 1", "1 ", "1.0", "9223372036854775808"))
      read(owner, "/" + id + "?petId=" + pet).andExpect(status().isBadRequest());
    read(owner, item(prompt) + "&mode=bad&mode=%ZZ&unknown=%ZZ").andExpect(status().isOk());
    read(owner, "?petId=" + pet + "&unknown=%ZZ&unknown=again").andExpect(status().isOk());
    read(owner, "/current?petId=" + pet)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.prompt.promptId").value(prompt))
        .andExpect(jsonPath("$.prompt.answerCount").doesNotExist());
    assertThatThrownBy(() -> new ListQuestionPromptsQuery(0L, pet, null, 0, 20))
        .isInstanceOf(InvalidQuestionAnswerException.class);
    assertThatThrownBy(() -> new ListQuestionPromptsQuery(owner, pet, null, -1, 20))
        .isInstanceOf(InvalidQuestionAnswerException.class);
    assertThatThrownBy(() -> ListQuestionPromptsQuery.from(owner, pet, "bad", 0, 20))
        .isInstanceOf(InvalidQuestionAnswerException.class);
    assertThatThrownBy(() -> new GetQuestionPromptQuery(owner, pet, null, 0, 20))
        .isInstanceOf(InvalidQuestionAnswerException.class);
  }

  @Test
  @DisplayName("#31-S37 원본 비활성·마지막 답변 삭제 뒤에도 스냅샷·완료·진행을 보존하고 GET은 쓰지 않는다")
  void readsNeverAdvanceOrWrite() {
    var emptyBefore = databaseState();
    lists.list(query(owner, null, 0, 20));
    assertThat(databaseState()).isEqualTo(emptyBefore);
    long first = prompt();
    var answer = answer(owner, first);
    long next = prompt();
    var before = details.get(detail(owner, first, 0, 20)).prompt();
    jdbc.update(
        "update question set content='관리자 수정', enabled=false where id=?", before.questionId());
    delete.delete(new DeleteQuestionAnswerCommand(owner, pet, answer.answerId(), 0L));
    var state = databaseState();
    var after = details.get(detail(owner, first, 0, 20)).prompt();
    assertThat(after.question()).isEqualTo(before.question());
    assertThat(after.completedAt()).isEqualTo(before.completedAt());
    assertThat(after.answerCount()).isZero();
    assertThat(after.myAnswerId()).isNull();
    assertThat(after.canAnswer()).isTrue();
    assertThat(lists.list(query(owner, null, 0, 20)).items())
        .extracting(QuestionPromptHistoryItem::promptId)
        .containsExactly(next, first);
    assertThat(databaseState()).isEqualTo(state);
  }

  List<List<Map<String, Object>>> databaseState() {
    return List.of(
            "question_prompt",
            "question_progress",
            "question_answer",
            "question_answer_attachment",
            "diary_entries",
            "diary_location",
            "media_assets",
            "media_validation_jobs")
        .stream()
        .map(t -> jdbc.queryForList("select * from " + t + " order by 1"))
        .toList();
  }

  @ParameterizedTest
  @CsvSource({"false,false", "false,true", "true,false", "true,true"})
  @DisplayName("#31-S36 바깥 RC와 별도 연결의 답변 추가·삭제에도 목록/상세는 독립 RR 읽기와 동일 스냅샷을 유지한다")
  void repeatableSnapshot(boolean detail, boolean removing) throws Exception {
    long prompt = prompt(), photo = asset(owner, false);
    var first =
        create
            .create(new CreateQuestionAnswerCommand(owner, pet, prompt, "원본", List.of(photo)))
            .answer();
    if (removing) answer(writer, prompt);
    var reached = new CountDownLatch(1);
    var committed = new CountDownLatch(1);
    Map<String, Snapshot> seen = new ConcurrentHashMap<>();
    doAnswer(
            inv -> {
              var r = inv.callRealMethod();
              if (org.springframework.transaction.support.TransactionSynchronizationManager
                  .isCurrentTransactionReadOnly()) seen.put("access", snapshot());
              return r;
            })
        .when(access)
        .currentMode(owner, pet);
    org.mockito.stubbing.Answer<Object> blocked =
        inv -> {
          seen.put("history", snapshot());
          reached.countDown();
          await(committed);
          return inv.callRealMethod();
        };
    if (detail) doAnswer(blocked).when(history).findSummary(pet, prompt, owner);
    else doAnswer(blocked).when(history).list(eq(pet), isNull(), eq(owner), eq(0), eq(20));
    if (detail) {
      doAnswer(
              inv -> {
                var r = inv.callRealMethod();
                seen.put("answers", snapshot());
                return r;
              })
          .when(answers)
          .list(any());
      doAnswer(
              inv -> {
                var r = inv.callRealMethod();
                seen.put("attachments", snapshot());
                return r;
              })
          .when(media)
          .metadata(eq(pet), anyList());
    }
    try (var pool = Executors.newSingleThreadExecutor()) {
      var change =
          pool.submit(
              () -> {
                try {
                  await(reached);
                  if (removing)
                    jdbc.update(
                        "delete from question_answer where prompt_id=? and author_member_id=?",
                        prompt,
                        writer);
                  else {
                    answer(writer, prompt);
                    prompt();
                  }
                } finally {
                  committed.countDown();
                }
              });
      var outer = new TransactionTemplate(manager);
      outer.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
      outer.executeWithoutResult(
          status -> {
            var previous = snapshot();
            jdbc.update("update member set nickname='외부 미커밋' where id=?", owner);
            if (detail) {
              var result = details.get(detail(owner, prompt, 0, 20));
              assertThat(result.prompt().answerCount()).isEqualTo(removing ? 2 : 1);
              assertThat(result.answers().items()).hasSize(removing ? 2 : 1);
              assertThat(result.answers().items().getFirst().attachments())
                  .hasSize(removing ? 0 : 1);
              assertThat(result.prompt().myAnswerId()).isEqualTo(first.answerId());
            } else {
              var result = lists.list(query(owner, null, 0, 20));
              assertThat(result.totalElements()).isEqualTo(1);
              assertThat(result.items()).hasSize(1);
              assertThat(result.items().getFirst().answerCount()).isEqualTo(removing ? 2 : 1);
              assertThat(result.items().getFirst().myAnswerId()).isEqualTo(first.answerId());
            }
            assertThat(seen.get("history").connection()).isNotEqualTo(previous.connection());
            assertThat(snapshot()).isEqualTo(previous);
            status.setRollbackOnly();
          });
      change.get(15, TimeUnit.SECONDS);
    }
    reset(history, access, answers, media);
    var current = details.get(detail(owner, prompt, 0, 20));
    assertThat(current.prompt().answerCount()).isEqualTo(removing ? 1 : 2);
    assertThat(current.answers().items()).hasSize(removing ? 1 : 2);
    if (!detail)
      assertThat(lists.list(query(owner, null, 0, 20)).totalElements()).isEqualTo(removing ? 1 : 2);
    assertThat(seen.values()).containsOnly(seen.get("history"));
    assertThat(seen.get("history").isolation()).isEqualTo("repeatable read");
    assertThat(seen.get("history").readOnly()).isTrue();
    assertThat(seen).hasSize(detail ? 4 : 2);
  }

  record Snapshot(int connection, String isolation, boolean readOnly) {}

  Snapshot snapshot() {
    return jdbc.queryForObject(
        "select"
            + " pg_backend_pid(),current_setting('transaction_isolation'),current_setting('transaction_read_only')",
        (rs, n) -> new Snapshot(rs.getInt(1), rs.getString(2), "on".equals(rs.getString(3))));
  }

  static void await(CountDownLatch latch) {
    try {
      assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError(e);
    }
  }

  @Test
  @DisplayName("#31-S38 페이지가 커져도 회차/회원 N+1을 만들지 않고 회차 목록에서 답변 본문·첨부를 제외한다")
  void batchedSql() throws Exception {
    for (int i = 0; i < 4; i++) {
      long p = prompt();
      create.create(
          new CreateQuestionAnswerCommand(owner, pet, p, "사진 답변", List.of(asset(owner, false))));
      create.create(
          new CreateQuestionAnswerCommand(writer, pet, p, "음성 답변", List.of(asset(writer, true))));
    }
    long small = statements(() -> lists.list(query(owner, null, 0, 1)));
    long large = statements(() -> lists.list(query(owner, null, 0, 4)));
    assertThat(large).isEqualTo(small);
    assertThat(large).isLessThanOrEqualTo(10);
    long first = lists.list(query(owner, null, 0, 1)).items().getFirst().promptId();
    clearInvocations(media);
    long detailSmall =
        statements(
            () -> {
              var result = details.get(detail(owner, first, 0, 1));
              assertThat(result.answers().items())
                  .hasSize(1)
                  .allSatisfy(a -> assertThat(a.attachments()).hasSize(1));
            });
    verify(media, times(1)).metadata(eq(pet), anyList());
    clearInvocations(media);
    long detailLarge =
        statements(
            () -> {
              var result = details.get(detail(owner, first, 0, 2));
              assertThat(result.answers().items())
                  .hasSize(2)
                  .allSatisfy(a -> assertThat(a.attachments()).hasSize(1));
              assertThat(
                      result.answers().items().stream()
                          .flatMap(a -> a.attachments().stream())
                          .map(QuestionAnswerResult.Attachment::type)
                          .toList())
                  .containsExactlyInAnyOrder("IMAGE", "AUDIO");
            });
    verify(media, times(1)).metadata(eq(pet), anyList());
    assertThat(detailLarge).isEqualTo(detailSmall);
    assertThat(detailLarge).isLessThanOrEqualTo(10);
    read(owner, "?petId=" + pet)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].content").doesNotExist())
        .andExpect(jsonPath("$.items[0].attachments").doesNotExist());
  }

  long statements(Runnable action) {
    var stats = emf.unwrap(SessionFactory.class).getStatistics();
    long before = stats.getPrepareStatementCount();
    action.run();
    return stats.getPrepareStatementCount() - before;
  }

  long asset(long author, boolean audio) {
    var now = clock.instant().minusSeconds(30);
    var a =
        MediaAsset.create(
            author,
            pet,
            "QUESTION_ANSWER_ATTACHMENT",
            audio ? "녹음.m4a" : "사진.png",
            audio ? "audio/mp4" : "image/png",
            10,
            UUID.randomUUID().toString(),
            now);
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
    return assets.save(a).getId();
  }
}
