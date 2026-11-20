package com.pawmento.question;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import com.pawmento.TestcontainersConfiguration;
import com.pawmento.member.application.port.in.*;
import com.pawmento.pet.application.exception.PetHasRecordsException;
import com.pawmento.pet.application.port.in.*;
import com.pawmento.question.application.exception.QuestionPersistenceUnavailableException;
import com.pawmento.question.application.port.out.*;
import com.pawmento.question.domain.QuestionMode;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
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
class QuestionAnswerScenarioTest {
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

  @Test
  @DisplayName("#31-S5/S10/S13/S15 늦은 답변은 실제 한국 등록일과 작성자·배정 스냅샷을 보존하며 다이어리를 만들지 않는다")
  void registrationAndSnapshot() throws Exception {
    long prompt = ensure();
    String snapshot =
        jdbc.queryForObject(
            "select question_snapshot from question_prompt where id=?", String.class, prompt);
    jdbc.update("update question set content='변경된 질문',enabled=false");
    when(clock.instant()).thenReturn(Instant.parse("2026-11-25T15:00:00Z"));
    var saved =
        create(prompt)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.completedNow").value(true))
            .andExpect(jsonPath("$.nextAssignmentRequired").value(true))
            .andExpect(jsonPath("$.answer.question").value(snapshot))
            .andExpect(jsonPath("$.answer.author.memberId").value(member))
            .andExpect(jsonPath("$.answer.author.displayName").value("보리 가족"))
            .andExpect(jsonPath("$.answer.answeredDate").value("2026-11-26"))
            .andExpect(jsonPath("$.answer.content").value("공을 쫓으며 신나게 뛰었어요."));
    long id = id(json(saved));
    pending(null);
    assertThat(
            jdbc.queryForObject(
                "select completed_at is not null from question_prompt where id=?",
                Boolean.class,
                prompt))
        .isTrue();
    call(post("/api/v1/questions/prompts/current?petId=" + pet)).andExpect(status().isConflict());
    call(get(answerPath(id)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.question").value(snapshot));
    assertThat(count("question_answer")).isEqualTo(1);
    assertThat(count("diary_entries")).isZero();
  }

  @Test
  @DisplayName("#31-S9/S14 내용 없는 PATCH는 현재 값을 200으로 반환하고 시각·버전·진행을 바꾸지 않는다")
  void patchWithoutContentDoesNotMutate() throws Exception {
    long prompt = ensure();
    var saved = json(create(prompt).andExpect(status().isCreated()));
    long answer = id(saved);
    var original = (Map<?, ?>) saved.get("answer");
    long next = ensure();
    when(clock.instant()).thenReturn(Instant.parse("2026-11-23T16:00:00Z"));
    for (String body : List.of("{\"version\":0}", "{\"version\":0,\"attachmentIds\":[]}")) {
      var response =
          json(
              call(patch(answerPath(answer)).contentType(MediaType.APPLICATION_JSON).content(body))
                  .andExpect(status().isOk()));
      assertThat((Map<?, ?>) response).isEqualTo(original);
      pending(next);
    }
    call(patch(answerPath(answer))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"version\":1}"))
        .andExpect(status().isConflict());
  }

  @Test
  @DisplayName("#31-S13/S14 회원 이름 누락은 사용자 입력 400이 아닌 공통 500이며 답변·완료·진행을 저장하지 않는다")
  void invalidAuthorNameIsStoredDataFailure() throws Exception {
    long prompt = ensure();
    for (String name : new String[] {null, "  "}) {
      doReturn(new MemberResult(member, "internal@example.test", name, "USER"))
          .when(memberDetails)
          .get(new GetMemberQuery(member));
      create(prompt)
          .andExpect(status().isInternalServerError())
          .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
          .andExpect(jsonPath("$.message").value("서버에서 데이터를 읽지 못했습니다. 잠시 후 다시 시도해 주세요."));
      assertThat(count("question_answer")).isZero();
      pending(prompt);
      assertThat(
              jdbc.queryForObject(
                  "select completed_at from question_prompt where id=?", Instant.class, prompt))
          .isNull();
    }
  }

  @Test
  @DisplayName("#31-S8/S9/S14 중복·버전 충돌을 거부하고 수정·삭제·과거 재답변은 현재 진행을 변경하지 않는다")
  void editDeletePast() throws Exception {
    long prompt = ensure();
    long id = id(json(create(prompt).andExpect(status().isCreated())));
    create(prompt).andExpect(status().isConflict());
    long next = ensure();
    when(clock.instant()).thenReturn(Instant.parse("2026-11-23T16:00:00Z"));
    call(patch(answerPath(id))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"version\":0,\"content\":\"수정한 답변\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(1))
        .andExpect(jsonPath("$.answeredDate").value("2026-11-20"))
        .andExpect(jsonPath("$.answeredAt").value("2026-11-20T14:59:00Z"))
        .andExpect(jsonPath("$.updatedAt").value("2026-11-23T16:00:00Z"));
    call(patch(answerPath(id))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"version\":0,\"content\":\"덮어쓰기\"}"))
        .andExpect(status().isConflict());
    call(delete(answerPath(id))).andExpect(status().isBadRequest());
    call(delete(answerPath(id) + "&version=0")).andExpect(status().isConflict());
    call(delete(answerPath(id) + "&version=1")).andExpect(status().isNoContent());
    pending(next);
    create(prompt)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.completedNow").value(false))
        .andExpect(jsonPath("$.nextAssignmentRequired").value(false));
    pending(next);
  }

  @Test
  @DisplayName("#31-S11/S12/S13 모드가 바뀐 제출·다른 아이 경로·타 소유자 접근과 서버 결정 필드 위조를 거부한다")
  void guards() throws Exception {
    long prompt = ensure();
    for (String body :
        List.of(
            "{\"content\":\"답변\",\"authorMemberId\":1}",
            "{\"content\":\"답변\",\"answeredDate\":\"2000-01-01\"}",
            "{\"content\":\"답변\",\"attachmentIds\":[1]}",
            "{\"content\":true}",
            "{\"content\":\" \"}"))
      call(post(createPath(prompt)).contentType(MediaType.APPLICATION_JSON).content(body))
          .andExpect(status().isBadRequest());
    as(
            member + 9999,
            post(createPath(prompt))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"답변\"}"))
        .andExpect(status().isForbidden());
    long other = pets.create(new CreatePetCommand(member, "다른 아이", "DOG", null, null)).petId();
    call(post(createPath(prompt).replace("petId=" + pet, "petId=" + other))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"content\":\"답변\"}"))
        .andExpect(status().isNotFound());
    modes.changeMode(new ChangePetModeCommand(member, pet, "GARDEN"));
    create(prompt).andExpect(status().isConflict());
    assertThat(count("question_answer")).isZero();
    modes.changeMode(new ChangePetModeCommand(member, pet, "ACTIVE"));
    long id = id(json(create(prompt).andExpect(status().isCreated())));
    modes.changeMode(new ChangePetModeCommand(member, pet, "GARDEN"));
    call(get(answerPath(id))).andExpect(status().isOk());
    call(patch(answerPath(id))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"version\":0,\"content\":\"수정\"}"))
        .andExpect(status().isConflict());
    call(delete(answerPath(id) + "&version=0")).andExpect(status().isConflict());
  }

  @Test
  @DisplayName("#31-S13 답변 이력은 한국 등록일 양 끝 포함·모드·회차·작성자로 필터하고 페이지와 안정 정렬을 제공한다")
  void history() throws Exception {
    long first = ensure();
    long firstId = id(json(create(first).andExpect(status().isCreated())));
    long second = ensure();
    long secondId = id(json(create(second).andExpect(status().isCreated())));
    when(clock.instant()).thenReturn(Instant.parse("2026-11-20T15:00:00Z"));
    modes.changeMode(new ChangePetModeCommand(member, pet, "GARDEN"));
    long garden = ensure();
    create(garden).andExpect(status().isCreated());
    String path = "/api/v1/questions/answers?petId=" + pet;
    call(get(
            path
                + "&mode=ACTIVE&from=2026-11-20&to=2026-11-20&authorMemberId="
                + member
                + "&page=0&size=1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.totalPages").value(2))
        .andExpect(jsonPath("$.items[0].answerId").value(secondId));
    call(get(path + "&mode=ACTIVE&page=1&size=1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].answerId").value(firstId));
    call(get(path + "&promptId=" + first))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1));
    call(get(path + "&from=2026-11-21"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1));
    call(get(path + "&page=2147483647&size=100"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isEmpty())
        .andExpect(jsonPath("$.totalElements").value(3));
    for (String query :
        List.of(
            "size=101",
            "page=-1",
            "mode=ALL",
            "petId=" + pet,
            "from=2026-02-30",
            "from=2026-11-22&to=2026-11-20",
            "authorMemberId=0",
            "page=99999999999")) call(get(path + "&" + query)).andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("#31-S14 진행 저장 뒤 실패하면 답변·완료 시각·현재 포인터가 함께 롤백된다")
  void rollback() throws Exception {
    long prompt = ensure();
    doAnswer(
            inv -> {
              inv.callRealMethod();
              throw new QuestionPersistenceUnavailableException(new RuntimeException("injected"));
            })
        .when(progress)
        .saveProgress(any());
    create(prompt).andExpect(status().isServiceUnavailable());
    pending(prompt);
    assertThat(count("question_answer")).isZero();
    assertThat(
            jdbc.queryForObject(
                "select completed_at from question_prompt where id=?", Instant.class, prompt))
        .isNull();
  }

  @Test
  @DisplayName("#31-S8/S14 서로 다른 연결의 동시 제출은 DB 잠금을 기다리고 한 답변·한 완료만 저장한다")
  void concurrentDuplicate() throws Exception {
    long prompt = ensure();
    var locked = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var first = new AtomicBoolean(true);
    doAnswer(
            inv -> {
              var p = inv.callRealMethod();
              if (first.getAndSet(false)) {
                locked.countDown();
                if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("해제 신호 없음");
              }
              return p;
            })
        .when(progress)
        .lock(pet, QuestionMode.ACTIVE);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var a = executor.submit(() -> create(prompt).andReturn().getResponse().getStatus());
      try {
        assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
        var b = executor.submit(() -> create(prompt).andReturn().getResponse().getStatus());
        awaitBlocked();
        assertThat(b.isDone()).isFalse();
        release.countDown();
        assertThat(a.get(10, TimeUnit.SECONDS)).isEqualTo(201);
        assertThat(b.get(10, TimeUnit.SECONDS)).isEqualTo(409);
      } finally {
        release.countDown();
      }
    }
    assertThat(count("question_answer")).isEqualTo(1);
    pending(null);
  }

  @Test
  @DisplayName("#31-S11 모드 변경이 먼저 pet 행을 잠그면 대기 중인 이전 모드 제출은 변경 후 409로 거부된다")
  void modeWinsRace() throws Exception {
    long prompt = ensure();
    var locked = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var change =
          executor.submit(
              () ->
                  new TransactionTemplate(transactions)
                      .execute(
                          s -> {
                            jdbc.queryForObject(
                                "select id from pets where id=? for update", Long.class, pet);
                            locked.countDown();
                            try {
                              if (!release.await(10, TimeUnit.SECONDS))
                                throw new AssertionError("해제 신호 없음");
                            } catch (InterruptedException e) {
                              throw new RuntimeException(e);
                            }
                            modes.changeMode(new ChangePetModeCommand(member, pet, "GARDEN"));
                            return true;
                          }));
      try {
        assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
        var save = executor.submit(() -> create(prompt).andReturn().getResponse().getStatus());
        awaitBlocked();
        release.countDown();
        change.get(10, TimeUnit.SECONDS);
        assertThat(save.get(10, TimeUnit.SECONDS)).isEqualTo(409);
      } finally {
        release.countDown();
      }
    }
    assertThat(count("question_answer")).isZero();
    pending(prompt);
  }

  @Test
  @DisplayName("#31-S12 아이 소유자라도 다른 작성자의 답변은 조회만 가능하고 수정·삭제는 403이다")
  void differentAuthor() throws Exception {
    long prompt = ensure();
    long id = id(json(create(prompt).andExpect(status().isCreated())));
    long other =
        members
            .signUp(
                new SignUpMemberCommand(
                    UUID.randomUUID() + "@test.example", "Password123!", "다른 작성자"))
            .id();
    // Persisted history fixture; this does not grant the other member family access.
    jdbc.update(
        "update question_answer set author_member_id=?,author_name_snapshot='다른 작성자' where id=?",
        other,
        id);
    call(get(answerPath(id)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.author.memberId").value(other));
    call(patch(answerPath(id))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"version\":0,\"content\":\"덮어쓰기\"}"))
        .andExpect(status().isForbidden());
    call(delete(answerPath(id) + "&version=0")).andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("#31-S14 실제 동시 수정은 앞선 변경을 기다리고 같은 버전의 두 번째 변경을 거부한다")
  void concurrentEdit() throws Exception {
    long prompt = ensure();
    long id = id(json(create(prompt).andExpect(status().isCreated())));
    var locked = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var first = new AtomicBoolean(true);
    doAnswer(
            inv -> {
              var a = inv.callRealMethod();
              if (first.getAndSet(false)) {
                locked.countDown();
                if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("해제 신호 없음");
              }
              return a;
            })
        .when(answers)
        .lock(pet, id);
    try (var executor = Executors.newFixedThreadPool(2)) {
      Callable<Integer> edit =
          () ->
              call(patch(answerPath(id))
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("{\"version\":0,\"content\":\"새 답변\"}"))
                  .andReturn()
                  .getResponse()
                  .getStatus();
      var a = executor.submit(edit);
      try {
        assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
        var b = executor.submit(edit);
        awaitBlocked();
        release.countDown();
        assertThat(a.get(10, TimeUnit.SECONDS)).isEqualTo(200);
        assertThat(b.get(10, TimeUnit.SECONDS)).isEqualTo(409);
      } finally {
        release.countDown();
      }
    }
    call(get(answerPath(id))).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
  }

  void awaitBlocked() {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (jdbc.queryForObject(
            "select count(*) from pg_stat_activity where datname=current_database() and"
                + " wait_event_type='Lock'",
            Integer.class)
        < 1) {
      if (System.nanoTime() > deadline) throw new AssertionError("실제 DB 잠금 대기 없음");
      Thread.yield();
    }
  }

  @Test
  @DisplayName("#31-S12 실제 답변이 있으면 아이 삭제를 거부하고 답변을 모두 지운 뒤에는 삭제할 수 있다")
  void petDeletion() throws Exception {
    long prompt = ensure();
    long id = id(json(create(prompt).andExpect(status().isCreated())));
    assertThatThrownBy(() -> deletePet.delete(new DeletePetCommand(member, pet)))
        .isInstanceOf(PetHasRecordsException.class);
    assertThatThrownBy(() -> petStorage.deleteById(pet)).isInstanceOf(PetHasRecordsException.class);
    // The DB restriction protects callers even when the precheck and insertion race.
    assertThatThrownBy(() -> jdbc.update("delete from pets where id=?", pet))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class)
        .hasMessageContaining("fk_question_answer_pet");
    call(delete(answerPath(id) + "&version=0")).andExpect(status().isNoContent());
    deletePet.delete(new DeletePetCommand(member, pet));
  }
}
