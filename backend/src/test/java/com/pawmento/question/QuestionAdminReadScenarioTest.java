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
import com.pawmento.question.application.exception.*;
import com.pawmento.question.application.port.in.*;
import com.pawmento.question.application.port.out.LoadQuestionsPort;
import com.pawmento.question.domain.QuestionFilter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {
    "pawmento.auth.jwt-secret=test-only-jwt-secret-0123456789-abcdefghij",
    "pawmento.media.worker.enabled=false"
})
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
class QuestionAdminReadScenarioTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired SignUpMemberUseCase signup;
  @Autowired LoginMemberUseCase login;
  @Autowired GetMemberUseCase members;
  @Autowired ListAdminQuestionsUseCase list;
  @Autowired GetAdminQuestionUseCase detail;
  @Autowired CreatePetUseCase pets;
  @Autowired PlatformTransactionManager transactions;
  @MockitoSpyBean LoadQuestionsPort questionRows;
  long admin;
  long user;
  String prefix;

  @BeforeEach
  void setup() {
    prefix = "admin-test-" + UUID.randomUUID();
    admin = signup.signUp(new SignUpMemberCommand(prefix + "@example.com", "password123", "관리자")).id();
    user = signup.signUp(new SignUpMemberCommand(prefix + "-user@example.com", "password123", "회원")).id();
    jdbc.update("update member set role='ADMIN' where id=?", admin);
  }

  @AfterEach
  void cleanup() {
    reset(questionRows);
    jdbc.update("delete from question where content like ?", prefix + "%");
  }

  long row(String mode, boolean enabled, String suffix) {
    return jdbc.queryForObject("insert into question(mode,content,enabled,created_at,updated_at,version) "
        + "values(?,?,?,now(),now(),0) returning id", Long.class, mode, prefix + suffix, enabled);
  }

  ResultActions as(long member, String role, MockHttpServletRequestBuilder request) throws Exception {
    return mvc.perform(request.with(jwt().jwt(j -> j.subject("" + member).claim("role", role))
        .authorities(new SimpleGrantedAuthority("ROLE_" + role))));
  }

  MockHttpServletRequestBuilder request(String url) {
    return get(java.net.URI.create(url));
  }

  String query(String search) {
    return "q=" + URLEncoder.encode(search, StandardCharsets.UTF_8);
  }

  @Test
  @DisplayName("#31-S22/#14-S8 관리자 경로의 실제 요청은 무인증 401·일반 회원 403·현재 관리자 허용이다")
  void routeAccess() throws Exception {
    long id = row("ACTIVE", false, "inactive");
    for (String url : new String[]{"/api/v1/admin/questions", "/api/v1/admin/questions/" + id}) {
      mvc.perform(get(url)).andExpect(status().isUnauthorized());
      as(user, "USER", get(url)).andExpect(status().isForbidden());
      as(admin, "ADMIN", get(url)).andExpect(status().isOk());
    }
    // 아직 없는 관리자 쓰기 경로도 일반 인증 규칙으로 빠지지 않는다.
    as(user, "USER", post("/api/v1/admin/questions")).andExpect(status().isForbidden());
    as(user, "USER", patch("/api/v1/admin/questions/" + id)).andExpect(status().isForbidden());
    as(admin, "ADMIN", request("/api/v1/admin/questions/" + id))
        .andExpect(jsonPath("enabled").value(false))
        .andExpect(jsonPath("version").value(0))
        .andExpect(jsonPath("seedKey").doesNotExist());
  }

  @Test
  @DisplayName("#31-S22 관리자 공통 루트와 하위 경로를 보호하고 기존 질문 관리자 경로는 제공하지 않는다")
  void sharedAdminNamespace() throws Exception {
    for (String path : new String[]{"/api/v1/admin", "/api/v1/admin/reports", "/api/v1/admin/questions/123"}) {
      mvc.perform(request(path)).andExpect(status().isUnauthorized());
      as(user, "USER", request(path)).andExpect(status().isForbidden());
    }
    as(admin, "ADMIN", request("/api/v1/questions/admin")).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("#31-S23 관리자 목록·상세 응답은 웹 DTO의 명시적인 필드만 직렬화한다")
  void explicitResponseFields() throws Exception {
    long id = row("ACTIVE", true, "response");
    var detailJson = as(admin, "ADMIN", request("/api/v1/admin/questions/" + id))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    java.util.Map<String, Object> detailFields = com.jayway.jsonpath.JsonPath.read(detailJson, "$");
    assertThat(detailFields.keySet()).containsExactlyInAnyOrder("questionId", "mode", "content",
        "enabled", "createdAt", "updatedAt", "version");
    var pageJson = as(admin, "ADMIN", request("/api/v1/admin/questions?" + query(prefix)))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    java.util.Map<String, Object> pageFields = com.jayway.jsonpath.JsonPath.read(pageJson, "$");
    java.util.Map<String, Object> itemFields = com.jayway.jsonpath.JsonPath.read(pageJson, "$.items[0]");
    assertThat(pageFields.keySet()).containsExactlyInAnyOrder("items", "page", "size", "totalElements", "totalPages");
    assertThat(itemFields.keySet()).containsExactlyInAnyOrderElementsOf(detailFields.keySet());
  }

  @Test
  @DisplayName("#31-S22 오래된 ADMIN 토큰·없는 회원과 직접 유스케이스도 현재 DB 역할을 확인한다")
  void currentRoleAndDirectCalls() throws Exception {
    long id = row("ACTIVE", true, "role");
    as(user, "ADMIN", request("/api/v1/admin/questions")).andExpect(status().isForbidden());
    jdbc.update("update member set role='USER' where id=?", admin);
    as(admin, "ADMIN", request("/api/v1/admin/questions/" + id)).andExpect(status().isForbidden());
    as(Long.MAX_VALUE, "ADMIN", request("/api/v1/admin/questions")).andExpect(status().isForbidden());
    var filter = new QuestionFilter(null, null, null, 0, 20);
    assertThatThrownBy(() -> list.list(new ListAdminQuestionsQuery(admin, filter)))
        .isInstanceOf(QuestionAdminDeniedException.class);
    assertThatThrownBy(() -> detail.get(new GetAdminQuestionQuery(user, id)))
        .isInstanceOf(QuestionAdminDeniedException.class);
    assertThatThrownBy(() -> detail.get(new GetAdminQuestionQuery(Long.MAX_VALUE, id)))
        .isInstanceOf(QuestionAdminDeniedException.class);
  }

  @Test
  @DisplayName("#31-S22/#14-S8 실제 로그인 JWT 쿠키의 ADMIN 허용과 역할 회수 후 403을 확인한다")
  void actualAdminCookie() throws Exception {
    var token = login.login(new LoginMemberCommand(prefix + "@example.com", "password123")).accessToken();
    var cookie = new jakarta.servlet.http.Cookie("ACCESS_TOKEN", token);
    mvc.perform(request("/api/v1/admin/questions").cookie(cookie)).andExpect(status().isOk());
    jdbc.update("update member set role='USER' where id=?", admin);
    mvc.perform(request("/api/v1/admin/questions").cookie(cookie)).andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("#31-S22 상세 직접 호출은 외부 트랜잭션의 캐시된 ADMIN 대신 진입 시점의 회수된 역할을 확인한다")
  void detailIgnoresOuterAdminCache() {
    long id = row("ACTIVE", true, "stale-admin");
    var outer = new TransactionTemplate(transactions);
    var independent = new TransactionTemplate(transactions);
    independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    outer.executeWithoutResult(ignored -> {
      assertThat(members.get(new GetMemberQuery(admin)).role()).isEqualTo("ADMIN");
      independent.executeWithoutResult(tx -> jdbc.update("update member set role='USER' where id=?", admin));
      assertThat(members.get(new GetMemberQuery(admin)).role()).isEqualTo("ADMIN");
      assertThatThrownBy(() -> detail.get(new GetAdminQuestionQuery(admin, id)))
          .isInstanceOf(QuestionAdminDeniedException.class);
    });
  }

  @Test
  @DisplayName("#31-S23 목록은 활성·비활성 포함, 필터 AND·ID 역순·정확한 페이지/빈 결과를 반환한다")
  void filtersAndPages() throws Exception {
    long first = row("ACTIVE", true, "match-a");
    row("ACTIVE", false, "match-b");
    long third = row("GARDEN", true, "match-c");
    as(admin, "ADMIN", request("/api/v1/admin/questions?" + query(prefix) + "&size=2"))
        .andExpect(jsonPath("totalElements").value(3)).andExpect(jsonPath("totalPages").value(2))
        .andExpect(jsonPath("items.length()").value(2)).andExpect(jsonPath("items[0].questionId").value(third));
    as(admin, "ADMIN", request("/api/v1/admin/questions?" + query(prefix) + "&size=2&page=1"))
        .andExpect(jsonPath("items[0].questionId").value(first));
    as(admin, "ADMIN", request("/api/v1/admin/questions?" + query(prefix) + "&mode=ACTIVE&enabled=false"))
        .andExpect(jsonPath("totalElements").value(1)).andExpect(jsonPath("items[0].enabled").value(false));
    as(admin, "ADMIN", request("/api/v1/admin/questions?" + query(prefix) + "&page=2147483647&size=100"))
        .andExpect(jsonPath("items.length()").value(0)).andExpect(jsonPath("totalElements").value(3));
    as(admin, "ADMIN", request("/api/v1/admin/questions?" + query(prefix + "missing")))
        .andExpect(jsonPath("totalPages").value(0)).andExpect(jsonPath("items.length()").value(0));
    as(admin, "ADMIN", request("/api/v1/admin/questions"))
        .andExpect(jsonPath("page").value(0)).andExpect(jsonPath("size").value(20));
  }

  @Test
  @DisplayName("#31-S23 검색은 대소문자를 무시하고 %, _, 역슬래시와 escape 문자를 문자 그대로 찾는다")
  void literalSearch() throws Exception {
    for (String literal : new String[]{"%", "_", "\\", "!"}) {
      long id = row("ACTIVE", true, "literal" + literal);
      row("ACTIVE", true, "literal-other");
      as(admin, "ADMIN", request("/api/v1/admin/questions?" + query(prefix.toUpperCase() + "LITERAL" + literal)))
          .andExpect(jsonPath("totalElements").value(1)).andExpect(jsonPath("items[0].questionId").value(id));
    }
    as(admin, "ADMIN", request("/api/v1/admin/questions?q=%20%20"))
        .andExpect(status().isOk());
    as(admin, "ADMIN", request("/api/v1/admin/questions?" + query("x".repeat(300))))
        .andExpect(status().isOk());
    as(admin, "ADMIN", request("/api/v1/admin/questions?" + query("x".repeat(301))))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("VALIDATION"));
  }

  @Test
  @DisplayName("#31-S23 목록 쿼리의 누락·잘못된 값·중복·인코딩 오류를 거절하고 알 수 없는 키는 무시한다")
  void invalidQueries() throws Exception {
    for (String q : new String[]{"mode=", "mode=OTHER", "enabled=", "enabled=TRUE", "page=-1",
        "page=2147483648", "size=0", "size=101", "page=", "q", "q=a&q=b", "q=a&%71=b"}) {
      as(admin, "ADMIN", get(java.net.URI.create("/api/v1/admin/questions?" + q)))
          .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("VALIDATION"));
    }
    for (String raw : new String[]{"q=%", "q=%GG", "q=%FF", "q=%E3%81", "q=%00", "q=abc%00def"}) {
      as(admin, "ADMIN", request("/api/v1/admin/questions").with(request -> {
        request.setQueryString(raw);
        return request;
      })).andExpect(status().isBadRequest());
    }
    as(admin, "ADMIN", request("/api/v1/admin/questions?ignored=a&ignored=b&petId=bad"))
        .andExpect(status().isOk());
    as(admin, "ADMIN", request("/api/v1/admin/questions/0")).andExpect(status().isBadRequest());
    as(admin, "ADMIN", request("/api/v1/admin/questions/9223372036854775807")).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("#31-S23 목록 건수와 데이터는 동시 등록 중에도 같은 읽기 스냅샷에서 반환한다")
  void consistentSnapshot() throws Exception {
    row("ACTIVE", true, "snapshot-first");
    var requiresNew = new TransactionTemplate(transactions);
    requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    doAnswer(inv -> {
      long count = (long) inv.callRealMethod();
      requiresNew.executeWithoutResult(ignored -> row("ACTIVE", true, "snapshot-new"));
      return count;
    }).when(questionRows).count(any());
    as(admin, "ADMIN", request("/api/v1/admin/questions?" + query(prefix)))
        .andExpect(jsonPath("totalElements").value(1)).andExpect(jsonPath("items.length()").value(1));
    assertThat(jdbc.queryForObject("select count(*) from question where content like ?", Long.class, prefix + "%"))
        .isEqualTo(2);
  }

  @Test
  @DisplayName("#31-S29 관리자 원본 관리 권한은 다른 소유자의 현재 질문·답변·첨부 접근을 허용하지 않는다")
  void noPersonalAccess() throws Exception {
    long pet = pets.create(new CreatePetCommand(user, "보리", "DOG", null, null)).petId();
    for (String path : new String[]{"/prompts/current", "/answers/1", "/answers/1/attachments/1/content"}) {
      as(admin, "ADMIN", request("/api/v1/questions" + path + "?petId=" + pet)).andExpect(status().isForbidden());
    }
  }
}
