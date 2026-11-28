package com.pawmento.diary;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.pawmento.TestcontainersConfiguration;
import com.pawmento.diary.application.exception.StoredDiaryTitleInvalidException;
import com.pawmento.diary.application.port.in.*;
import com.pawmento.diary.application.port.out.DiaryMapPort;
import com.pawmento.diary.domain.exception.InvalidDiaryTitleException;
import com.pawmento.family.application.port.out.SavePetFamilyMemberPort;
import com.pawmento.family.domain.PetFamilyMember;
import com.pawmento.member.application.port.in.*;
import com.pawmento.pet.application.port.in.*;
import com.pawmento.support.SignUpTokens;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(
    properties = {
      "pawmento.auth.jwt-secret=test-only-jwt-secret-0123456789-abcdefghij",
      "pawmento.media.worker.enabled=false"
    })
@Import({TestcontainersConfiguration.class, DiaryTitleScenarioTest.Time.class})
@AutoConfigureMockMvc
class DiaryTitleScenarioTest {
  @Autowired MockMvc mvc;
  @Autowired SignUpTokens signUpTokens;
  @Autowired JdbcTemplate jdbc;
  @Autowired SignUpMemberUseCase members;
  @Autowired CreatePetUseCase pets;
  @Autowired SavePetFamilyMemberPort families;
  @Autowired CreateDiaryEntryUseCase create;
  @Autowired GetDiaryEntryUseCase get;
  @Autowired TestClock clock;
  @MockitoSpyBean DiaryMapPort map;
  @MockitoSpyBean com.pawmento.diary.application.port.out.DiaryMediaPort attachments;
  @Autowired com.pawmento.media.application.port.out.SaveMediaAssetPort assets;
  final JsonMapper json = JsonMapper.builder().build();
  long owner, writer, pet;

  @TestConfiguration
  static class Time {
    @Bean
    @Primary
    TestClock titleClock() {
      return new TestClock();
    }
  }

  static class TestClock extends Clock {
    final AtomicReference<Instant> now =
        new AtomicReference<>(Instant.parse("2026-11-20T00:00:00Z"));

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
    clock.now.set(Instant.parse("2026-11-20T00:00:00Z"));
    owner = member("소유주");
    writer = member("작성 가족");
    pet = pets.create(new CreatePetCommand(owner, "보리", "DOG", null, null)).petId();
    families.save(PetFamilyMember.join(pet, writer, clock.instant()));
  }

  long member(String name) {
    return members
        .signUp(signUpTokens.command(UUID.randomUUID() + "@test.example", "Password123!", name))
        .id();
  }

  String list() {
    return "/api/v1/diaries?petId=" + pet;
  }

  String path(long id) {
    return "/api/v1/diaries/" + id + "?petId=" + pet;
  }

  ResultActions as(long actor, MockHttpServletRequestBuilder req) throws Exception {
    return mvc.perform(req.with(jwt().jwt(j -> j.subject("" + actor))));
  }

