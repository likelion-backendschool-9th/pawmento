package com.pawmento.question;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import com.pawmento.TestcontainersConfiguration;
import com.pawmento.global.security.AccessTokenCookie;
import com.pawmento.question.application.port.out.QuestionProgressStorePort;
import com.pawmento.question.domain.QuestionMode;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

// 실제 회원가입/로그인 쿠키와 초대/가입 API를 사용한다. 테스트 바깥 트랜잭션 없이 요청마다 커밋한다.
@SpringBootTest(
    properties = {
      "pawmento.auth.jwt-secret=test-only-jwt-secret-0123456789-abcdefghij",
      "pawmento.media.worker.enabled=false"
    })
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
class FamilyFirstQuestionScenarioTest {
  private static final JsonMapper JSON = JsonMapper.builder().build();
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @MockitoSpyBean QuestionProgressStorePort progress;

  record Actor(long id, Cookie cookie) {}

  @ParameterizedTest
  @CsvSource({"ACTIVE,false", "ACTIVE,true", "GARDEN,false", "GARDEN,true"})
  @DisplayName("#31-S3 실제 가입한 두 가족의 동시 최초 배정은 DB 잠금 대기 후 한 질문·진행으로 수렴한다")
  void concurrentFamilyAssignment(String mode, boolean secondFamilyFirst) throws Exception {
    var owner = member("소유주");
    var familyA = member("가족 A");
    var familyB = member("가족 B");
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
    join(owner, familyA, pet);
    join(familyA, familyB, pet);
    mvc.perform(get("/api/v1/families?petId=" + pet).cookie(familyB.cookie()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.members.length()").value(2));

    String path = "/api/v1/questions/prompts/current?petId=" + pet;
    for (var actor : List.of(owner, familyA, familyB))
      mvc.perform(get(path).cookie(actor.cookie()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.mode").value(mode))
          .andExpect(jsonPath("$.prompt").isEmpty());
    assertThat(count("question_prompt", pet)).isZero();
    assertThat(count("question_progress", pet)).isZero();

    var held = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var firstCall = new AtomicBoolean(true);
    var blockerPid = new AtomicInteger();
    doAnswer(
            inv -> {
              var result = inv.callRealMethod();
              if (firstCall.compareAndSet(true, false)) {
                blockerPid.set(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                held.countDown();
                if (!release.await(15, TimeUnit.SECONDS))
                  throw new AssertionError("배정 잠금 해제 신호 없음");
              }
              return result;
            })
        .when(progress)
        .lock(pet, QuestionMode.valueOf(mode));

    var firstActor = secondFamilyFirst ? familyB : familyA;
    var waitingActor = secondFamilyFirst ? familyA : familyB;
    MvcResult created;
    try (var pool = Executors.newFixedThreadPool(2)) {
      try {
        var first = pool.submit(() -> postJson(path, firstActor, Map.of(), 201));
        assertThat(held.await(10, TimeUnit.SECONDS)).isTrue();
        var second = pool.submit(() -> postJson(path, waitingActor, Map.of(), 200));
        awaitDatabaseWait(blockerPid.get());
        assertThat(second.isDone()).as("두 번째 가족 요청은 실제 DB 잠금을 기다린다").isFalse();
        release.countDown();
        created = first.get(15, TimeUnit.SECONDS);
        var existing = second.get(15, TimeUnit.SECONDS);
        assertThat(value(created, "$.prompt")).isEqualTo(value(existing, "$.prompt"));
        assertThat(value(created, "$.mode")).isEqualTo(mode);
        assertThat(value(existing, "$.mode")).isEqualTo(mode);
      } finally {
        release.countDown();
      }
    }

    long prompt = number(created, "$.prompt.promptId");
    for (var actor : List.of(owner, familyA, familyB)) {
      var current =
          mvc.perform(get(path).cookie(actor.cookie())).andExpect(status().isOk()).andReturn();
      assertThat(value(current, "$.prompt")).isEqualTo(value(created, "$.prompt"));
      assertThat(number(postJson(path, actor, Map.of(), 200), "$.prompt.promptId"))
          .isEqualTo(prompt);
    }
    assertThat(count("question_prompt", pet)).isEqualTo(1);
    assertThat(count("question_progress", pet)).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select current_prompt_id from question_progress where pet_id=? and mode=?",
                Long.class,
                pet,
                mode))
        .isEqualTo(prompt);
    assertThat(
            jdbc.queryForObject(
                "select next_sequence from question_progress where pet_id=? and mode=?",
                Long.class,
                pet,
                mode))
        .isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "select mode from question_prompt where id=?", String.class, prompt))
        .isEqualTo(mode);
    assertThat(count("question_answer", pet)).isZero();
  }

  private void awaitDatabaseWait(int blockerPid) throws Exception {
    assertThat(blockerPid).isPositive();
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (jdbc.queryForObject(
            "select count(*) from pg_stat_activity where ? = any(pg_blocking_pids(pid)) and"
                + " wait_event_type='Lock'",
            Integer.class,
            blockerPid)
        == 0) {
      if (System.nanoTime() >= deadline) throw new AssertionError("첫 가족의 트랜잭션을 기다리는 DB 연결 없음");
      Thread.sleep(10);
    }
  }

  private long count(String table, long pet) {
    return jdbc.queryForObject(
        "select count(*) from " + table + " where pet_id=?", Long.class, pet);
  }

  private Actor member(String nickname) throws Exception {
    String email = "first-question-" + UUID.randomUUID() + "@test.example";
    postJson(
        "/api/v1/auth/signup",
        null,
        Map.of("email", email, "password", "Password123!", "nickname", nickname),
        201);
    var login =
        postJson(
            "/api/v1/auth/login", null, Map.of("email", email, "password", "Password123!"), 200);
    var cookie = login.getResponse().getCookie(AccessTokenCookie.NAME);
    assertThat(cookie).isNotNull();
    return new Actor(number(login, "$.id"), cookie);
  }

  private void join(Actor issuer, Actor invited, long pet) throws Exception {
    var code = postJson("/api/v1/families/invite-codes", issuer, Map.of("petId", pet), 201);
    postJson("/api/v1/families/members", invited, Map.of("code", value(code, "$.code")), 200);
  }

  private MvcResult postJson(String path, Actor actor, Object body, int expected) throws Exception {
    var request = post(path).contentType(MediaType.APPLICATION_JSON).content(json(body));
    if (actor != null) request.cookie(actor.cookie());
    return mvc.perform(request).andExpect(status().is(expected)).andReturn();
  }

  private Object value(MvcResult result, String path) throws Exception {
    return JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), path);
  }

  private long number(MvcResult result, String path) throws Exception {
    return ((Number) value(result, path)).longValue();
  }

  private String json(Object value) {
    return JSON.writeValueAsString(value);
  }
}
