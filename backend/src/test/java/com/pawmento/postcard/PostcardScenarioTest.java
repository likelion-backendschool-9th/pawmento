package com.pawmento.postcard;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.pawmento.TestcontainersConfiguration;
import com.pawmento.diary.application.port.in.*;
import com.pawmento.media.application.port.out.*;
import com.pawmento.media.domain.*;
import com.pawmento.pet.application.port.in.*;
import com.pawmento.postcard.application.port.in.*;
import com.pawmento.postcard.application.port.out.LoadPostcardPolicyPort;
import com.pawmento.postcard.domain.PostcardPolicy;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(
    properties = {
      "pawmento.auth.jwt-secret=test-only-jwt-secret-0123456789-abcdefghij",
      "pawmento.media.worker.enabled=false"
    })
@Import({TestcontainersConfiguration.class, PostcardScenarioTest.Time.class})
@AutoConfigureMockMvc
class PostcardScenarioTest {
  static final LocalDate TODAY = LocalDate.of(2026, 11, 21);
  static final Path ROOT;
  static final AtomicLong MEMBERS = new AtomicLong(71_000);

  static {
    try {
      ROOT = Files.createTempDirectory("postcards");
    } catch (java.io.IOException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  @DynamicPropertySource
  static void storage(DynamicPropertyRegistry r) {
    r.add("pawmento.postcard.image-root", ROOT::toString);
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class Time {
    @Bean
    @Primary
    Clock postcardClock() {
      // 2026-11-21 12:00 Asia/Seoul
      return Clock.fixed(Instant.parse("2026-11-21T03:00:00Z"), ZoneOffset.UTC);
    }
  }

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired CreatePetUseCase pets;
  @Autowired ChangePetModeUseCase modes;
  @Autowired CreateDiaryEntryUseCase diaries;
  @Autowired RequestPostcardUseCase requests;
  @Autowired SaveMediaAssetPort assets;
  @MockitoSpyBean ReadMediaFilePort mediaFiles;
  @MockitoSpyBean LoadPostcardPolicyPort policies;
  @Autowired Clock clock;

  @BeforeEach
  void thumbnails() {
    // 썸네일 파일 내용은 저장 키로 구분한다.
    doAnswer(i -> new ByteArrayInputStream(("jpeg:" + i.getArgument(0)).getBytes()))
        .when(mediaFiles)
        .open(anyString(), anyString());
  }

  static long member() {
    return MEMBERS.incrementAndGet();
  }

  long pet(long owner, String mode) {
    long id = pets.create(new CreatePetCommand(owner, "콩이", "DOG", "말티즈", null)).petId();
    if (!"ACTIVE".equals(mode)) modes.changeMode(new ChangePetModeCommand(owner, id, mode));
    return id;
  }

  long diary(long owner, long pet, String content, Long... attachments) {
    return diaries
        .create(
            new CreateDiaryEntryCommand(
                owner,
                pet,
                content,
                LocalDate.of(2026, 11, 20),
                new DiaryWriteFeatures(attachments.length == 0 ? null : List.of(attachments), false)))
        .diaryId();
  }

  long asset(long owner, long pet, boolean audio) {
    var now = clock.instant().minusSeconds(60);
    var a =
        MediaAsset.create(
            owner,
            pet,
            "DIARY_ATTACHMENT",
            "file",
            audio ? "audio/webm" : "image/png",
            10,
            UUID.randomUUID().toString(),
            now);
    a.begin(now);
    a.received(new StoredUpload(10, "a".repeat(64)), now);
    a.validated(
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
    return assets.save(a).getId();
  }

  String storageKey(long asset) {
    return jdbc.queryForObject("select storage_key from media_assets where id=?", String.class, asset);
  }

  ResultActions call(long member, MockHttpServletRequestBuilder req) throws Exception {
    return mvc.perform(req.with(jwt().jwt(j -> j.subject("" + member))));
  }

  ResultActions request(long member, long pet, String body) throws Exception {
    return call(
        member,
        post("/api/v1/postcards?petId=" + pet).contentType(MediaType.APPLICATION_JSON).content(body));
  }

  static String diarySource(long diary) {
    return "{\"source\":{\"kind\":\"DIARY\",\"id\":" + diary + "},\"consent\":true}";
  }

  static String withNote(long diary, String note) {
    return "{\"source\":{\"kind\":\"DIARY\",\"id\":"
        + diary
        + "},\"note\":\""
        + note
        + "\",\"consent\":true}";
  }

  long created(ResultActions result) throws Exception {
    String body = result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    return Long.parseLong(body.replaceAll(".*\"postcardId\":(\\d+).*", "$1"));
  }

  long rows(long pet) {
    return jdbc.queryForObject("select count(*) from postcards where pet_id=?", Long.class, pet);
  }

  long used(long member) {
    return jdbc
        .queryForList(
            "select count from postcard_daily_usage where usage_date=? and member_id=?",
            Long.class,
            TODAY,
            member)
        .stream()
        .findFirst()
        .orElse(0L);
  }

  long total() {
    return jdbc
        .queryForList("select count from postcard_daily_total where usage_date=?", Long.class, TODAY)
        .stream()
        .findFirst()
        .orElse(0L);
  }

  long sourceFiles() throws Exception {
    Path dir = ROOT.resolve("sources");
    if (!Files.exists(dir)) return 0;
    try (var files = Files.list(dir)) {
      return files.count();
    }
  }

  @Test
  @DisplayName("#103-S1 정원 모드 일기로 엽서를 요청하면 PROCESSING으로 저장되고 상세에서 보인다")
  void requestFromDiary() throws Exception {
    long a = member(), pet = pet(a, "GARDEN");
    long diary = diary(a, pet, "바닷가에서 노을을 보며 산책했다");

    var result =
        request(a, pet, diarySource(diary))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.postcardId").isNumber())
            .andExpect(jsonPath("$.status").value("PROCESSING"));
    long id = created(result);

    call(a, get("/api/v1/postcards/" + id + "?petId=" + pet))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PROCESSING"))
        .andExpect(jsonPath("$.source.kind").value("DIARY"))
        .andExpect(jsonPath("$.source.id").value(diary))
        .andExpect(jsonPath("$.imageUrl").value(nullValue()))
        .andExpect(jsonPath("$.title").value("콩이와 함께한 작은 순간"))
        .andExpect(jsonPath("$.text").value("바닷가에서 노을을 보며 산책했다"));
  }

  @Test
  @DisplayName("#103-S2 만들 수 없는 요청은 거절되고 엽서·횟수·복사 사진이 늘지 않는다")
  void rejectedRequests() throws Exception {
    long a = member(), b = member();
    long active = pet(a, "ACTIVE"), garden = pet(a, "GARDEN"), other = pet(a, "GARDEN");
    long bPet = pet(b, "GARDEN");
    long activeDiary = diary(a, active, "산책");
    long otherDiary = diary(a, other, "다른 아이의 일기");
    long voice = diary(a, garden, "", asset(a, garden, true));
    long photo = asset(a, garden, false);
    long hiddenPhoto = diary(a, garden, "", photo);
    // 참조가 끊긴 사진은 일기 상세에서 available: false다.
    jdbc.update("update media_assets set reference_count=0 where id=?", photo);
    long text = diary(a, garden, "글");
    long filesBefore = sourceFiles(), totalBefore = total();

    request(a, active, diarySource(activeDiary))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONFLICT"));
    request(a, garden, diarySource(otherDiary))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    request(a, bPet, diarySource(text))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    for (String body :
        List.of(
            "{\"source\":{\"kind\":\"DIARY\",\"id\":" + text + "}}",
            "{\"source\":{\"kind\":\"DIARY\",\"id\":" + text + "},\"consent\":false}",
            "{\"consent\":true}",
            "{\"source\":{\"kind\":\"QUESTION_ANSWER\",\"id\":" + text + "},\"consent\":true}",
            "{\"source\":{\"kind\":\"WALK\",\"id\":" + text + "},\"consent\":true}",
            withNote(text, "가".repeat(201))))
      request(a, garden, body)
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("VALIDATION"));
    for (long diary : List.of(voice, hiddenPhoto))
      request(a, garden, diarySource(diary))
          .andExpect(status().isConflict())
          .andExpect(
              jsonPath("$.message")
                  .value("그림으로 만들 내용이 부족해요. 다른 기록을 고르거나 설명을 더해 주세요."));

    assertThat(rows(active) + rows(garden) + rows(other) + rows(bPet)).isZero();
    assertThat(used(a)).isZero();
    assertThat(total()).isEqualTo(totalBefore);
    assertThat(sourceFiles()).isEqualTo(filesBefore);
  }

  @Test
  @DisplayName("#103-S3 회원당 하루 3장을 넘기면 거절하고, 지워도 횟수는 돌아오지 않는다")
  void memberDailyLimit() throws Exception {
    long a = member(), pet = pet(a, "GARDEN"), diary = diary(a, pet, "공원");
    long first = created(request(a, pet, diarySource(diary)).andExpect(status().isAccepted()));
    request(a, pet, diarySource(diary)).andExpect(status().isAccepted());
    request(a, pet, diarySource(diary)).andExpect(status().isAccepted());

    request(a, pet, diarySource(diary))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("엽서는 베타 기간에 하루 3장까지 만들 수 있어요."));
    assertThat(rows(pet)).isEqualTo(3);

    jdbc.update("delete from postcards where id=?", first);
    request(a, pet, diarySource(diary)).andExpect(status().isConflict());
    assertThat(used(a)).isEqualTo(3);
  }

  @Test
  @DisplayName("#103-S3 같은 회원의 동시 요청은 남은 한 장만 받아들인다")
  void concurrentMemberLimit() throws Exception {
    long a = member(), pet = pet(a, "GARDEN"), diary = diary(a, pet, "공원");
    request(a, pet, diarySource(diary)).andExpect(status().isAccepted());
    request(a, pet, diarySource(diary)).andExpect(status().isAccepted());

    var outcomes = race(List.of(() -> command(a, pet, diary), () -> command(a, pet, diary)));

    assertThat(outcomes).containsExactlyInAnyOrder("accepted", "PostcardLimitExceededException");
    assertThat(rows(pet)).isEqualTo(3);
  }

  @Test
  @DisplayName("#103-S3 전체 하루 상한에 닿으면 서로 다른 회원의 동시 요청 중 하나만 받아들인다")
  void concurrentTotalLimit() throws Exception {
    long c = member(), d = member();
    long cPet = pet(c, "GARDEN"), dPet = pet(d, "GARDEN");
    long cDiary = diary(c, cPet, "공원"), dDiary = diary(d, dPet, "바다");
    long before = total();
    jdbc.update(
        "insert into postcard_daily_total(usage_date, count) values (?, 99)"
            + " on conflict (usage_date) do update set count = 99",
        TODAY);
    try {
      var outcomes =
          race(List.of(() -> command(c, cPet, cDiary), () -> command(d, dPet, dDiary)));

      assertThat(outcomes).containsExactlyInAnyOrder("accepted", "PostcardLimitExceededException");
      assertThat(rows(cPet) + rows(dPet)).isEqualTo(1);
      request(c, cPet, diarySource(cDiary))
          .andExpect(status().isConflict())
          .andExpect(
              jsonPath("$.message").value("오늘은 엽서를 더 만들 수 없어요. 내일 다시 시도해 주세요."));
    } finally {
      jdbc.update("update postcard_daily_total set count=? where usage_date=?", before, TODAY);
    }
  }

  @Test
  @DisplayName("#103-S3 #103-S16 설정에서 회원당 제한을 바꾸면 요청과 남은 횟수가 따른다")
  void configuredLimit() throws Exception {
    long a = member(), pet = pet(a, "GARDEN"), diary = diary(a, pet, "공원");
    request(a, pet, diarySource(diary)).andExpect(status().isAccepted());

    call(a, get("/api/v1/postcards/usage?petId=" + pet))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.beta").value(true))
        .andExpect(jsonPath("$.dailyLimit").value(3))
        .andExpect(jsonPath("$.usedToday").value(1))
        .andExpect(jsonPath("$.remainingToday").value(2));

    doReturn(new PostcardPolicy(true, 4, 100)).when(policies).load();
    call(a, get("/api/v1/postcards/usage?petId=" + pet))
        .andExpect(jsonPath("$.dailyLimit").value(4))
        .andExpect(jsonPath("$.remainingToday").value(3));
    request(a, pet, diarySource(diary)).andExpect(status().isAccepted());
    request(a, pet, diarySource(diary)).andExpect(status().isAccepted());
    request(a, pet, diarySource(diary)).andExpect(status().isAccepted());
    request(a, pet, diarySource(diary)).andExpect(status().isConflict());
  }

  @Test
  @DisplayName("#103-S12 사진이 있는 일기는 첫 사진의 썸네일을 엽서에 복사한다")
  void copiesFirstPhoto() throws Exception {
    long a = member(), pet = pet(a, "GARDEN");
    long p21 = asset(a, pet, false), p22 = asset(a, pet, false), p23 = asset(a, pet, false);
    long withText = diary(a, pet, "벤치에서 쉬었다", p21, p22);
    long photoOnly = diary(a, pet, "", p23);

    long first = created(request(a, pet, diarySource(withText)).andExpect(status().isAccepted()));
    long second = created(request(a, pet, diarySource(photoOnly)).andExpect(status().isAccepted()));

    assertThat(Files.readString(ROOT.resolve("sources/" + first + ".jpg")))
        .startsWith("jpeg:" + storageKey(p21));
    assertThat(Files.readString(ROOT.resolve("sources/" + second + ".jpg")))
        .startsWith("jpeg:" + storageKey(p23));
    assertThat(
            jdbc.queryForObject(
                "select source_photo_key from postcards where id=?", String.class, second))
        .isEqualTo(second + ".jpg");
    call(a, get("/api/v1/postcards/" + second + "?petId=" + pet)).andExpect(jsonPath("$.text").value(""));
  }

  @Test
  @DisplayName("#103-S15 글이 없으면 추가 설명을 문구로 쓰고, 글이 있으면 일기 글을 쓴다")
  void noteFillsMissingText() throws Exception {
    long a = member(), pet = pet(a, "GARDEN");
    long voice = diary(a, pet, "", asset(a, pet, true));
    long text = diary(a, pet, "산책길에서 만난 친구");
    String note = "공원 벤치 옆에서 낙엽을 밟으며 놀았다";

    long fromVoice = created(request(a, pet, withNote(voice, note)).andExpect(status().isAccepted()));
    long fromText = created(request(a, pet, withNote(text, note)).andExpect(status().isAccepted()));

    call(a, get("/api/v1/postcards/" + fromVoice + "?petId=" + pet))
        .andExpect(jsonPath("$.note").value(note))
        .andExpect(jsonPath("$.text").value(note));
    call(a, get("/api/v1/postcards/" + fromText + "?petId=" + pet))
        .andExpect(jsonPath("$.note").value(note))
        .andExpect(jsonPath("$.text").value("산책길에서 만난 친구"));
    assertThat(
            jdbc.queryForList(
                "select source_pet_species || ':' || source_pet_breed from postcards where pet_id=?",
                String.class,
                pet))
        .containsOnly("DOG:말티즈");
  }

  String command(long member, long pet, long diary) {
    requests.request(new RequestPostcardCommand(member, pet, "DIARY", diary, null));
    return "accepted";
  }

  List<String> race(List<Callable<String>> tasks) throws Exception {
    var start = new CountDownLatch(1);
    var pool = Executors.newFixedThreadPool(tasks.size());
    try {
      var futures = new ArrayList<Future<String>>();
      for (var task : tasks)
        futures.add(
            pool.submit(
                () -> {
                  start.await();
                  try {
                    return task.call();
                  } catch (RuntimeException e) {
                    return e.getClass().getSimpleName();
                  }
                }));
      start.countDown();
      var outcomes = new ArrayList<String>();
      for (var f : futures) outcomes.add(f.get(30, TimeUnit.SECONDS));
      return outcomes;
    } finally {
      pool.shutdownNow();
    }
  }
}
