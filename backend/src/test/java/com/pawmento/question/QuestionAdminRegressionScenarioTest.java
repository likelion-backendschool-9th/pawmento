package com.pawmento.question;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import com.pawmento.TestcontainersConfiguration;
import com.pawmento.global.exception.PermissionDeniedException;
import com.pawmento.media.application.port.out.ReadMediaFilePort;
import com.pawmento.media.application.port.out.SaveMediaAssetPort;
import com.pawmento.media.domain.*;
import com.pawmento.member.application.port.in.*;
import com.pawmento.question.application.port.in.*;
import com.pawmento.question.domain.QuestionMode;
import java.io.ByteArrayInputStream;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

/** 실제 관리자 HTTP 변경과 기존 사용자 API를 검증한다. 공유 질문 풀을 변경하므로 병렬 실행에서 격리한다. */
@Isolated("공유 질문 풀의 비활성화·시드 재적용·복구")
@SpringBootTest(properties = {
    "pawmento.auth.jwt-secret=test-only-jwt-secret-0123456789-abcdefghij",
    "pawmento.media.worker.enabled=false",
    "pawmento.postcard.worker.enabled=false"
})
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
class QuestionAdminRegressionScenarioTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired SignUpMemberUseCase signup;
  @Autowired SaveMediaAssetPort mediaRows;
  @Autowired GetQuestionAnswerUseCase answerDetails;
  @Autowired ListQuestionAnswersUseCase answerList;
  @Autowired EditQuestionAnswerUseCase editAnswer;
  @Autowired DeleteQuestionAnswerUseCase deleteAnswer;
  @Autowired GetQuestionFileUseCase answerFiles;
  @Autowired @Qualifier("questionSeed") InitializingBean seed;
  @MockitoSpyBean ReadMediaFilePort files;
  @MockitoSpyBean Clock clock;
  final JsonMapper json = JsonMapper.builder().build();
  final List<Long> createdQuestions = new ArrayList<>();
  final List<Long> createdPets = new ArrayList<>();
  final List<Long> createdAssets = new ArrayList<>();
  List<Map<String, Object>> originalPool;
  long admin, owner;
  String prefix;

  @BeforeEach
  void setup() {
    prefix = "admin-regression-" + UUID.randomUUID();
    admin = signup.signUp(new SignUpMemberCommand(prefix + "@example.com", "Password123!", "관리자")).id();
    owner = signup.signUp(new SignUpMemberCommand(prefix + "-owner@example.com", "Password123!", "보호자")).id();
    jdbc.update("update member set role='ADMIN' where id=?", admin);
    originalPool = jdbc.queryForList("select * from question order by id");
    // 배정 후보만 고립시킨다. 검사할 원본 생성·수정은 아래 HTTP 헬퍼를 사용한다.
    jdbc.update("update question set enabled=false");
  }

  @AfterEach
  void restoreFixtures() {
    reset(files, clock);
    for (long pet : createdPets) {
      jdbc.update("delete from question_answer where pet_id=?", pet);
      jdbc.update("delete from question_progress where pet_id=?", pet);
      jdbc.update("delete from question_prompt where pet_id=?", pet);
      jdbc.update("delete from diary_entries where pet_id=?", pet);
      jdbc.update("delete from pets where id=?", pet);
    }
    for (long id : createdQuestions) jdbc.update("delete from question where id=?", id);
    for (long id : createdAssets) {
      jdbc.update("delete from media_validation_jobs where asset_id=?", id);
      jdbc.update("delete from media_assets where id=?", id);
    }
    if (originalPool != null) for (var row : originalPool)
      jdbc.update("update question set content=?,enabled=?,updated_at=?,version=? where id=?",
          row.get("content"), row.get("enabled"), row.get("updated_at"), row.get("version"), row.get("id"));
  }

  ResultActions as(long member, MockHttpServletRequestBuilder request) throws Exception {
    String role = member == admin ? "ADMIN" : "USER";
    return mvc.perform(request.with(jwt().jwt(j -> j.subject("" + member).claim("role", role))
        .authorities(new SimpleGrantedAuthority("ROLE_" + role))));
  }

  MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder request, Map<String, ?> values) {
    return request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(values));
  }

  Map<String, Object> value(ResultActions result) throws Exception {
    return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$");
  }

  long number(Map<String, ?> value, String key) { return ((Number) value.get(key)).longValue(); }

  long question(QuestionMode mode, String text) throws Exception {
    var result = value(as(admin, body(post("/api/v1/admin/questions"),
        Map.of("mode", mode.name(), "content", text))).andExpect(status().isCreated()));
    long id = number(result, "questionId");
    createdQuestions.add(id);
    return id;
  }

  Map<String, Object> change(long question, long version, Map<String, ?> fields) throws Exception {
    var values = new HashMap<String, Object>(fields);
    values.put("version", version);
    return value(as(admin, body(patch("/api/v1/admin/questions/" + question), values))
        .andExpect(status().isOk()));
  }

  void advanceTime() {
    doReturn(clock.instant().plusSeconds(1)).when(clock).instant();
  }

  @ParameterizedTest
  @EnumSource(QuestionMode.class)
  @DisplayName("#31-S27 답변 생성·수정 응답은 DB 시각과 같고 한국 자정 반올림으로 최초 답변 날짜가 바뀌지 않는다")
  void answerTimestampsMatchStoredValuesAtMidnight(QuestionMode mode) throws Exception {
    doReturn(Instant.parse("2026-11-23T14:59:59.999999999Z")).when(clock).instant();
    long question = question(mode, prefix + " 시각 경계 질문");
    long pet = pet(owner, mode);
    long prompt = number(prompt(assign(pet, 201)), "promptId");
    var response = value(as(owner, body(post("/api/v1/questions/prompts/" + prompt + "/answers?petId=" + pet),
        Map.of("content", "자정 직전의 답변"))).andExpect(status().isCreated()));
    Map<String, Object> created = JsonPath.read(json.writeValueAsString(response), "$.answer");
    long id = number(created, "answerId");
    assertThat(number(created, "questionId")).isEqualTo(question);
    assertThat(created.get("answeredAt")).isEqualTo("2026-11-23T14:59:59.999999Z");
    assertThat(created.get("updatedAt")).isEqualTo(created.get("answeredAt"));
    assertThat(created.get("answeredDate")).isEqualTo("2026-11-23");
    assertThat(answerDetail(pet, id)).isEqualTo(created);
    assertThat(jdbc.queryForObject("select completed_at from question_prompt where id=?", java.sql.Timestamp.class, prompt).toInstant())
        .isEqualTo(Instant.parse((String) created.get("answeredAt")));

    doReturn(Instant.parse("2026-11-23T15:00:00.123456789Z")).when(clock).instant();
    var edited = value(as(owner, body(patch(answerPath(pet, id)), Map.of("content", "자정 뒤 수정", "version", 0)))
        .andExpect(status().isOk()));
    assertThat(edited.get("updatedAt")).isEqualTo("2026-11-23T15:00:00.123456Z");
    assertThat(edited.get("answeredAt")).isEqualTo(created.get("answeredAt"));
    assertThat(edited.get("answeredDate")).isEqualTo("2026-11-23");
    assertThat(answerDetail(pet, id)).isEqualTo(edited);
  }

  long pet(long member, QuestionMode mode) throws Exception {
    long id = number(value(as(member, body(post("/api/v1/pets"), Map.of("name", "보리", "species", "DOG")))
        .andExpect(status().isCreated())), "petId");
    createdPets.add(id);
    if (mode == QuestionMode.GARDEN)
      as(member, body(patch("/api/v1/pets/" + id + "/mode"), Map.of("mode", "GARDEN")))
          .andExpect(status().isOk());
    return id;
  }

  String current(long pet) { return "/api/v1/questions/prompts/current?petId=" + pet; }
  String answerPath(long pet, long answer) { return "/api/v1/questions/answers/" + answer + "?petId=" + pet; }

  Map<String, Object> assign(long pet, int status) throws Exception {
    return value(as(owner, post(current(pet))).andExpect(status().is(status)));
  }

  Map<String, Object> prompt(Map<String, Object> current) {
    return JsonPath.read(json.writeValueAsString(current), "$.prompt");
  }

  long answer(long pet, long prompt, List<Long> assets) throws Exception {
    var created = value(as(owner, body(post("/api/v1/questions/prompts/" + prompt + "/answers?petId=" + pet),
        Map.of("content", "공을 쫓으며 신나게 뛰었어요.", "attachmentIds", assets)))
        .andExpect(status().isCreated()));
    return ((Number) JsonPath.read(json.writeValueAsString(created), "$.answer.answerId")).longValue();
  }

  Map<String, Object> answerDetail(long pet, long answer) throws Exception {
    return value(as(owner, get(answerPath(pet, answer))).andExpect(status().isOk()));
  }

  @ParameterizedTest
  @EnumSource(QuestionMode.class)
  @DisplayName("#31-S27 관리자 API 수정·비활성화는 기존 배정·답변을 보존하고 빈 풀에서도 미답변에 답할 수 있다")
  void preservesSnapshotsThroughAdminApi(QuestionMode mode) throws Exception {
    // PostgreSQL의 microsecond 반올림과 최초 배정 응답의 일치도 확정적으로 검사한다.
    doReturn(Instant.now().plusSeconds(1).with(java.time.temporal.ChronoField.NANO_OF_SECOND, 123456789))
        .when(clock).instant();
    long question = question(mode, prefix + " 원래 질문");
    long answeredPet = pet(owner, mode), pendingPet = pet(owner, mode), futurePet = pet(owner, mode);
    var answeredPrompt = prompt(assign(answeredPet, 201));
    long answer = answer(answeredPet, number(answeredPrompt, "promptId"), List.of());
    var originalAnswer = answerDetail(answeredPet, answer);
    var pending = assign(pendingPet, 201);
    assertThat(Instant.parse((String) prompt(pending).get("assignedAt"))).isEqualTo(
        jdbc.queryForObject("select assigned_at from question_prompt where id=?", java.sql.Timestamp.class,
            number(prompt(pending), "promptId")).toInstant());
    var originalPrompts = jdbc.queryForList("select * from question_prompt where pet_id in (?,?) order by id", answeredPet, pendingPet);
    var originalProgress = jdbc.queryForList("select * from question_progress where pet_id in (?,?) order by pet_id", answeredPet, pendingPet);

    advanceTime();
    change(question, 0, Map.of("content", prefix + " 새 질문", "enabled", false));
    assertThat(answerDetail(answeredPet, answer)).isEqualTo(originalAnswer);
    assertThat(value(as(owner, get(current(pendingPet))).andExpect(status().isOk()))).isEqualTo(pending);
    assertThat(assign(pendingPet, 200)).isEqualTo(pending);
    assertThat(jdbc.queryForList("select * from question_prompt where pet_id in (?,?) order by id", answeredPet, pendingPet))
        .isEqualTo(originalPrompts);
    assertThat(jdbc.queryForList("select * from question_progress where pet_id in (?,?) order by pet_id", answeredPet, pendingPet))
        .isEqualTo(originalProgress);

    long other = question(mode, prefix + " 다른 질문");
    var fresh = prompt(assign(futurePet, 201));
    assertThat(number(fresh, "questionId")).isEqualTo(other);
    advanceTime();
    change(other, 0, Map.of("enabled", false));
    // 현재 활성 풀이 비었어도, 이미 배정된 질문과 기존 답변은 사용할 수 있다.
    var pendingPrompt = prompt(pending);
    long late = answer(pendingPet, number(pendingPrompt, "promptId"), List.of());
    assertThat(answerDetail(pendingPet, late).get("question")).isEqualTo(prefix + " 원래 질문");
    assertThat(answerDetail(answeredPet, answer)).isEqualTo(originalAnswer);
    as(owner, post(current(answeredPet))).andExpect(status().isConflict());

    advanceTime();
    change(question, 1, Map.of("enabled", true));
    var reactivated = prompt(assign(answeredPet, 201));
    assertThat(number(reactivated, "questionId")).isEqualTo(question);
    assertThat(reactivated.get("question")).isEqualTo(prefix + " 새 질문");
    assertThat(number(reactivated, "promptId")).isNotEqualTo(number(answeredPrompt, "promptId"));
    assertThat(number(reactivated, "sequence")).isEqualTo(number(answeredPrompt, "sequence") + 1);
    assertThat(answerDetail(answeredPet, answer)).isEqualTo(originalAnswer);
  }

  @ParameterizedTest
  @EnumSource(QuestionMode.class)
  @DisplayName("#31-S28 실제 시드 재적용은 관리자 API로 변경한 시드 원본과 신규 원본의 모든 저장값을 보존한다")
  void seedReapplicationPreservesAdminChanges(QuestionMode mode) throws Exception {
    var row = jdbc.queryForMap("select * from question where mode=? and seed_key is not null order by id limit 1", mode.name());
    long id = ((Number) row.get("id")).longValue();
    var edited = change(id, ((Number) row.get("version")).longValue(),
        Map.of("content", prefix + " 변경한 시드", "enabled", false));
    long created = question(mode, prefix + " 새 원본");
    var before = jdbc.queryForList("select * from question order by id");
    seed.afterPropertiesSet();
    seed.afterPropertiesSet();
    assertThat(jdbc.queryForList("select * from question order by id")).isEqualTo(before);
    assertThat(value(as(admin, get("/api/v1/admin/questions/" + id)).andExpect(status().isOk())))
        .isEqualTo(edited);
    assertThat(jdbc.queryForObject("select seed_key from question where id=?", String.class, id))
        .isEqualTo(row.get("seed_key"));
    assertThat(jdbc.queryForObject("select seed_key from question where id=?", String.class, created)).isNull();
  }

  long readyImage(long pet) {
    var now = Instant.now().minusSeconds(30);
    var asset = MediaAsset.create(owner, pet, "QUESTION_ANSWER_ATTACHMENT", "photo.png", "image/png", 10,
        UUID.randomUUID().toString(), now);
    asset.begin(now);
    asset.received(new StoredUpload(10, "a".repeat(64)), now);
    asset.validated(new VerifiedMedia(UUID.randomUUID().toString(), "image/png", 10,
        "a".repeat(64), null, 1, 1, 5L), now);
    long id = mediaRows.save(asset).getId();
    createdAssets.add(id);
    return id;
  }

  @ParameterizedTest
  @EnumSource(QuestionMode.class)
  @DisplayName("#31-S29 원본 관리자는 타 소유자의 현재 질문·답변·첨부에 웹과 직접 유스케이스로 접근하지 못한다")
  void adminCannotReadOrModifyPrivateAnswers(QuestionMode mode) throws Exception {
    long question = question(mode, prefix + " 질문");
    long pet = pet(owner, mode);
    long asset = readyImage(pet);
    long prompt = number(prompt(assign(pet, 201)), "promptId");
    long answer = answer(pet, prompt, List.of(asset));
    var before = answerDetail(pet, answer);
    doAnswer(call -> new ByteArrayInputStream(new byte["thumbnail".equals(call.getArgument(1)) ? 5 : 10]))
        .when(files).open(anyString(), anyString());
    String content = "/api/v1/questions/answers/" + answer + "/attachments/" + asset + "/content?petId=" + pet;
    String thumbnail = content.replace("/content?", "/thumbnail?");
    as(owner, get(content)).andExpect(status().isOk());
    as(owner, get(thumbnail)).andExpect(status().isOk());
    clearInvocations(files);
    change(question, 0, Map.of("content", prefix + " 관리자 변경", "enabled", false));
    var original = value(as(admin, get("/api/v1/admin/questions/" + question)).andExpect(status().isOk())
        .andExpect(jsonPath("$.seedKey").doesNotExist()).andExpect(jsonPath("$.author").doesNotExist())
        .andExpect(jsonPath("$.attachments").doesNotExist()));
    assertThat(original.keySet()).containsExactlyInAnyOrder("questionId", "mode", "content", "enabled",
        "createdAt", "updatedAt", "version");
    var page = value(as(admin, get("/api/v1/admin/questions").queryParam("q", prefix))
        .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)));
    var listed = (Map<?, ?>) ((List<?>) page.get("items")).getFirst();
    assertThat(listed).isEqualTo(original);
    for (var request : List.of(get(current(pet)), post(current(pet)),
        get("/api/v1/questions/answers?petId=" + pet), get(answerPath(pet, answer)),
        body(patch(answerPath(pet, answer)), Map.of("content", "다른 사람의 수정", "version", 0)),
        delete(answerPath(pet, answer) + "&version=0"), get(content), get(thumbnail), head(content))) {
      as(admin, request).andExpect(status().isForbidden());
    }
    for (Runnable operation : List.<Runnable>of(
        () -> answerDetails.get(new GetQuestionAnswerQuery(admin, pet, answer)),
        () -> answerList.list(new ListQuestionAnswersQuery(admin, pet, mode, null, null, null, null, 0, 20)),
        () -> editAnswer.edit(new EditQuestionAnswerCommand(admin, pet, answer, "우회 시도", 0)),
        () -> deleteAnswer.delete(new DeleteQuestionAnswerCommand(admin, pet, answer, 0)),
        () -> answerFiles.get(new GetQuestionFileQuery(admin, pet, answer, asset, false))))
      assertThatThrownBy(operation::run).isInstanceOf(PermissionDeniedException.class);
    verify(files, never()).open(anyString(), anyString());
    assertThat(answerDetail(pet, answer)).isEqualTo(before);
    assertThat(jdbc.queryForObject("select reference_count from media_assets where id=?", Integer.class, asset)).isEqualTo(1);
    assertThat(jdbc.queryForObject("select count(*) from media_validation_jobs where asset_id=? and kind='DELETE'", Long.class, asset)).isZero();
  }

  Map<String, Object> diaryList(long pet) throws Exception {
    return value(as(owner, get("/api/v1/diaries?petId=" + pet)).andExpect(status().isOk()));
  }

  Map<String, Object> calendar(long pet, YearMonth month) throws Exception {
    return value(as(owner, get("/api/v1/diaries/calendar?petId=" + pet + "&month=" + month))
        .andExpect(status().isOk()));
  }

  @Test
  @DisplayName("#31-S29 질문 원본 변경과 답변 생성·수정·삭제는 기존 다이어리 목록·달력·행을 변경하지 않는다")
  void answersStaySeparateFromDiaryAndCalendar() throws Exception {
    long question = question(QuestionMode.ACTIVE, prefix + " 질문");
    long pet = pet(owner, QuestionMode.ACTIVE);
    var date = LocalDate.now(ZoneId.of("Asia/Seoul"));
    var month = YearMonth.from(date);
    as(owner, body(post("/api/v1/diaries?petId=" + pet),
        Map.of("title", "다이어리 제목", "content", "다이어리에만 남는 기록", "recordedDate", date.toString())))
        .andExpect(status().isCreated());
    var list = diaryList(pet);
    var calendar = calendar(pet, month);
    var rows = jdbc.queryForList("select * from diary_entries where pet_id=? order by id", pet);
    assertThat(number(list, "totalElements")).isEqualTo(1);
    assertThat((List<?>) calendar.get("days")).hasSize(1);
    long prompt = number(prompt(assign(pet, 201)), "promptId");
    long answer = answer(pet, prompt, List.of());
    assertDiaryUnchanged(pet, month, list, calendar, rows);
    change(question, 0, Map.of("content", prefix + " 변경", "enabled", false));
    assertDiaryUnchanged(pet, month, list, calendar, rows);
    as(owner, body(patch(answerPath(pet, answer)), Map.of("content", "답변만 수정", "version", 0)))
        .andExpect(status().isOk());
    assertDiaryUnchanged(pet, month, list, calendar, rows);
    as(owner, delete(answerPath(pet, answer) + "&version=1")).andExpect(status().isNoContent());
    assertDiaryUnchanged(pet, month, list, calendar, rows);
    assertThat(jdbc.queryForObject("select count(*) from question_answer where pet_id=?", Long.class, pet)).isZero();
  }

  void assertDiaryUnchanged(long pet, YearMonth month, Map<String, Object> list,
      Map<String, Object> calendar, List<Map<String, Object>> rows) throws Exception {
    assertThat(diaryList(pet)).isEqualTo(list);
    assertThat(calendar(pet, month)).isEqualTo(calendar);
    assertThat(jdbc.queryForList("select * from diary_entries where pet_id=? order by id", pet)).isEqualTo(rows);
  }
}
