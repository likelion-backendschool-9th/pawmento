package com.pawmento.question;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import com.pawmento.TestcontainersConfiguration;
import com.pawmento.pet.application.port.in.*;
import com.pawmento.question.application.exception.QuestionPersistenceUnavailableException;
import com.pawmento.question.application.port.out.QuestionProgressStorePort;
import com.pawmento.question.application.port.out.SelectQuestionPort;
import com.pawmento.question.domain.QuestionMode;
import java.net.URI;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(
    properties = {
      "pawmento.auth.jwt-secret=test-only-jwt-secret-0123456789-abcdefghij",
      "pawmento.media.worker.enabled=false"
    })
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
class CurrentQuestionPromptScenarioTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired CreatePetUseCase pets;
  @Autowired ChangePetModeUseCase modes;
  @MockitoBean Clock clock;
  @MockitoBean SelectQuestionPort selection;
  @MockitoSpyBean QuestionProgressStorePort store;
  private List<Map<String, Object>> originalQuestions;

  @BeforeEach
  void setup() {
    when(clock.instant()).thenReturn(Instant.parse("2026-11-20T14:59:00Z"));
    when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));
    when(clock.withZone(any())).thenReturn(clock);
    when(selection.select(anyList())).thenAnswer(inv -> ((List<?>) inv.getArgument(0)).getFirst());
    originalQuestions = jdbc.queryForList("select id,content,enabled from question");
  }

  @AfterEach
  void restorePool() {
    for (var row : originalQuestions)
      jdbc.update(
          "update question set content=?,enabled=? where id=?",
          row.get("content"),
          row.get("enabled"),
          row.get("id"));
  }

  long pet() {
    return pets.create(new CreatePetCommand(9001L, "보리", "DOG", null, null)).petId();
  }

  String path(long pet) {
    return "/api/v1/questions/prompts/current?petId=" + pet;
  }

  ResultActions call(long member, MockHttpServletRequestBuilder request) throws Exception {
    return mvc.perform(request.with(jwt().jwt(j -> j.subject("" + member).claim("role", "USER"))));
  }

  Map<String, Object> ensure(long pet, int expected) throws Exception {
    return JsonPath.read(
        call(9001, post(path(pet)))
            .andExpect(status().is(expected))
            .andReturn()
            .getResponse()
            .getContentAsString(),
        "$");
  }

  long promptId(Map<String, Object> response) {
    return ((Number) ((Map<?, ?>) response.get("prompt")).get("promptId")).longValue();
  }

  @Test
  @DisplayName("#31-S2/S3 GET은 저장하지 않고 최초 POST만 배정하며 다른 아이의 진행은 분리한다")
  void firstAssignment() throws Exception {
    long pet = pet(), other = pet();
    call(9001, get(path(pet)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mode").value("ACTIVE"))
        .andExpect(jsonPath("$.prompt").isEmpty());
    assertThat(
            jdbc.queryForObject(
                "select count(*) from question_progress where pet_id=?", Long.class, pet))
        .isZero();
    long id = promptId(ensure(pet, 201));
    assertThat(promptId(ensure(pet, 200))).isEqualTo(id);
    call(9001, get(path(pet)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.prompt.promptId").value(id));
    assertThat(promptId(ensure(other, 201))).isNotEqualTo(id);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from diary_entries where pet_id=?", Long.class, pet))
        .isZero();
  }

  @Test
  @DisplayName("#31-S4/S10 한국 자정·여러 날·원본 수정과 비활성화 뒤에도 미답변 질문과 시각은 유지된다")
  void keepsUnansweredPrompt() throws Exception {
    long pet = pet();
    var first = ensure(pet, 201);
    jdbc.update("update question set content='다른 문구',enabled=false");
    when(clock.instant()).thenReturn(Instant.parse("2026-11-25T16:00:00Z"));
    assertThat(ensure(pet, 200)).isEqualTo(first);
    var read =
        call(9001, get(path(pet)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(JsonPath.<Map<String, Object>>read(read, "$")).isEqualTo(first);
    verify(selection, times(1)).select(anyList());
  }

  @Test
  @DisplayName("#31-S2/S11 모드를 바꾸면 별도로 배정하고 돌아오면 이전 진행을 이어 본다")
  void preservesModeProgress() throws Exception {
    long pet = pet();
    var active = ensure(pet, 201);
    modes.changeMode(new ChangePetModeCommand(9001L, pet, "GARDEN"));
    call(9001, get(path(pet)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mode").value("GARDEN"))
        .andExpect(jsonPath("$.prompt").isEmpty());
    var garden = ensure(pet, 201);
    assertThat(garden.get("mode")).isEqualTo("GARDEN");
    assertThat(promptId(garden)).isNotEqualTo(promptId(active));
    modes.changeMode(new ChangePetModeCommand(9001L, pet, "ACTIVE"));
    assertThat(ensure(pet, 200)).isEqualTo(active);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from question_progress where pet_id=?", Long.class, pet))
        .isEqualTo(2);
  }

  @Test
  @DisplayName("#31-S10 빈 풀은 신규 배정만 409이며 진행·질문을 쓰지 않고 반대 모드에서 뽑지 않는다")
  void emptyPool() throws Exception {
    long pet = pet();
    jdbc.update("update question set enabled=false where mode='ACTIVE'");
    call(9001, post(path(pet))).andExpect(status().isConflict());
    call(9001, get(path(pet))).andExpect(status().isOk()).andExpect(jsonPath("$.prompt").isEmpty());
    assertThat(
            jdbc.queryForObject(
                "select count(*) from question_prompt where pet_id=?", Long.class, pet))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from question_progress where pet_id=?", Long.class, pet))
        .isZero();
  }

  @Test
  @DisplayName("#31-S12 소유자 범위의 인증·접근 검사와 petId 원본 쿼리 검증은 저장 전에 수행된다")
  void accessAndQueryValidation() throws Exception {
    long pet = pet();
    mvc.perform(post(path(pet))).andExpect(status().isUnauthorized());
    call(9002, post(path(pet))).andExpect(status().isForbidden());
    call(9001, get(path(Long.MAX_VALUE))).andExpect(status().isNotFound());
    for (String query :
        new String[] {
          "",
          "petId=0",
          "petId=-1",
          "petId=abc",
          "petId=9223372036854775808",
          "petId=1&%70etId=2",
          "petId=%FF"
        })
      call(
              9001,
              get(
                  URI.create(
                      "/api/v1/questions/prompts/current" + (query.isEmpty() ? "" : "?" + query))))
          .andExpect(status().isBadRequest());
    call(9001, post("/api/v1/questions/prompts/current").header("petId", pet))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(store);
  }

  @Test
  @DisplayName("#31-S3 서로 다른 HTTP 요청의 최초 배정은 DB 잠금을 기다린 뒤 201/200의 같은 회차로 수렴한다")
  void concurrentFirstAssignment() throws Exception {
    long pet = pet();
    var locked = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var first = new AtomicBoolean(true);
    doAnswer(
            inv -> {
              var progress = inv.callRealMethod();
              if (first.getAndSet(false)) {
                locked.countDown();
                if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("해제 신호 없음");
              }
              return progress;
            })
        .when(store)
        .lock(pet, QuestionMode.ACTIVE);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var a = executor.submit(() -> ensure(pet, 201));
      try {
        assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
        var b = executor.submit(() -> ensure(pet, 200));
        // 가족 회수와 직렬화하기 위해 진행 advisory lock보다 pet 행 잠금을 먼저 기다린다.
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (jdbc.queryForObject(
                "select count(*) from pg_stat_activity where pid <> pg_backend_pid()"
                    + " and wait_event_type='Lock'"
                    + " and lower(query) like '%from pets where id = % for update%'",
                Integer.class)
            < 1) {
          if (System.nanoTime() > deadline) throw new AssertionError("DB 잠금 대기 없음");
          Thread.yield();
        }
        assertThat(b.isDone()).isFalse();
        release.countDown();
        assertThat(promptId(a.get(10, TimeUnit.SECONDS)))
            .isEqualTo(promptId(b.get(10, TimeUnit.SECONDS)));
      } finally {
        release.countDown();
      }
    }
    assertThat(
            jdbc.queryForObject(
                "select count(*) from question_prompt where pet_id=?", Long.class, pet))
        .isEqualTo(1);
  }

  @Test
  @DisplayName("#31-S3 진행 저장 뒤 장애가 나면 새 회차와 포인터도 같은 트랜잭션으로 롤백한다")
  void rollsBackAssignment() throws Exception {
    long pet = pet();
    doAnswer(
            inv -> {
              inv.callRealMethod();
              throw new QuestionPersistenceUnavailableException(new RuntimeException("injected"));
            })
        .when(store)
        .saveProgress(any());
    call(9001, post(path(pet))).andExpect(status().isServiceUnavailable());
    assertThat(
            jdbc.queryForObject(
                "select count(*) from question_prompt where pet_id=?", Long.class, pet))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from question_progress where pet_id=?", Long.class, pet))
        .isZero();
  }
}
