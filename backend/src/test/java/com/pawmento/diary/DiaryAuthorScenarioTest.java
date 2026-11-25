package com.pawmento.diary;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import com.pawmento.TestcontainersConfiguration;
import com.pawmento.diary.application.exception.*;
import com.pawmento.diary.application.port.in.*;
import com.pawmento.diary.application.port.out.LoadDiaryAuthorNameForBackfillPort;
import com.pawmento.family.application.port.out.SavePetFamilyMemberPort;
import com.pawmento.family.domain.PetFamilyMember;
import com.pawmento.member.application.port.in.*;
import com.pawmento.pet.application.port.in.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(
    properties = {
      "pawmento.auth.jwt-secret=test-only-jwt-secret-0123456789-abcdefghij",
      "pawmento.media.worker.enabled=false"
    })
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
class DiaryAuthorScenarioTest {
  @Autowired MockMvc mvc;
  @Autowired SignUpMemberUseCase members;
  @Autowired ChangeMemberNicknameUseCase nicknames;
  @Autowired CreatePetUseCase pets;
  @Autowired SavePetFamilyMemberPort families;
  @Autowired CreateDiaryEntryUseCase create;
  @Autowired GetDiaryEntryUseCase get;
  @Autowired LoadDiaryAuthorNameForBackfillPort authors;
  @Autowired JdbcTemplate jdbc;
  @Autowired TransactionTemplate tx;
  @MockitoSpyBean GetMemberUseCase memberDetails;
  @MockitoSpyBean com.pawmento.diary.application.port.out.SaveDiaryEntryPort saved;
  long owner, writer, pet;

  @BeforeEach
  void setup() {
    owner = member("소유주");
    writer = member("작성 가족");
    pet = pets.create(new CreatePetCommand(owner, "보리", "DOG", null, null)).petId();
    families.save(PetFamilyMember.join(pet, writer, Instant.now()));
  }

  long member(String name) {
    return members
        .signUp(new SignUpMemberCommand(UUID.randomUUID() + "@test.example", "Password123!", name))
        .id();
  }

  GetDiaryEntryResult create() {
    return create.create(
        new CreateDiaryEntryCommand(writer, pet, "함께한 하루", LocalDate.of(2026, 11, 17), "테스트 일기"));
  }

  String list() {
    return "/api/v1/diaries?petId=" + pet;
  }

  String detail(long id) {
    return "/api/v1/diaries/" + id + "?petId=" + pet;
  }