  ResultActions change(long actor, long id, Map<String, Object> body) throws Exception {
    return as(
        actor,
        patch(path(id))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(body)));
  }

  GetDiaryEntryResult create(String title, boolean location) {
    return create.create(
        CreateDiaryEntryCommand.withTitle(
            writer,
            pet,
            "본문",
            LocalDate.of(2026, 11, 17),
            new DiaryWriteFeatures(
                null,
                location
                    ? DiaryLocationChange.manual(37.5, 127.0, "산책길")
                    : DiaryLocationChange.keep()),
            title));
  }

  @Test
  @DisplayName("#32-S33 제목 생성·상세·목록과 무제목 요청을 거절하고 제목만으로 빈 일기를 만들지 않는다")
  void createsAndReads() throws Exception {
    as(
            writer,
            post(list())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "content", "본문", "title", "  산책한 날  ", "recordedDate", "2026-11-17"))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.title").value("산책한 날"));
    var entry = create("  우리 아이  ", false);
    as(owner, get(path(entry.diaryId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("우리 아이"));
    as(owner, get(list()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].title").value("우리 아이"));
    for (String field : List.of("", ",\"title\":null", ",\"title\":\"\"", ",\"title\":\"   \""))
      as(
              writer,
              post(list())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"content\":\"기존 요청\",\"recordedDate\":\"2026-11-17\"" + field + "}"))
          .andExpect(status().isBadRequest());
    as(
            writer,
            post(list())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"제목만\",\"recordedDate\":\"2026-11-17\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("#32-S34 제목만 수정해도 핀 revision을 맞추고 위치 생략 본문 수정·제목 생략 유지·제거 거절을 확인한다")
  void editsPreservePinAndIdentity() throws Exception {
    var entry = create("처음 제목", true);
    var before = get.get(new GetDiaryEntryQuery(writer, pet, entry.diaryId()));
    var originalPin =
        jdbc.queryForMap("select * from diary_location where diary_id=?", entry.diaryId());
    clock.now.set(clock.instant().plusSeconds(30));
    change(writer, entry.diaryId(), Map.of("title", "새 제목", "version", 0))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("새 제목"))
        .andExpect(jsonPath("$.version").value(1))
        .andExpect(jsonPath("$.updatedAt").value(clock.instant().toString()));
    var pin = jdbc.queryForMap("select * from diary_location where diary_id=?", entry.diaryId());
    for (String field :
        List.of("latitude", "longitude", "place_name", "source", "source_asset_id", "created_by"))
      assertThat(pin.get(field)).as(field).isEqualTo(originalPin.get(field));
    assertThat(((Number) pin.get("source_revision")).longValue()).isEqualTo(1);
    change(writer, entry.diaryId(), Map.of("content", "새 본문", "version", 1))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("새 제목"))
        .andExpect(jsonPath("$.version").value(2));
    change(writer, entry.diaryId(), Map.of("title", "새 제목", "version", 2))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(3));
    var clear = new LinkedHashMap<String, Object>();
    clear.put("title", null);
    clear.put("version", 3);
    change(writer, entry.diaryId(), clear).andExpect(status().isBadRequest());
    change(writer, entry.diaryId(), Map.of("title", "   ", "version", 3))
        .andExpect(status().isBadRequest());
    change(writer, entry.diaryId(), Map.of("version", 3)).andExpect(status().isBadRequest());
    var after = get.get(new GetDiaryEntryQuery(writer, pet, entry.diaryId()));
    assertThat(after.author()).isEqualTo(before.author());
    assertThat(after.createdAt()).isEqualTo(before.createdAt());
    assertThat(after.recordedDate()).isEqualTo(before.recordedDate());
    assertThat(after.createdBy()).isEqualTo(writer);
    assertThat(
            jdbc.queryForObject(
                "select source_revision from diary_location where diary_id=?",
                Long.class,
                entry.diaryId()))
        .isEqualTo(3L);
    as(owner, get("/api/v1/diaries/calendar?petId=" + pet + "&month=2026-11"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.days[0].count").value(1));
  }

  @Test
  @DisplayName("#32-S35 HTTP와 직접 입력에서 제목 경계를 거절하고 주소 조회나 저장을 진행하지 않는다")
  void rejectsInvalidTitlesBeforeSideEffects() throws Exception {
    for (Object value :
        List.of(
            "가".repeat(101),
            "🐾".repeat(101),
            "\t",
            "제목\n",
            "앞\u2028뒤",
            "앞\u2029뒤",
            42,
            true,
            List.of("제목"))) {
      as(
              writer,
              post(list())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      json.writeValueAsString(
                          Map.of(
                              "title",
                              value,
                              "content",
                              "본문",
                              "recordedDate",
                              "2026-11-17",
                              "location",
                              Map.of("mode", "MANUAL", "latitude", 37.5, "longitude", 127.0)))))
          .andExpect(status().isBadRequest());
    }
    assertThatThrownBy(() -> create("앞\u2028뒤", false))
        .isInstanceOf(InvalidDiaryTitleException.class);
    verify(map, never()).resolveAddress(anyDouble(), anyDouble());
    assertThat(
            jdbc.queryForObject(
                "select count(*) from diary_entries where pet_id=?", Long.class, pet))
        .isZero();
    for (String escaped : List.of("\\uD800", "\\uDC00", "\\uDC00\\uD800")) {
      as(
              writer,
              post(list())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      "{\"title\":\"앞"
                          + escaped
                          + "뒤\",\"content\":\"본문\",\"recordedDate\":\"2026-11-17\"}"))
          .andExpect(status().isBadRequest());
    }
    var unicode = create("🐾".repeat(100), false);
    assertThat(unicode.title()).isEqualTo("🐾".repeat(100));
    assertThat(get.get(new GetDiaryEntryQuery(writer, pet, unicode.diaryId())).title())
        .isEqualTo(unicode.title());
  }

  @Test
  @DisplayName("#32-S36 타인·회수 후 제목 변경과 오래된 버전을 거부하고 핀 실패는 제목도 롤백한다")
  void permissionsVersionsAndRollback() throws Exception {
    var entry = create("원본", true);
    change(owner, entry.diaryId(), Map.of("title", "타인 수정", "version", 0))
        .andExpect(status().isForbidden());
    change(writer, entry.diaryId(), Map.of("title", "오래된 버전", "version", 9))
        .andExpect(status().isConflict());
    DiaryMapPort target = AopTestUtils.getUltimateTargetObject(map);
    doThrow(new IllegalStateException("test-only pin failure"))
        .when(target)
        .apply(anyLong(), anyLong(), anyLong(), anyLong(), any());
    assertThatThrownBy(
            () -> {
              var cmd =
                  new EditDiaryEntryCommand(
                      writer,
                      pet,
                      entry.diaryId(),
                      null,
                      null,
                      0L,
                      DiaryWriteFeatures.textOnly(),
                      DiaryTitleChange.replace("롤백될 제목"));
              // invoke the real use case to observe transaction rollback
              editor.edit(cmd);
            })
        .isInstanceOf(IllegalStateException.class);
    assertThat(get.get(new GetDiaryEntryQuery(writer, pet, entry.diaryId())).title())
        .isEqualTo("원본");
    assertThat(
            jdbc.queryForObject(
                "select version from diary_entries where id=?", Long.class, entry.diaryId()))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "select source_revision from diary_location where diary_id=?",
                Long.class,
                entry.diaryId()))
        .isZero();
    jdbc.update("delete from pet_family_members where pet_id=? and member_id=?", pet, writer);
    change(writer, entry.diaryId(), Map.of("title", "회수 후", "version", 0))
        .andExpect(status().isForbidden());
  }

  @Autowired EditDiaryEntryUseCase editor;

  @Test
  @DisplayName("#32-S35 저장된 잘못된 제목은 사용자 오류 400이 아닌 저장 데이터 오류로 구분한다")
  void corruptStoredTitle() {
    var entry = create("정상", false);
    for (String raw : List.of("앞\u2028뒤", "  산책  ", "   ", "")) {
      jdbc.update("update diary_entries set title=? where id=?", raw, entry.diaryId());
      assertThatThrownBy(() -> get.get(new GetDiaryEntryQuery(writer, pet, entry.diaryId())))
          .isInstanceOf(StoredDiaryTitleInvalidException.class);
      assertThatThrownBy(
              () ->
                  editor.edit(
                      new EditDiaryEntryCommand(writer, pet, entry.diaryId(), "수정", null, 0L)))
          .isInstanceOf(StoredDiaryTitleInvalidException.class);
      assertThat(
              jdbc.queryForObject(
                  "select title from diary_entries where id=?", String.class, entry.diaryId()))
          .isEqualTo(raw);
    }
  }

  @Test
  @DisplayName("#32-S36 첨부 참조 반영 뒤 실패해도 제목·본문·날짜·버전·연결·참조·정리 작업을 함께 롤백한다")
  void rollsBackTitleWithAttachmentChanges() {
    long previous = readyPhoto(), requested = readyPhoto();
    var entry =
        create.create(
            CreateDiaryEntryCommand.withTitle(
                writer,
                pet,
                "원본",
                LocalDate.of(2026, 11, 17),
                new DiaryWriteFeatures(List.of(previous), false),
                "원래 제목"));
    var before = get.get(new GetDiaryEntryQuery(writer, pet, entry.diaryId()));
    var failure =
        new com.pawmento.media.application.exception.MediaPersistenceUnavailableException(
            new IllegalStateException("test-only after reference changes"));
    com.pawmento.diary.application.port.out.DiaryMediaPort target =
        AopTestUtils.getUltimateTargetObject(attachments);
    doAnswer(
            inv -> {
              inv.callRealMethod();
              throw failure;
            })
        .when(target)
        .change(anyLong(), anyLong(), anyList(), anyList());
    assertThatThrownBy(
            () ->
                editor.edit(
                    new EditDiaryEntryCommand(
                        writer,
                        pet,
                        entry.diaryId(),
                        "새 본문",
                        LocalDate.of(2026, 11, 18),
                        0L,
                        new DiaryWriteFeatures(List.of(requested), false),
                        DiaryTitleChange.replace("새 제목"))))
        .isSameAs(failure);
    assertThat(get.get(new GetDiaryEntryQuery(writer, pet, entry.diaryId()))).isEqualTo(before);
    assertThat(
            jdbc.queryForList(
                "select asset_id from diary_attachments where diary_id=?",
                Long.class,
                entry.diaryId()))
        .containsExactly(previous);
    assertThat(
            jdbc.queryForObject(
                "select reference_count from media_assets where id=?", Long.class, previous))
        .isEqualTo(1L);
    assertThat(
            jdbc.queryForObject(
                "select reference_count from media_assets where id=?", Long.class, requested))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from media_validation_jobs where asset_id in (?,?) and"
                    + " kind='DELETE'",
                Long.class,
                previous,
                requested))
        .isZero();
  }

  @Test
  @DisplayName("#32-S37 기존 무제목 일기는 조회되지만 수정 저장에는 제목이 필요하다")
  void legacyTitleMustBeFilledOnEdit() throws Exception {
    var entry = create("기존", false);
    jdbc.update("update diary_entries set title=null where id=?", entry.diaryId());
    as(writer, get(path(entry.diaryId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").isEmpty());
    change(writer, entry.diaryId(), Map.of("content", "새 본문", "version", 0))
        .andExpect(status().isBadRequest());
    change(writer, entry.diaryId(), Map.of("title", "새 제목", "content", "새 본문", "version", 0))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("새 제목"))
        .andExpect(jsonPath("$.version").value(1));
  }

  long readyPhoto() {
    var now = clock.instant().minusSeconds(60);
    var a =
        com.pawmento.media.domain.MediaAsset.create(
            writer,
            pet,
            "DIARY_ATTACHMENT",
            "사진.png",
            "image/png",
            10,
            UUID.randomUUID().toString(),
            now);
    a.begin(now);
    a.received(new com.pawmento.media.domain.StoredUpload(10, "a".repeat(64)), now);
    a.validated(
        new com.pawmento.media.domain.VerifiedMedia(
            UUID.randomUUID().toString(), "image/png", 10, "a".repeat(64), null, 1, 1, 5L),
        now);
    return assets.save(a).getId();
  }
}
