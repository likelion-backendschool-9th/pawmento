package com.pawmento.question;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.pawmento.TestcontainersConfiguration;
import com.pawmento.member.application.port.in.*;
import com.pawmento.pet.application.port.in.*;
import com.pawmento.question.application.exception.QuestionAdminDeniedException;
import com.pawmento.question.application.port.in.*;
import com.pawmento.question.application.port.out.SaveQuestionPort;
import com.pawmento.question.domain.exception.InvalidQuestionException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
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
class QuestionAdminCreateScenarioTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired SignUpMemberUseCase signup;
  @Autowired LoginMemberUseCase login;
  @Autowired GetMemberUseCase members;
  @Autowired CreateAdminQuestionUseCase create;
  @Autowired CreatePetUseCase pets;
  @Autowired EnsureCurrentQuestionPromptUseCase ensure;
  @Autowired GetCurrentQuestionPromptUseCase currentQueries;
  @Autowired GetAdminQuestionUseCase details;
  @Autowired @org.springframework.beans.factory.annotation.Qualifier("questionSeed")
  org.springframework.beans.factory.InitializingBean seed;
  @Autowired PlatformTransactionManager transactions;
  @MockitoSpyBean SaveQuestionPort questionRows;
  @MockitoSpyBean java.time.Clock clock;
  final JsonMapper json = JsonMapper.builder().build();
  long admin;
  long user;
  String prefix;

  @BeforeEach
  void setup() {
    prefix = "create-test-" + UUID.randomUUID();
    admin = signup.signUp(new SignUpMemberCommand(prefix + "@example.com", "password123", "관리자")).id();
    user = signup.signUp(new SignUpMemberCommand(prefix + "-user@example.com", "password123", "회원")).id();
    jdbc.update("update member set role='ADMIN' where id=?", admin);
  }

  @AfterEach
  void cleanup() {
    reset(questionRows);
    jdbc.update("delete from question where content like ?", prefix + "%");
  }

  String body(String mode, String content) {
    return json.writeValueAsString(Map.of("mode", mode, "content", content));
  }

  ResultActions as(long actor, String role, MockHttpServletRequestBuilder request) throws Exception {
    return mvc.perform(request.with(jwt().jwt(j -> j.subject("" + actor).claim("role", role))
        .authorities(new SimpleGrantedAuthority("ROLE_" + role))));
  }

  Map<String, Object> response(ResultActions action) throws Exception {
    return com.jayway.jsonpath.JsonPath.read(action.andReturn().getResponse().getContentAsString(), "$");
  }

  long count() {
    return jdbc.queryForObject("select count(*) from question", Long.class);
  }

  @Test
  @DisplayName("#31-S24 ACTIVE/GARDEN 등록은 strip한 내용·기본 활성·version 0·서버 시각으로 저장되고 상세·목록에서 조회된다")
  void createBothModes() throws Exception {
    for (String mode : new String[]{"ACTIVE", "GARDEN"}) {
      Instant before = Instant.now();
      var result = response(as(admin, "ADMIN", post("/api/v1/admin/questions")
          .contentType(MediaType.APPLICATION_JSON).content(body(mode, "  " + prefix + mode + "  ")))
          .andExpect(status().isCreated()).andExpect(jsonPath("seedKey").doesNotExist()));
      long id = ((Number) result.get("questionId")).longValue();
      assertThat(id).isPositive();
      assertThat(result.get("content")).isEqualTo(prefix + mode);
      assertThat(result.get("mode")).isEqualTo(mode);
      assertThat(result.get("enabled")).isEqualTo(true);
      assertThat(((Number) result.get("version")).longValue()).isZero();
      Instant created = Instant.parse((String) result.get("createdAt"));
      assertThat(created).isBetween(before, Instant.now());
      assertThat(result.get("updatedAt")).isEqualTo(result.get("createdAt"));
      assertThat(jdbc.queryForObject("select seed_key from question where id=?", String.class, id)).isNull();
      as(admin, "ADMIN", get("/api/v1/admin/questions/" + id)).andExpect(status().isOk())
          .andExpect(jsonPath("questionId").value(id)).andExpect(jsonPath("content").value(prefix + mode));
      as(admin, "ADMIN", get("/api/v1/admin/questions?q=" + prefix + "&mode=" + mode))
          .andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(1))
          .andExpect(jsonPath("items[0].questionId").value(id));
    }
  }

  @Test
  @DisplayName("#31-S24 등록 응답의 시각은 실제 DB 저장 정밀도로 목록·상세 응답과 일치한다")
  void persistedTimestampPrecision() throws Exception {
    doReturn(Instant.parse("2026-11-23T00:00:00.123456789Z")).when(clock).instant();
    var created = response(as(admin, "ADMIN", post("/api/v1/admin/questions")
        .contentType(MediaType.APPLICATION_JSON).content(body("ACTIVE", prefix)))
        .andExpect(status().isCreated()));
    long id = ((Number) created.get("questionId")).longValue();
    var detail = response(as(admin, "ADMIN", get("/api/v1/admin/questions/" + id))
        .andExpect(status().isOk()));
    var page = response(as(admin, "ADMIN", get("/api/v1/admin/questions?q=" + prefix))
        .andExpect(status().isOk()));
    var listed = (Map<?, ?>) ((java.util.List<?>) page.get("items")).getFirst();
    for (String field : new String[]{"createdAt", "updatedAt"}) {
      assertThat(created.get(field)).as(field + " 상세").isEqualTo(detail.get(field));
      assertThat(created.get(field)).as(field + " 목록").isEqualTo(listed.get(field));
    }
    var persisted = jdbc.queryForObject("select created_at from question where id=?", java.sql.Timestamp.class, id);
    assertThat(Instant.parse((String) created.get("createdAt"))).isEqualTo(persisted.toInstant());
  }

  @Test
  @DisplayName("#31-S24 동일 본문도 별도 ID로 등록하고 정확히 300자를 허용한다")
  void duplicateAndBoundary() {
    String content = prefix + "x".repeat(300 - prefix.length());
    var first = create.create(new CreateAdminQuestionCommand(admin, "ACTIVE", content));
    var second = create.create(new CreateAdminQuestionCommand(admin, "ACTIVE", content));
    assertThat(first.content()).hasSize(300);
    assertThat(second.questionId()).isNotEqualTo(first.questionId());
  }

  @Test
  @DisplayName("#31-S24 누락·null·잘못된 타입·공백·301자·잘못된 모드·금지 필드를 400으로 거부하고 저장하지 않는다")
  void rejectsInvalidBodies() throws Exception {
    long before = count();
    String longContent = body("ACTIVE", "x".repeat(301));
    for (String input : new String[]{"null", "[]", "{}", "{\"mode\":\"ACTIVE\"}",
        "{\"content\":\"test\"}", "{\"mode\":null,\"content\":\"test\"}",
        "{\"mode\":\"ACTIVE\",\"content\":null}", "{\"mode\":7,\"content\":\"test\"}",
        "{\"mode\":\"ACTIVE\",\"content\":7}", body("OTHER", "test"), body("active", "test"),
        body("ACTIVE", ""), body("ACTIVE", "   "), body("ACTIVE", "bad\0text"), longContent}) {
      as(admin, "ADMIN", post("/api/v1/admin/questions").contentType(MediaType.APPLICATION_JSON).content(input))
          .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("VALIDATION"));
    }
    for (String field : new String[]{"enabled", "questionId", "seedKey", "version", "createdAt", "updatedAt", "memberId"}) {
      String input = "{\"mode\":\"ACTIVE\",\"content\":\"test\",\"" + field + "\":false}";
      as(admin, "ADMIN", post("/api/v1/admin/questions").contentType(MediaType.APPLICATION_JSON).content(input))
          .andExpect(status().isBadRequest());
    }
    assertThat(count()).isEqualTo(before);
  }

  @Test
  @DisplayName("#31-S22/S24 등록도 무인증 401·일반 회원/권한 회수/없는 회원 403으로 거부한다")
  void permissions() throws Exception {
    String payload = body("ACTIVE", prefix);
    long before = count();
    mvc.perform(post("/api/v1/admin/questions").contentType(MediaType.APPLICATION_JSON).content(payload))
        .andExpect(status().isUnauthorized());
    for (long actor : new long[]{user, Long.MAX_VALUE}) {
      as(actor, actor == user ? "USER" : "ADMIN", post("/api/v1/admin/questions")
          .contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isForbidden());
    }
    var token = login.login(new LoginMemberCommand(prefix + "@example.com", "password123")).accessToken();
    jdbc.update("update member set role='USER' where id=?", admin);
    mvc.perform(post("/api/v1/admin/questions").cookie(new jakarta.servlet.http.Cookie("ACCESS_TOKEN", token))
        .contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isForbidden());
    assertThatThrownBy(() -> create.create(new CreateAdminQuestionCommand(admin, "ACTIVE", prefix)))
        .isInstanceOf(QuestionAdminDeniedException.class);
    assertThat(count()).isEqualTo(before);
  }

  @Test
  @DisplayName("#31-S22 등록 직접 호출도 외부에 남은 ADMIN 캐시로 권한 회수를 우회하지 못한다")
  void staleOuterAdmin() {
    var outer = new TransactionTemplate(transactions);
    var independent = new TransactionTemplate(transactions);
    independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    long before = count();
    outer.executeWithoutResult(ignored -> {
      assertThat(members.get(new GetMemberQuery(admin)).role()).isEqualTo("ADMIN");
      independent.executeWithoutResult(tx -> jdbc.update("update member set role='USER' where id=?", admin));
      assertThat(members.get(new GetMemberQuery(admin)).role()).isEqualTo("ADMIN");
      assertThatThrownBy(() -> create.create(new CreateAdminQuestionCommand(admin, "ACTIVE", prefix)))
          .isInstanceOf(QuestionAdminDeniedException.class);
    });
    assertThat(count()).isEqualTo(before);
  }

  @Test
  @DisplayName("#31-S24 입력 포트 직접 호출에서도 모드·본문 불변조건을 검사한다")
  void directValidation() {
    long before = count();
    for (String mode : new String[]{null, "OTHER"}) {
      assertThatThrownBy(() -> create.create(new CreateAdminQuestionCommand(admin, mode, prefix)))
          .isInstanceOf(InvalidQuestionException.class);
    }
    for (String content : new String[]{null, "", " ", "x".repeat(301), "bad\0text"}) {
      assertThatThrownBy(() -> create.create(new CreateAdminQuestionCommand(admin, "ACTIVE", content)))
          .isInstanceOf(InvalidQuestionException.class);
    }
    assertThat(count()).isEqualTo(before);
  }

  @Test
  @DisplayName("#31-S24 저장 후 실패 시 등록 전체를 롤백한다")
  void saveRollback() {
    long before = count();
    doAnswer(invocation -> {
      invocation.callRealMethod();
      throw new IllegalStateException("after insert");
    }).when(questionRows).save(any());
    assertThatThrownBy(() -> create.create(new CreateAdminQuestionCommand(admin, "ACTIVE", prefix)))
        .isInstanceOf(IllegalStateException.class);
    assertThat(count()).isEqualTo(before);
  }

  @Test
  @DisplayName("#31-S24/S28 관리자 등록 질문은 기존 시드 재적용 뒤에도 ID·본문·활성·시각·버전을 보존한다")
  void seedPreservesNewQuestion() throws Exception {
    var created = create.create(new CreateAdminQuestionCommand(admin, "GARDEN", prefix));
    var query = new GetAdminQuestionQuery(admin, created.questionId());
    var before = details.get(query);
    long count = count();
    seed.afterPropertiesSet();
    assertThat(details.get(query)).isEqualTo(before);
    assertThat(count()).isEqualTo(count);
  }

  @Test
  @DisplayName("#31-S29 원본 등록은 기존 배정·진행·답변·다이어리를 변경하지 않는다")
  void leavesProgressAndDiaryAlone() {
    long pet = pets.create(new CreatePetCommand(user, "보리", "DOG", null, null)).petId();
    ensure.ensure(new EnsureCurrentQuestionPromptCommand(user, pet));
    var current = currentQueries.get(new GetCurrentQuestionPromptQuery(user, pet));
    long diaries = jdbc.queryForObject("select count(*) from diary_entries", Long.class);
    long answers = jdbc.queryForObject("select count(*) from question_answer", Long.class);
    create.create(new CreateAdminQuestionCommand(admin, "ACTIVE", prefix));
    assertThat(ensure.ensure(new EnsureCurrentQuestionPromptCommand(user, pet)).current()).isEqualTo(current);
    assertThat(jdbc.queryForObject("select count(*) from diary_entries", Long.class)).isEqualTo(diaries);
    assertThat(jdbc.queryForObject("select count(*) from question_answer", Long.class)).isEqualTo(answers);
  }
}
