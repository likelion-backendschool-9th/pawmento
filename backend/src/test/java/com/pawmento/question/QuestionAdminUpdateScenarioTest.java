package com.pawmento.question;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.pawmento.TestcontainersConfiguration;
import com.pawmento.member.application.port.in.*;
import com.pawmento.question.application.exception.*;
import com.pawmento.question.application.port.in.*;
import com.pawmento.question.application.port.out.UpdateQuestionPort;
import com.pawmento.question.domain.exception.InvalidQuestionException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = {
    "pawmento.auth.jwt-secret=test-only-jwt-secret-0123456789-abcdefghij",
    "pawmento.media.worker.enabled=false"
})
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
class QuestionAdminUpdateScenarioTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired SignUpMemberUseCase signup;
  @Autowired GetMemberUseCase members;
  @Autowired CreateAdminQuestionUseCase create;
  @Autowired UpdateAdminQuestionUseCase update;
  @Autowired GetAdminQuestionUseCase details;
  @Autowired PlatformTransactionManager transactions;
  @MockitoSpyBean UpdateQuestionPort updates;
  @MockitoSpyBean java.time.Clock clock;
  final JsonMapper json = JsonMapper.builder().build();
  long admin, user, id;
  String prefix;

  @BeforeEach
  void setup() {
    prefix = "update-test-" + UUID.randomUUID();
    admin = signup.signUp(new SignUpMemberCommand(prefix + "@example.com", "password123", "관리자")).id();
    user = signup.signUp(new SignUpMemberCommand(prefix + "-user@example.com", "password123", "회원")).id();
    jdbc.update("update member set role='ADMIN' where id=?", admin);
    id = create.create(new CreateAdminQuestionCommand(admin, "ACTIVE", prefix)).questionId();
  }

  @AfterEach
  void cleanup() {
    reset(updates, clock);
    jdbc.update("delete from question where id=?", id);
  }

  AdminQuestionResult detail() { return details.get(new GetAdminQuestionQuery(admin, id)); }

  UpdateAdminQuestionCommand command(String content, Boolean enabled, long version) {
    return new UpdateAdminQuestionCommand(admin, id, content, enabled, version);
  }

  ResultActions patchAs(long actor, String role, String pathId, String body) throws Exception {
    return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/admin/questions/" + pathId)
        .with(jwt().jwt(j -> j.subject("" + actor).claim("role", role))
            .authorities(new SimpleGrantedAuthority("ROLE_" + role)))
        .contentType(MediaType.APPLICATION_JSON).content(body));
  }

  ResultActions patch(String body) throws Exception { return patchAs(admin, "ADMIN", "" + id, body); }

  @Test
  @DisplayName("#31-S25 내용 수정·비활성·재활성은 생략 필드와 불변 필드를 보존하고 실제 변경만 버전을 증가한다")
  void editAndReactivate() throws Exception {
    var original = detail();
    String changed = "수정한 질문";
    patch(json.writeValueAsString(Map.of("content", "  " + changed + "  ", "version", 0)))
        .andExpect(status().isOk()).andExpect(jsonPath("content").value(changed))
        .andExpect(jsonPath("enabled").value(true)).andExpect(jsonPath("version").value(1));
    patch("{\"enabled\":false,\"version\":1}").andExpect(status().isOk())
        .andExpect(jsonPath("enabled").value(false)).andExpect(jsonPath("version").value(2));
    patch("{\"enabled\":true,\"version\":2}").andExpect(status().isOk())
        .andExpect(jsonPath("content").value(changed)).andExpect(jsonPath("version").value(3));
    var result = detail();
    assertThat(result.questionId()).isEqualTo(original.questionId());
    assertThat(result.mode()).isEqualTo(original.mode());
    assertThat(result.createdAt()).isEqualTo(original.createdAt());
    assertThat(result.updatedAt()).isAfterOrEqualTo(original.updatedAt());
    assertThat(jdbc.queryForObject("select seed_key from question where id=?", String.class, id)).isNull();
  }

  @Test
  @DisplayName("#31-S25 동일 값은 시각·버전을 유지하고 오래된 버전은 동일 값이어도 409다")
  void noOpStillChecksVersion() throws Exception {
    var before = detail();
    assertThat(update.update(command("  " + prefix + "  ", true, 0))).isEqualTo(before);
    verify(updates, never()).update(any());
    update.update(command(null, false, 0));
    patch("{\"enabled\":false,\"version\":0}").andExpect(status().isConflict());
    var latest = detail();
    assertThat(update.update(command(null, false, 1))).isEqualTo(latest);
  }

  @Test
  @DisplayName("#31-S25 DB 시각 반올림·Clock 역행에도 동일 값 PATCH는 200이며 시각·버전을 유지한다")
  void noOpBeforeStoredCreationTime() throws Exception {
    var before = detail();
    doReturn(before.createdAt().minusNanos(1)).when(clock).instant();
    patch(json.writeValueAsString(Map.of("content", " " + prefix + " ", "enabled", true, "version", 0)))
        .andExpect(status().isOk()).andExpect(jsonPath("version").value(0));
    assertThat(detail()).isEqualTo(before);
    verify(updates, never()).update(any());
  }

  @Test
  @DisplayName("#31-S25 내용과 활성을 한 번에 바꿔도 기존 seed_key와 생성 정보는 유지한다")
  void updatesTogetherPreserveSeedKey() throws Exception {
    jdbc.update("update question set seed_key=? where id=?", prefix, id);
    var before = detail();
    patch(json.writeValueAsString(Map.of("content", prefix + " changed", "enabled", false, "version", 0)))
        .andExpect(status().isOk()).andExpect(jsonPath("content").value(prefix + " changed"))
        .andExpect(jsonPath("enabled").value(false)).andExpect(jsonPath("version").value(1))
        .andExpect(jsonPath("seedKey").doesNotExist());
    assertThat(jdbc.queryForObject("select seed_key from question where id=?", String.class, id)).isEqualTo(prefix);
    assertThat(detail().createdAt()).isEqualTo(before.createdAt());
    assertThat(detail().mode()).isEqualTo(before.mode());
  }

  @Test
  @DisplayName("#31-S25 변경 응답의 시각·version은 PostgreSQL 저장값 및 상세와 일치한다")
  void returnsStoredPrecision() throws Exception {
    doReturn(Instant.now().plusSeconds(1).with(java.time.temporal.ChronoField.NANO_OF_SECOND, 123456789))
        .when(clock).instant();
    var response = patch("{\"enabled\":false,\"version\":0}").andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    var actual = detail();
    assertThat(com.jayway.jsonpath.JsonPath.<String>read(response, "$.updatedAt"))
        .isEqualTo(actual.updatedAt().toString());
    assertThat(actual.updatedAt()).isEqualTo(jdbc.queryForObject(
        "select updated_at from question where id=?", java.sql.Timestamp.class, id).toInstant());
  }

  @Test
  @DisplayName("#31-S25 필수 버전·변경 필드·타입·null·금지 필드·본문 경계를 검사하고 원본을 보존한다")
  void invalidBodies() throws Exception {
    var before = detail();
    for (String body : new String[]{"null", "[]", "{}", "{\"version\":0}",
        "{\"enabled\":false}", "{\"enabled\":null,\"version\":0}",
        "{\"content\":null,\"version\":0}", "{\"content\":3,\"version\":0}",
        "{\"enabled\":\"false\",\"version\":0}", "{\"enabled\":0,\"version\":0}",
        "{\"enabled\":false,\"version\":null}", "{\"enabled\":false,\"version\":\"0\"}",
        "{\"enabled\":false,\"version\":0.0}", "{\"enabled\":false,\"version\":-1}",
        "{\"enabled\":false,\"version\":9223372036854775808}"}) {
      patch(body).andExpect(status().isBadRequest());
    }
    for (String content : new String[]{"", " ", "x".repeat(301), "bad\0text"})
      patch(json.writeValueAsString(Map.of("content", content, "version", 0)))
          .andExpect(status().isBadRequest());
    for (String field : new String[]{"mode", "seedKey", "questionId", "createdAt", "updatedAt", "memberId"})
      patch("{\"enabled\":false,\"version\":0,\"" + field + "\":false}")
          .andExpect(status().isBadRequest());
    for (String badId : new String[]{"0", "-1", "x", "9223372036854775808"})
      patchAs(admin, "ADMIN", badId, "{\"enabled\":false,\"version\":0}")
          .andExpect(status().isBadRequest());
    patchAs(admin, "ADMIN", "" + Long.MAX_VALUE, "{\"enabled\":false,\"version\":0}")
        .andExpect(status().isNotFound());
    assertThat(detail()).isEqualTo(before);
    assertThat(update.update(command("x".repeat(300), null, 0)).content()).hasSize(300);
  }

  @Test
  @DisplayName("#31-S22/S25 웹·직접 호출 모두 일반회원·없는 회원·회수된 ADMIN을 거절하고 동일 값도 권한 검사한다")
  void permissions() throws Exception {
    var before = detail();
    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
        "/api/v1/admin/questions/" + id).contentType(MediaType.APPLICATION_JSON)
        .content("{\"enabled\":false,\"version\":0}")).andExpect(status().isUnauthorized());
    for (long actor : new long[]{user, Long.MAX_VALUE}) {
      patchAs(actor, actor == user ? "USER" : "ADMIN", "" + id,
          "{\"enabled\":false,\"version\":0}").andExpect(status().isForbidden());
      assertThatThrownBy(() -> update.update(new UpdateAdminQuestionCommand(actor, id, null, true, 0)))
          .isInstanceOf(QuestionAdminDeniedException.class);
    }
    jdbc.update("update member set role='USER' where id=?", admin);
    patchAs(admin, "ADMIN", "" + id, "{\"enabled\":false,\"version\":0}")
        .andExpect(status().isForbidden());
    assertThatThrownBy(() -> update.update(command(null, true, 0)))
        .isInstanceOf(QuestionAdminDeniedException.class);
    jdbc.update("update member set role='ADMIN' where id=?", admin);
    assertThat(detail()).isEqualTo(before);
  }

  @Test
  @DisplayName("#31-S22/S25 외부 ADMIN 캐시를 재사용하지 않고 현재 역할로 변경을 거부한다")
  void staleOuterAdmin() {
    var outer = new TransactionTemplate(transactions);
    var independent = new TransactionTemplate(transactions);
    independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    outer.executeWithoutResult(ignored -> {
      assertThat(members.get(new GetMemberQuery(admin)).role()).isEqualTo("ADMIN");
      independent.executeWithoutResult(tx -> jdbc.update("update member set role='USER' where id=?", admin));
      assertThat(members.get(new GetMemberQuery(admin)).role()).isEqualTo("ADMIN");
      assertThatThrownBy(() -> update.update(command(null, true, 0)))
          .isInstanceOf(QuestionAdminDeniedException.class);
    });
  }

  @Test
  @DisplayName("#31-S25 입력 포트도 잘못된 본문·ID·version·변경 필드 생략을 거부한다")
  void directValidation() {
    var before = detail();
    for (String value : new String[]{"", " ", "x".repeat(301), "bad\0text"})
      assertThatThrownBy(() -> update.update(command(value, null, 0))).isInstanceOf(InvalidQuestionException.class);
    for (UpdateAdminQuestionCommand value : new UpdateAdminQuestionCommand[]{command(null, null, 0),
        command(null, true, -1), new UpdateAdminQuestionCommand(admin, 0L, null, true, 0)})
      assertThatThrownBy(() -> update.update(value)).isInstanceOf(InvalidQuestionException.class);
    assertThat(detail()).isEqualTo(before);
  }

  @Test
  @DisplayName("#31-S26 PostgreSQL에서 같은 버전의 내용 수정·비활성화는 하나만 성공하고 패자는 409이며 부분 변경이 없다")
  void concurrentEdits() throws Exception {
    var barrier = new CyclicBarrier(2);
    doAnswer(call -> {
      barrier.await(10, TimeUnit.SECONDS);
      return call.callRealMethod();
    }).when(updates).update(any());
    try (var executor = Executors.newFixedThreadPool(2)) {
      var first = executor.submit(() -> attempt(command(prefix + " changed", null, 0)));
      var second = executor.submit(() -> attempt(command(null, false, 0)));
      var outcomes = List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
      assertThat(outcomes.stream().filter(AdminQuestionResult.class::isInstance)).hasSize(1);
      assertThat(outcomes.stream().filter(QuestionModificationConflictException.class::isInstance)).hasSize(1);
      var winner = (AdminQuestionResult) outcomes.stream().filter(AdminQuestionResult.class::isInstance).findFirst().orElseThrow();
      assertThat(detail()).isEqualTo(winner);
      assertThat(detail().version()).isEqualTo(1);
      if (winner.enabled()) assertThat(winner.content()).isEqualTo(prefix + " changed");
      else assertThat(winner.content()).isEqualTo(prefix);
    }
  }

  Object attempt(UpdateAdminQuestionCommand command) {
    try { return update.update(command); }
    catch (QuestionModificationConflictException conflict) { return conflict; }
  }

  @Test
  @DisplayName("#31-S25 DB flush 후 실패하면 본문·활성 상태·시각·version을 모두 롤백한다")
  void rollbackAfterFlush() {
    var before = detail();
    doAnswer(call -> { call.callRealMethod(); throw new IllegalStateException("after flush"); })
        .when(updates).update(any());
    assertThatThrownBy(() -> update.update(command(prefix + " changed", false, 0)))
        .isInstanceOf(IllegalStateException.class);
    assertThat(detail()).isEqualTo(before);
  }
}