  @Test
  @DisplayName("#32-S30 생성·목록·상세·수정은 작성 시 이름을 유지하고 다음 일기는 새 닉네임을 저장한다")
  void snapshotsNames() throws Exception {
    var initial =
        mvc.perform(
                post(list())
                    .with(jwt().jwt(j -> j.subject("" + writer)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"title\":\"테스트"
                            + " 일기\",\"content\":\"산책\",\"recordedDate\":\"2026-11-17\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.createdBy").value(writer))
            .andExpect(jsonPath("$.author.memberId").value(writer))
            .andExpect(jsonPath("$.author.displayName").value("작성 가족"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    long id = ((Number) JsonPath.read(initial, "$.diaryId")).longValue();
    nicknames.changeNickname(new ChangeMemberNicknameCommand(writer, "새 이름"));
    mvc.perform(
            patch(detail(id))
                .with(jwt().jwt(j -> j.subject("" + writer)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"테스트 일기\",\"content\":\"수정\",\"version\":0}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.author.displayName").value("작성 가족"));
    var next = create();
    assertThat(next.author().displayName()).isEqualTo("새 이름");
    GetMemberUseCase nameLookup = AopTestUtils.getUltimateTargetObject(memberDetails);
    clearInvocations(nameLookup);
    var listed =
        mvc.perform(get(list()).with(jwt().jwt(j -> j.subject("" + owner))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(JsonPath.<List<String>>read(listed, "$.items[*].author.displayName"))
        .containsExactly("새 이름", "작성 가족");
    var original = get.get(new GetDiaryEntryQuery(owner, pet, id));
    assertThat(original.author().memberId()).isEqualTo(writer);
    assertThat(original.author().displayName()).isEqualTo("작성 가족");
    assertThat(listed).doesNotContain("\"email\"", "\"tag\"", "\"role\"");
    mvc.perform(
            get("/api/v1/diaries/calendar?petId=" + pet + "&month=2026-11")
                .with(jwt().jwt(j -> j.subject("" + owner))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.days[0].count").value(2));
    verifyNoInteractions(nameLookup);
  }

  @Test
  @DisplayName("#32-S30 작성자 이름을 body로 보내면 명시적 null도 거절하고 원본을 보존한다")
  void rejectsClientNames() throws Exception {
    var initial = create();
    var baseline = get.get(new GetDiaryEntryQuery(owner, pet, initial.diaryId()));
    for (String field :
        List.of(
            "\"author\":null",
            "\"author\":{\"memberId\":1,\"displayName\":\"위조\"}",
            "\"authorNameSnapshot\":\"위조\"",
            "\"displayName\":\"위조\"")) {
      mvc.perform(
              post(list())
                  .with(jwt().jwt(j -> j.subject("" + writer)))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      "{\"title\":\"테스트 일기\",\"content\":\"위조\",\"recordedDate\":\"2026-11-17\","
                          + field
                          + "}"))
          .andExpect(status().isBadRequest());
      mvc.perform(
              patch(detail(initial.diaryId()))
                  .with(jwt().jwt(j -> j.subject("" + writer)))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"title\":\"테스트 일기\",\"content\":\"위조\",\"version\":0," + field + "}"))
          .andExpect(status().isBadRequest());
    }
    assertThat(get.get(new GetDiaryEntryQuery(owner, pet, initial.diaryId()))).isEqualTo(baseline);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from diary_entries where pet_id=?", Long.class, pet))
        .isEqualTo(1);
  }

  @Test
  @DisplayName("#32-S30 실제 없는 회원 조회를 잡아도 바깥 트랜잭션은 rollback-only가 되지 않는다")
  void missingMemberDoesNotPoisonCaller() {
    tx.executeWithoutResult(
        status -> {
          assertThat(authors.loadNameForBackfill(Long.MAX_VALUE)).isEmpty();
          jdbc.update("update pets set breed='커밋 확인' where id=?", pet);
        });
    assertThat(jdbc.queryForObject("select breed from pets where id=?", String.class, pet))
        .isEqualTo("커밋 확인");
    jdbc.update("delete from member where id=?", writer);
    assertThatThrownBy(this::create).isInstanceOf(DiaryAuthorMemberNotFoundException.class);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from diary_entries where pet_id=?", Long.class, pet))
        .isZero();
  }

  @Test
  @DisplayName("#32-S30 비어 있는 회원 이름과 조회 장애는 알 수 없는 작성자로 숨기지 않고 저장을 취소한다")
  void failuresAreNotMissingMembers() {
    GetMemberUseCase target = AopTestUtils.getUltimateTargetObject(memberDetails);
    doReturn(new MemberResult(writer, "fixture@test.example", "  ", "0000", "USER"))
        .when(target)
        .get(new GetMemberQuery(writer));
    assertThatThrownBy(this::create).isInstanceOf(DiaryAuthorMemberNameInvalidException.class);
    assertThatThrownBy(() -> authors.loadNameForBackfill(writer))
        .isInstanceOf(DiaryAuthorMemberNameInvalidException.class);
    var outage = new DataAccessResourceFailureException("test-only lookup outage");
    doThrow(outage).when(target).get(new GetMemberQuery(writer));
    assertThatThrownBy(this::create)
        .isInstanceOf(DiaryPersistenceUnavailableException.class)
        .hasCause(outage);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from diary_entries where pet_id=?", Long.class, pet))
        .isZero();
  }

  @Test
  @DisplayName("#32-S30 가족이 나간 뒤에도 이름 스냅샷은 남고 기존 가족만 열람할 수 있다")
  void keepsDepartedAuthorsSnapshot() throws Exception {
    var entry = create();
    jdbc.update("delete from pet_family_members where pet_id=? and member_id=?", pet, writer);
    mvc.perform(get(detail(entry.diaryId())).with(jwt().jwt(j -> j.subject("" + writer))))
        .andExpect(status().isForbidden());
    mvc.perform(get(detail(entry.diaryId())).with(jwt().jwt(j -> j.subject("" + owner))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.author.memberId").value(writer))
        .andExpect(jsonPath("$.author.displayName").value("작성 가족"));
  }

  @Test
  @DisplayName("#32-S30 정상 작성의 이름 조회와 일기 저장은 같은 쓰기 TX와 DB 연결을 사용한다")
  void regularWritesDoNotNeedNestedConnections() {
    var readConnection = new java.util.concurrent.atomic.AtomicReference<Integer>();
    var writeConnection = new java.util.concurrent.atomic.AtomicReference<Integer>();
    GetMemberUseCase target = AopTestUtils.getUltimateTargetObject(memberDetails);
    doAnswer(
            inv -> {
              readConnection.set(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
              assertThat(
                      org.springframework.transaction.support.TransactionSynchronizationManager
                          .isCurrentTransactionReadOnly())
                  .isFalse();
              return inv.callRealMethod();
            })
        .when(target)
        .get(new GetMemberQuery(writer));
    com.pawmento.diary.application.port.out.SaveDiaryEntryPort storage =
        AopTestUtils.getUltimateTargetObject(saved);
    doAnswer(
            inv -> {
              writeConnection.set(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
              return inv.callRealMethod();
            })
        .when(storage)
        .save(org.mockito.ArgumentMatchers.any());
    create();
    assertThat(readConnection.get()).isNotNull().isEqualTo(writeConnection.get());
  }
}
