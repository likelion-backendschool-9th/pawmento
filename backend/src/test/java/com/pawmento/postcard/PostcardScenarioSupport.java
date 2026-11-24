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
import com.pawmento.postcard.application.port.out.*;
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
      "pawmento.media.worker.enabled=false",
      "pawmento.postcard.worker.enabled=false"
    })
@Import({TestcontainersConfiguration.class, PostcardScenarioSupport.Time.class})
@AutoConfigureMockMvc
abstract class PostcardScenarioSupport {
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

  // 2026-11-21 12:00 Asia/Seoul
  static final Instant NOW = Instant.parse("2026-11-21T03:00:00Z");

  @TestConfiguration(proxyBeanMethods = false)
  static class Time {
    @Bean
    @Primary
    MovableClock postcardClock() {
      return new MovableClock(NOW);
    }
  }

  static final class MovableClock extends Clock {
    private final java.util.concurrent.atomic.AtomicReference<Instant> now;

    MovableClock(Instant now) {
      this.now = new java.util.concurrent.atomic.AtomicReference<>(now);
    }

    void set(Instant instant) {
      now.set(instant);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now.get();
    }
  }

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired CreatePetUseCase pets;
  @Autowired ChangePetModeUseCase modes;
  @Autowired CreateDiaryEntryUseCase diaries;
  @Autowired SaveMediaAssetPort assets;
  @MockitoSpyBean ReadMediaFilePort mediaFiles;
  @MockitoSpyBean LoadPostcardPolicyPort policies;
  @MockitoSpyBean GeneratePostcardImagePort generator;
  @MockitoSpyBean SavePostcardPort savePostcards;
  @MockitoSpyBean com.pawmento.pet.application.port.out.HasPetRecordsPort petRecords;
  @Autowired RequestPostcardUseCase requestUseCase;
  @Autowired ProcessPostcardWorkUseCase worker;
  @Autowired MovableClock clock;

  @BeforeEach
  void thumbnails() {
    clock.set(NOW);
    // 앞 테스트가 남긴 만드는 중 엽서를 이번 테스트의 작업이 집어 가지 않게 한다.
    jdbc.update(
        "update postcards set status='FAILED', error_message='test reset' where"
            + " status='PROCESSING'");
    // 전체 하루 상한(100)은 테스트마다 따로 센다.
    jdbc.update("delete from postcard_daily_total");
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
    // 실제 member 입력 포트에서 이름을 읽을 수 있도록 회원 fixture도 만든다.
    jdbc.update(
        "insert into member(id,email,nickname,tag,role,created_at)"
            + " values(?,?,?,?, 'USER', now()) on conflict(id) do nothing",
        owner,
        "postcard-diary-" + owner + "@test.example",
        "엽서 가족",
        String.format("%04d", owner % 10000));
    return diaries
        .create(
            new CreateDiaryEntryCommand(
                owner,
                pet,
                content,
                LocalDate.of(2026, 11, 20),
                new DiaryWriteFeatures(
                    attachments.length == 0 ? null : List.of(attachments), false),
                "테스트 일기"))
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
    return jdbc.queryForObject(
        "select storage_key from media_assets where id=?", String.class, asset);
  }

  ResultActions call(long member, MockHttpServletRequestBuilder req) throws Exception {
    return mvc.perform(req.with(jwt().jwt(j -> j.subject("" + member))));
  }

  ResultActions request(long member, long pet, String body) throws Exception {
    return call(
        member,
        post("/api/v1/postcards?petId=" + pet)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
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
        .queryForList(
            "select count from postcard_daily_total where usage_date=?", Long.class, TODAY)
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

  String command(long member, long pet, long diary) {
    requestUseCase.request(new RequestPostcardCommand(member, pet, "DIARY", diary, null, true));
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

  long postcard(long member, long pet, String body) throws Exception {
    return created(request(member, pet, body).andExpect(status().isAccepted()));
  }

  String stateOf(long postcard) {
    return jdbc.queryForObject("select status from postcards where id=?", String.class, postcard);
  }

  long imageFiles() throws Exception {
    Path dir = ROOT.resolve("images");
    if (!Files.exists(dir)) return 0;
    try (var files = Files.list(dir)) {
      return files.count();
    }
  }
}
