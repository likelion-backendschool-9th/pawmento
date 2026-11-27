package com.pawmento.question;

import static org.assertj.core.api.Assertions.*;
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
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// 실제 가족 API 대신 멤버십 저장 포트로 구성한다. 소유주의 타인 삭제(Q2)는 별도 HTTP 시나리오에서 검증한다.
@SpringBootTest(
    properties = {
      "pawmento.auth.jwt-secret=test-only-jwt-secret-0123456789-abcdefghij",
      "pawmento.media.worker.enabled=false"
    })
@Import({TestcontainersConfiguration.class, FamilyQuestionAnswerMutationScenarioTest.Time.class})
@AutoConfigureMockMvc
class FamilyQuestionAnswerMutationScenarioTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired SignUpMemberUseCase members;
  @Autowired ChangeMemberNicknameUseCase nicknames;
  @Autowired CreatePetUseCase pets;
  @Autowired ChangePetModeUseCase modes;
  @Autowired SavePetFamilyMemberPort families;
  @Autowired EnsureCurrentQuestionPromptUseCase prompts;
  @Autowired CreateQuestionAnswerUseCase answers;
  @Autowired GetQuestionAnswerUseCase get;
  @Autowired SaveMediaAssetPort media;
  @Autowired TestClock clock;
  long owner, writer, other, pet;

  @TestConfiguration
  static class Time {
    @Bean
    @Primary
    TestClock familyQuestionClock() {
      return new TestClock();
    }
  }

  static class TestClock extends Clock {
    final AtomicReference<Instant> now =
        new AtomicReference<>(Instant.parse("2026-11-20T14:59:00Z"));

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
    clock.now.set(Instant.parse("2026-11-20T14:59:00Z"));
    owner = member("소유주");
    writer = member("작성 가족");
    other = member("다른 가족");
    pet = pets.create(new CreatePetCommand(owner, "보리", "DOG", null, null)).petId();
    families.save(PetFamilyMember.join(pet, writer, clock.instant()));
    families.save(PetFamilyMember.join(pet, other, clock.instant()));
  }

  long member(String name) {
    return members
        .signUp(new SignUpMemberCommand(UUID.randomUUID() + "@test.example", "Password123!", name))
        .id();
  }

  ResultActions as(long actor, MockHttpServletRequestBuilder request) throws Exception {
    return mvc.perform(request.with(jwt().jwt(j -> j.subject("" + actor))));
  }

  @ParameterizedTest
  @CsvSource({"ACTIVE,false", "ACTIVE,true", "GARDEN,false", "GARDEN,true"})
  @DisplayName(
      "#31-S8/S12/S15/S31 가족의 답변 수정·삭제는 작성자와 실제 등록일을 보존하고 첨부 유무와 관계없이 타인 수정·가족의 타인 삭제를 거부한다 (고침 #133: Q2 삭제 권한)")
  void mutationsPreserveAuthorAndProgress(String mode, boolean attached) throws Exception {
    modes.changeMode(new ChangePetModeCommand(owner, pet, mode));
    long prompt =
        prompts
            .ensure(new EnsureCurrentQuestionPromptCommand(writer, pet))
            .current()
            .prompt()
            .promptId();
    // 며칠 지난 질문도 실제 답변 등록일을 사용한다.
    clock.now.set(Instant.parse("2026-11-23T15:01:00Z"));
    long asset = attached ? asset() : 0L;
    var answer =
        answers
            .create(
                new CreateQuestionAnswerCommand(
                    writer, pet, prompt, "원본", attached ? List.of(asset) : List.of()))
            .answer();
    var baseline = get.get(new GetQuestionAnswerQuery(owner, pet, answer.answerId()));
    assertThat(baseline.answeredDate()).isEqualTo(LocalDate.of(2026, 11, 24));
    String path = "/api/v1/questions/answers/" + answer.answerId() + "?petId=" + pet;
    long next =
        prompts
            .ensure(new EnsureCurrentQuestionPromptCommand(owner, pet))
            .current()
            .prompt()
            .promptId();
    for (long actor : new long[] {owner, other}) {
      as(
              actor,
              patch(path)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"content\":\"타인 수정\",\"attachmentIds\":[],\"version\":0}"))
          .andExpect(status().isForbidden());
      if (actor == other) as(actor, delete(path + "&version=0")).andExpect(status().isForbidden());
    }
    assertThat(get.get(new GetQuestionAnswerQuery(owner, pet, answer.answerId())))
        .isEqualTo(baseline);
    if (attached)
      assertThat(
              jdbc.queryForObject(
                  "select reference_count from media_assets where id=?", Long.class, asset))
          .isEqualTo(1L);
    nicknames.changeNickname(new ChangeMemberNicknameCommand(writer, "바뀐 이름"));
    clock.now.set(Instant.parse("2026-11-25T03:00:00Z"));
    as(
            writer,
            patch(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"내 답변 수정\",\"version\":0}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(1))
        .andExpect(jsonPath("$.author.memberId").value(writer))
        .andExpect(jsonPath("$.author.displayName").value("작성 가족"))
        .andExpect(jsonPath("$.answeredDate").value("2026-11-24"))
        .andExpect(jsonPath("$.answeredAt").value(baseline.answeredAt().toString()));
    as(writer, delete(path + "&version=1")).andExpect(status().isNoContent());
    as(owner, get(path)).andExpect(status().isNotFound());
    as(other, get("/api/v1/questions/prompts/current?petId=" + pet))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.prompt.promptId").value(next));
    if (attached) {
      assertThat(
              jdbc.queryForObject(
                  "select reference_count from media_assets where id=?", Long.class, asset))
          .isZero();
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from media_validation_jobs where asset_id=? and kind='DELETE'",
                  Long.class,
                  asset))
          .isEqualTo(1L);
    }
    assertThat(
            jdbc.queryForObject(
                "select count(*) from diary_entries where pet_id=?", Long.class, pet))
        .isZero();
    as(owner, get("/api/v1/diaries/calendar?petId=" + pet + "&month=2026-11"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.days").isEmpty());
  }

  long asset() {
    var now = clock.instant().minusSeconds(60);
    var asset =
        MediaAsset.create(
            writer,
            pet,
            "QUESTION_ANSWER_ATTACHMENT",
            "사진.png",
            "image/png",
            10,
            UUID.randomUUID().toString(),
            now);
    asset.begin(now);
    asset.received(new StoredUpload(10, "a".repeat(64)), now);
    asset.validated(
        new VerifiedMedia(
            UUID.randomUUID().toString(), "image/png", 10, "a".repeat(64), null, 1, 1, 5L),
        now);
    return media.save(asset).getId();
  }
}
