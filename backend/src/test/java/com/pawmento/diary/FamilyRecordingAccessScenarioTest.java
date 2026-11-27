package com.pawmento.diary;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.pawmento.TestcontainersConfiguration;
import com.pawmento.diary.application.port.in.*;
import com.pawmento.family.application.port.out.SavePetFamilyMemberPort;
import com.pawmento.family.domain.PetFamilyMember;
import com.pawmento.global.exception.PermissionDeniedException;
import com.pawmento.global.exception.ResourceNotFoundException;
import com.pawmento.media.application.port.in.*;
import com.pawmento.media.application.port.out.*;
import com.pawmento.media.domain.*;
import com.pawmento.member.application.port.in.*;
import com.pawmento.pet.application.port.in.*;
import com.pawmento.question.application.port.in.*;
import java.io.ByteArrayInputStream;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

// 실제 가족 저장소를 사용하는 포트 통합 검사다. 초대/탈퇴 API와 실제 파일 검증은 대체하지 않는다.
@SpringBootTest(
    properties = {
      "pawmento.auth.jwt-secret=test-only-jwt-secret-0123456789-abcdefghij",
      "pawmento.media.worker.enabled=false"
    })
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
class FamilyRecordingAccessScenarioTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired SignUpMemberUseCase members;
  @Autowired CreatePetUseCase pets;
  @Autowired ChangePetModeUseCase modes;
  @Autowired SavePetFamilyMemberPort family;
  @Autowired CreateDiaryEntryUseCase createDiary;
  @Autowired EditDiaryEntryUseCase editDiary;
  @Autowired DeleteDiaryEntryUseCase deleteDiary;
  @Autowired GetDiaryEntryUseCase getDiary;
  @Autowired EnsureCurrentQuestionPromptUseCase ensure;
  @Autowired CreateQuestionAnswerUseCase createAnswer;
  @Autowired GetQuestionAnswerUseCase getAnswer;
  @Autowired EditQuestionAnswerUseCase editAnswer;
  @Autowired CreateMediaUploadUseCase createUpload;
  @Autowired GetMediaUploadUseCase getUpload;
  @Autowired SaveMediaAssetPort assets;
  @MockitoSpyBean ReadMediaFilePort files;
  long owner, writer, other, pet;

  @BeforeEach
  void setup() {
    owner = member("소유주");
    writer = member("작성 가족");
    other = member("다른 가족");
    pet = pets.create(new CreatePetCommand(owner, "보리", "DOG", null, null)).petId();
    family.save(PetFamilyMember.join(pet, writer, Instant.now()));
    family.save(PetFamilyMember.join(pet, other, Instant.now()));
  }

  long member(String name) {
    return members
        .signUp(new SignUpMemberCommand(UUID.randomUUID() + "@test.example", "Password123!", name))
        .id();
  }

  GetDiaryEntryResult diary(long actor, DiaryWriteFeatures features) {
    return createDiary.create(
        new CreateDiaryEntryCommand(
            actor, pet, "함께한 하루", LocalDate.of(2026, 11, 17), features, "테스트 일기"));
  }

  long prompt(long actor) {
    return ensure
        .ensure(new EnsureCurrentQuestionPromptCommand(actor, pet))
        .current()
        .prompt()
        .promptId();
  }

  String diaryPath(long id) {
    return "/api/v1/diaries/" + id + "?petId=" + pet;
  }

  void removed() {
    jdbc.update("delete from pet_family_members where pet_id=? and member_id=?", pet, writer);
  }

  @Test
  @DisplayName("#32-S30/S31 가족이 일기를 작성·공유하되 본인만 수정하고 타인 위치는 숨긴다 (고침 #133: Q2 삭제 권한)")
  void diaryAccess() throws Exception {
    var written =
        diary(
            writer,
            new DiaryWriteFeatures(List.of(), DiaryLocationChange.manual(37.5, 127.0, "산책길")));
    assertThat(written.createdBy()).isEqualTo(writer);
    mvc.perform(get(diaryPath(written.diaryId())).with(jwt().jwt(j -> j.subject("" + owner))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.createdBy").value(writer))
        .andExpect(jsonPath("$.location").isEmpty());
    mvc.perform(get("/api/v1/diaries?petId=" + pet).with(jwt().jwt(j -> j.subject("" + other))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].createdBy").value(writer));
    mvc.perform(
            get("/api/v1/diaries/calendar?petId=" + pet + "&month=2026-11")
                .with(jwt().jwt(j -> j.subject("" + other))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.days[0].count").value(1));
    assertThatThrownBy(
            () ->
                editDiary.edit(
                    new EditDiaryEntryCommand(owner, pet, written.diaryId(), "타인 수정", null, 0L)))
        .isInstanceOf(PermissionDeniedException.class);
    assertThatThrownBy(
            () ->
                deleteDiary.delete(new DeleteDiaryEntryCommand(other, pet, written.diaryId(), 0L)))
        .isInstanceOf(PermissionDeniedException.class);
    var edited =
        editDiary.edit(new EditDiaryEntryCommand(writer, pet, written.diaryId(), "내 수정", null, 0L));
    deleteDiary.delete(
        new DeleteDiaryEntryCommand(writer, pet, written.diaryId(), edited.version()));
    assertThatThrownBy(() -> getDiary.get(new GetDiaryEntryQuery(owner, pet, written.diaryId())))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"ACTIVE", "GARDEN"})
  @DisplayName("#31-S30/S31 가족은 공통 질문에 개별 답변하고 지난 회차의 추가 답변은 진행을 바꾸지 않는다")
  void sharedQuestion(String mode) {
    modes.changeMode(new ChangePetModeCommand(owner, pet, mode));
    long first = prompt(writer);
    assertThat(prompt(other)).isEqualTo(first);
    var answer = createAnswer.create(new CreateQuestionAnswerCommand(writer, pet, first, "공원 산책"));
    assertThat(answer.completedNow()).isTrue();
    assertThat(answer.answer().author().memberId()).isEqualTo(writer);
    assertThat(answer.answer().author().displayName()).isEqualTo("작성 가족");
    long next = prompt(owner);
    var second = createAnswer.create(new CreateQuestionAnswerCommand(other, pet, first, "공놀이"));
    assertThat(second.completedNow()).isFalse();
    assertThat(prompt(writer)).isEqualTo(next);
    assertThat(getAnswer.get(new GetQuestionAnswerQuery(owner, pet, answer.answer().answerId())))
        .isEqualTo(answer.answer());
    assertThatThrownBy(
            () ->
                editAnswer.edit(
                    new EditQuestionAnswerCommand(
                        owner, pet, answer.answer().answerId(), "타인 수정", 0L)))
        .isInstanceOf(PermissionDeniedException.class);
  }

  @Test
  @DisplayName("#32-S31/#31-S31/#33-S20 비가족은 403·없는 아이는 404이며 ADMIN도 개인 기록을 읽지 못한다")
  void missingAndStrangers() throws Exception {
    long outsider = member("비가족");
    long id = diary(writer, new DiaryWriteFeatures(List.of(), false)).diaryId();
    mvc.perform(
            get(diaryPath(id))
                .with(jwt().jwt(j -> j.subject("" + outsider).claim("role", "ADMIN"))))
        .andExpect(status().isForbidden());
    mvc.perform(
            get("/api/v1/diaries?petId=" + Long.MAX_VALUE)
                .with(jwt().jwt(j -> j.subject("" + owner))))
        .andExpect(status().isNotFound());
    mvc.perform(
            get("/api/v1/questions/prompts/current?petId=" + pet)
                .with(jwt().jwt(j -> j.subject("" + outsider))))
        .andExpect(status().isForbidden());
    assertThatThrownBy(() -> prompt(outsider)).isInstanceOf(PermissionDeniedException.class);
    assertThatThrownBy(
            () -> ensure.ensure(new EnsureCurrentQuestionPromptCommand(owner, Long.MAX_VALUE)))
        .isInstanceOf(ResourceNotFoundException.class);
    assertThatThrownBy(
            () ->
                createUpload.create(
                    new CreateMediaUploadCommand(
                        outsider, pet, "DIARY_ATTACHMENT", "사진.png", "image/png", 10, false)))
        .isInstanceOf(PermissionDeniedException.class);
    assertThatThrownBy(
            () ->
                createUpload.create(
                    new CreateMediaUploadCommand(
                        owner,
                        Long.MAX_VALUE,
                        "DIARY_ATTACHMENT",
                        "사진.png",
                        "image/png",
                        10,
                        false)))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  @DisplayName("#31-S30 서로 다른 가족의 실제 동시 답변 둘은 보존되고 완료는 한 번이다")
  void concurrentAnswers() throws Exception {
    long current = prompt(owner);
    var start = new CyclicBarrier(2);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var a =
          pool.submit(
              () -> {
                start.await(5, TimeUnit.SECONDS);
                return createAnswer.create(
                    new CreateQuestionAnswerCommand(writer, pet, current, "첫 가족"));
              });
      var b =
          pool.submit(
              () -> {
                start.await(5, TimeUnit.SECONDS);
                return createAnswer.create(
                    new CreateQuestionAnswerCommand(other, pet, current, "다른 가족"));
              });
      var first = a.get(10, TimeUnit.SECONDS);
      var second = b.get(10, TimeUnit.SECONDS);
      assertThat(first.completedNow() ^ second.completedNow()).isTrue();
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from question_answer where prompt_id=?", Long.class, current))
          .isEqualTo(2L);
      assertThat(
              jdbc.queryForObject(
                  "select current_prompt_id from question_progress where pet_id=? and"
                      + " mode='ACTIVE'",
                  Long.class,
                  pet))
          .isNull();
    }
  }

  @Test
  @DisplayName("#33-S20 가족은 기록용 파일만 업로드하며 남의 미연결 업로드와 프로필 관리는 허용하지 않는다")
  void uploadsRemainPrivate() {
    var uploaded =
        createUpload.create(
            new CreateMediaUploadCommand(
                writer, pet, "DIARY_ATTACHMENT", "산책.png", "image/png", 10, false));
    assertThat(getUpload.get(new GetMediaUploadQuery(writer, pet, uploaded.assetId())).assetId())
        .isEqualTo(uploaded.assetId());
    assertThatThrownBy(() -> getUpload.get(new GetMediaUploadQuery(owner, pet, uploaded.assetId())))
        .isInstanceOf(PermissionDeniedException.class);
    assertThatThrownBy(
            () ->
                createUpload.create(
                    new CreateMediaUploadCommand(
                        writer, pet, "PET_PROFILE", "프로필.png", "image/png", 10, false)))
        .isInstanceOf(PermissionDeniedException.class);
    removed();
    assertThatThrownBy(
            () -> getUpload.get(new GetMediaUploadQuery(writer, pet, uploaded.assetId())))
        .isInstanceOf(PermissionDeniedException.class);
    assertThatThrownBy(
            () ->
                createUpload.create(
                    new CreateMediaUploadCommand(
                        writer,
                        pet,
                        "QUESTION_ANSWER_ATTACHMENT",
                        "녹음.webm",
                        "audio/webm",
                        10,
                        false)))
        .isInstanceOf(PermissionDeniedException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"DIARY_ATTACHMENT", "QUESTION_ANSWER_ATTACHMENT"})
  @DisplayName("#33-S20 가족은 연결된 원본·썸네일·HEAD·음성 Range를 읽고 회수 뒤 모든 경로가 차단된다")
  void parentFiles(String purpose) throws Exception {
    long image = ready(purpose, false), audio = ready(purpose, true);
    String base;
    if (purpose.equals("DIARY_ATTACHMENT")) {
      var entry = diary(writer, new DiaryWriteFeatures(List.of(image, audio), false));
      base = "/api/v1/diaries/" + entry.diaryId();
    } else {
      var answer =
          createAnswer
              .create(
                  new CreateQuestionAnswerCommand(
                      writer, pet, prompt(writer), "즐거운 하루", List.of(image, audio)))
              .answer();
      base = "/api/v1/questions/answers/" + answer.answerId();
    }
    doAnswer(inv -> new ByteArrayInputStream(new byte[10]))
        .when(files)
        .open(anyString(), anyString());
    String contentSuffix = purpose.equals("DIARY_ATTACHMENT") ? "" : "/content";
    String imagePath = base + "/attachments/" + image;
    String audioPath = base + "/attachments/" + audio;
    for (String path : List.of(imagePath + contentSuffix, imagePath + "/thumbnail")) {
      mvc.perform(get(path + "?petId=" + pet).with(jwt().jwt(j -> j.subject("" + owner))))
          .andExpect(status().isOk());
      mvc.perform(head(path + "?petId=" + pet).with(jwt().jwt(j -> j.subject("" + other))))
          .andExpect(status().isOk());
    }
    mvc.perform(
            get(audioPath + contentSuffix + "?petId=" + pet)
                .header("Range", "bytes=0-3")
                .with(jwt().jwt(j -> j.subject("" + owner))))
        .andExpect(status().isPartialContent())
        .andExpect(header().string("Content-Range", "bytes 0-3/10"));
    // 남의 READY 자산을 새 부모에 연결할 수는 없다.
    assertThatThrownBy(
            () -> {
              if (purpose.equals("DIARY_ATTACHMENT"))
                diary(other, new DiaryWriteFeatures(List.of(image), false));
              else
                createAnswer.create(
                    new CreateQuestionAnswerCommand(
                        other, pet, prompt(other), "복사", List.of(image)));
            })
        .isInstanceOf(ResourceNotFoundException.class);
    removed();
    clearInvocations(files);
    for (String path :
        List.of(imagePath + contentSuffix, imagePath + "/thumbnail", audioPath + contentSuffix)) {
      mvc.perform(get(path + "?petId=" + pet).with(jwt().jwt(j -> j.subject("" + writer))))
          .andExpect(status().isForbidden());
      mvc.perform(head(path + "?petId=" + pet).with(jwt().jwt(j -> j.subject("" + writer))))
          .andExpect(status().isForbidden());
    }
    verifyNoInteractions(files);
    assertThat(
            jdbc.queryForObject(
                "select reference_count from media_assets where id=?", Long.class, image))
        .isEqualTo(1L);
  }

  long ready(String purpose, boolean audio) {
    var now = Instant.now().minusSeconds(60);
    var asset =
        MediaAsset.create(
            writer,
            pet,
            purpose,
            "파일",
            audio ? "audio/webm" : "image/png",
            10,
            UUID.randomUUID().toString(),
            now);
    asset.begin(now);
    asset.received(new StoredUpload(10, "a".repeat(64)), now);
    asset.validated(
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
    return assets.save(asset).getId();
  }
}
