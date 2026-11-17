package com.pawmento.media;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import com.pawmento.TestcontainersConfiguration;
import com.pawmento.global.exception.StateConflictException;
import com.pawmento.global.security.AccessTokenCookie;
import com.pawmento.media.adapter.out.persistence.MediaValidationJobJpaRepository;
import com.pawmento.media.application.port.in.*;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.*;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "pawmento.auth.jwt-secret=test-only-jwt-secret-0123456789-abcdefghij",
      "pawmento.media.worker.enabled=false"
    })
@AutoConfigureMockMvc
class MediaUploadScenarioTest {
  private static final Path ROOT =
      Path.of(System.getProperty("java.io.tmpdir"), "pawmento-media-scenario-" + UUID.randomUUID());

  @DynamicPropertySource
  static void storage(DynamicPropertyRegistry r) {
    r.add("pawmento.media.root", ROOT::toString);
    r.add("spring.servlet.multipart.location", () -> ROOT.resolve("tmp/multipart").toString());
  }

  @Autowired MockMvc mvc;
  @Autowired MediaValidationJobJpaRepository jobs;
  @Autowired CreateMediaUploadUseCase createUseCase;
  @LocalServerPort int port;
  @Autowired ProcessMediaWorkUseCase worker;

  @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
  com.pawmento.media.adapter.out.storage.BoundedMediaProcess parserProcess;

  @Autowired CleanupMediaUseCase cleanupUseCase;
  @Autowired com.pawmento.media.application.service.MediaWorkTransactions workTransactions;
  @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
  @Autowired com.pawmento.media.application.port.out.LoadMediaAssetPort assets;
  @Autowired com.pawmento.media.application.port.out.CollectMediaOrphansPort orphans;

  @BeforeEach
  void isolateWork() {
    jobs.deleteAll();
  }

  record Owner(Cookie cookie, Long memberId, Long petId) {}

  @AfterAll
  static void clean() throws Exception {
    if (Files.exists(ROOT))
      try (var files = Files.walk(ROOT)) {
        for (Path p : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
      }
  }

  @Test
  @DisplayName("#33-S1 소유자 세션·multipart 수신·PROCESSING과 검증 작업은 함께 저장된다")
  void receive() throws Exception {
    Owner a = owner();
    long id = session(a, "image/png", 3);
    upload(a, id, new byte[] {1, 2, 3})
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.status").value("PROCESSING"))
        .andExpect(jsonPath("$.temporaryStorageKey").doesNotExist())
        .andExpect(jsonPath("$.sha256").doesNotExist());
    assertThat(jobs.findById(id)).isPresent();
    assertThat(jobs.findById(id).orElseThrow().getStatus()).isEqualTo("PENDING");
    mvc.perform(get(path(a, id)).cookie(a.cookie()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PROCESSING"));
    upload(a, id, new byte[] {9, 9, 9}).andExpect(status().isConflict());
  }

  @Test
  @DisplayName("#33-S2 미인증·다른 소유자·다른 반려동물의 세션과 파일 접근을 거부한다")
  void access() throws Exception {
    Owner a = owner(), other = owner();
    long id = session(a, "audio/webm", 3);
    mvc.perform(
            post("/api/v1/pets/" + a.petId() + "/media/uploads")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("image/png", 3)))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/v1/pets/" + a.petId() + "/media/uploads")
                .cookie(other.cookie())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("image/png", 3)))
        .andExpect(status().isForbidden());
    mvc.perform(get(path(a, id)).cookie(other.cookie())).andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/pets/" + other.petId() + "/media/" + id).cookie(other.cookie()))
        .andExpect(status().isForbidden());
    mvc.perform(
            multipart(path(a, id) + "/content")
                .file(new MockMultipartFile("file", "a", null, new byte[] {1, 2, 3}))
                .cookie(other.cookie())
                .header("Origin", "http://localhost")
                .header("X-Pawmento-Upload", "1"))
        .andExpect(status().isForbidden());
    assertThat(jobs.findById(id)).isEmpty();
  }

  @Test
  @DisplayName("#33-S3 영상 신고·빈 파일·한도 초과·미확정 위치 동의 업로드를 거부한다")
  void invalidSession() throws Exception {
    Owner a = owner();
    create(a, "video/mp4", 3).andExpect(status().isUnsupportedMediaType());
    create(a, "image/png", 0).andExpect(status().isBadRequest());
    create(a, "image/png", 15728641).andExpect(status().isPayloadTooLarge());
    mvc.perform(
            post("/api/v1/pets/" + a.petId() + "/media/uploads")
                .cookie(a.cookie())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    body("image/png", 3)
                        .replace(
                            "}",
                            ",\"locationConsent\":{\"granted\":true,\"policyVersion\":\"unconfirmed\"}}")))
        .andExpect(status().isConflict());
  }

  @Test
  @DisplayName("#33-S15 한 multipart 요청에는 file 파일 하나만 허용한다")
  void oneFile() throws Exception {
    Owner a = owner();
    long id = session(a, "audio/webm", 3);
    mvc.perform(
            multipart(path(a, id) + "/content")
                .file(new MockMultipartFile("file", "a", null, new byte[] {1}))
                .file(new MockMultipartFile("file", "b", null, new byte[] {2}))
                .cookie(a.cookie())
                .header("Origin", "http://localhost")
                .header("X-Pawmento-Upload", "1"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            multipart(path(a, id) + "/content")
                .file(new MockMultipartFile("wrong", "a", null, new byte[] {1}))
                .cookie(a.cookie())
                .header("Origin", "http://localhost")
                .header("X-Pawmento-Upload", "1"))
        .andExpect(status().isBadRequest());
    mvc.perform(get(path(a, id)).cookie(a.cookie()))
        .andExpect(jsonPath("$.status").value("PENDING"));
  }

  @Test
  @DisplayName("#33-S16 수신 크기 불일치 시 REJECTED로 보존하고 부분 파일과 검증 작업을 남기지 않는다")
  void mismatch() throws Exception {
    Owner a = owner();
    long id = session(a, "image/png", 3);
    long before = fileCount();
    upload(a, id, new byte[] {1, 2}).andExpect(status().isBadRequest());
    mvc.perform(get(path(a, id)).cookie(a.cookie()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("REJECTED"));
    assertThat(fileCount()).isEqualTo(before);
    assertThat(jobs.findById(id)).isEmpty();
    // 성공적으로 파일을 정리한 실패 세션은 새 세션 건수 제한을 차지하지 않는다.
    for (int i = 0; i < 5; i++) session(a, "audio/webm", 3);
  }

  @Test
  @DisplayName("#33-S15 실제 초과 전송도 413으로 거부하고 실패 파일을 정리한다")
  void actualOverflow() throws Exception {
    Owner a = owner();
    long id = session(a, "image/png", 3);
    long before = fileCount();
    upload(a, id, new byte[15728641]).andExpect(status().isPayloadTooLarge());
    assertThat(fileCount()).isEqualTo(before);
    assertThat(jobs.findById(id)).isEmpty();
  }

  @Test
  @DisplayName("#33-S4 같은 세션을 동시에 수신해도 하나만 PROCESSING으로 진행한다")
  void concurrentUpload() throws Exception {
    Owner a = owner();
    long id = session(a, "audio/webm", 3);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    CountDownLatch start = new CountDownLatch(1);
    try {
      List<Future<Integer>> results = new ArrayList<>();
      for (int i = 0; i < 2; i++)
        results.add(
            pool.submit(
                () -> {
                  start.await();
                  return upload(a, id, new byte[] {1, 2, 3}).andReturn().getResponse().getStatus();
                }));
      start.countDown();
      List<Integer> codes = new ArrayList<>();
      for (var f : results) codes.add(f.get(20, TimeUnit.SECONDS));
      assertThat(codes).containsExactlyInAnyOrder(202, 409);
      assertThat(jobs.findById(id)).isPresent();
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  @DisplayName("#33-S4 동시 세션 생성이 업로더 미완료 파일 5개 제한을 우회하지 못한다")
  void concurrentQuota() throws Exception {
    Owner a = owner();
    ExecutorService pool = Executors.newFixedThreadPool(6);
    CountDownLatch start = new CountDownLatch(1);
    try {
      List<Future<Boolean>> results = new ArrayList<>();
      for (int i = 0; i < 6; i++)
        results.add(
            pool.submit(
                () -> {
                  start.await();
                  try {
                    createUseCase.create(
                        new CreateMediaUploadCommand(
                            a.memberId(),
                            a.petId(),
                            "DIARY_ATTACHMENT",
                            "a.png",
                            "image/png",
                            3,
                            false));
                    return true;
                  } catch (StateConflictException e) {
                    return false;
                  }
                }));
      start.countDown();
      int accepted = 0;
      for (var f : results) if (f.get(30, TimeUnit.SECONDS)) accepted++;
      assertThat(accepted).isEqualTo(5);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  @DisplayName("#33-S17 실제 HTTP에서 인증·헤더·Origin·소유권 검사가 multipart 파싱보다 먼저다")
  void guardBeforeParsing() throws Exception {
    Owner a = owner(), other = owner();
    long id = session(a, "image/png", 3);
    String url = "http://localhost:" + port + path(a, id) + "/content";
    // 이 본문은 multipart로 파싱하면 400이어야 한다. 앞단 거부가 먼저 나와야 한다.
    assertThat(raw(url, null, null, null)).isEqualTo(401);
    assertThat(raw(url, a.cookie(), null, "http://localhost:" + port)).isEqualTo(403);
    assertThat(raw(url, a.cookie(), "1", "http://elsewhere.invalid")).isEqualTo(403);
    assertThat(raw(url, other.cookie(), "1", "http://localhost:" + port)).isEqualTo(403);
    assertThat(raw(url, a.cookie(), "1", "http://localhost:" + port)).isEqualTo(400);
    mvc.perform(get(path(a, id)).cookie(a.cookie()))
        .andExpect(jsonPath("$.status").value("PENDING"));
  }

  int raw(String url, Cookie cookie, String header, String origin) throws Exception {
    var builder =
        HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(15))
            .header("Content-Type", "multipart/form-data; boundary=absent")
            .POST(HttpRequest.BodyPublishers.ofString("broken multipart"));
    if (cookie != null) builder.header("Cookie", cookie.getName() + "=" + cookie.getValue());
    if (header != null) builder.header("X-Pawmento-Upload", header);
    if (origin != null) builder.header("Origin", origin);
    return HttpClient.newHttpClient()
        .send(builder.build(), HttpResponse.BodyHandlers.discarding())
        .statusCode();
  }

  long fileCount() throws Exception {
    try (var files = Files.list(ROOT.resolve("tmp/uploads"))) {
      return files.count();
    }
  }

  byte[] fixture(String name) throws Exception {
    try (var in = getClass().getResourceAsStream("/media/" + name)) {
      return in.readAllBytes();
    }
  }

  void drain() {
    for (int n = 0; n < 100 && worker.processNext(); n++) {}
  }

  String state(long id) {
    return assets.findById(id).orElseThrow().getStatus().name();
  }

  long received(Owner owner, String mime, byte[] data) throws Exception {
    long id = session(owner, mime, data.length);
    upload(owner, id, data).andExpect(status().isAccepted());
    return id;
  }

  @Test
  @DisplayName("#33-S1 실제 JPEG·PNG·WebP 검증 후 READY와 안전한 메타데이터·썸네일을 제공한다")
  void imagesReady() throws Exception {
    Owner a = owner();
    for (var pair :
        List.of(
            new String[] {"sample.png", "image/png"},
            new String[] {"sample.jpg", "image/jpeg"},
            new String[] {"sample.webp", "image/webp"})) {
      long id = received(a, pair[1], fixture(pair[0]));
      drain();
      assertThat(state(id)).isEqualTo("READY");
      mvc.perform(get(path(a, id)).cookie(a.cookie()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.width").value(80))
          .andExpect(jsonPath("$.height").value(40))
          .andExpect(jsonPath("$.previewPath").value(path(a, id) + "/preview"))
          .andExpect(jsonPath("$.storageKey").doesNotExist());
      mvc.perform(get(path(a, id) + "/thumbnail").cookie(a.cookie()))
          .andExpect(status().isOk())
          .andExpect(content().contentType("image/jpeg"));
      assertThat(assets.findById(id).orElseThrow().isTemporaryFileDeleted()).isTrue();
      assertThat(jobs.findById(id).orElseThrow().getStatus()).isEqualTo("DONE");
    }
  }

  @Test
  @DisplayName("#33-S1 실제 WebM·Ogg/Opus·M4A/AAC·MP3를 음성 서비스본으로 검증한다")
  void audioReady() throws Exception {
    Owner a = owner();
    for (var pair :
        List.of(
            new String[] {"sample.webm", "audio/webm"},
            new String[] {"sample.ogg", "audio/ogg"},
            new String[] {"sample.m4a", "audio/mp4"},
            new String[] {"sample.mp3", "audio/mpeg"})) {
      long id = received(a, pair[1], fixture(pair[0]));
      drain();
      assertThat(state(id)).isEqualTo("READY");
      mvc.perform(get(path(a, id)).cookie(a.cookie()))
          .andExpect(jsonPath("$.mimeType").value("audio/mp4"))
          .andExpect(jsonPath("$.durationMs").isNumber())
          .andExpect(jsonPath("$.thumbnailPath").isEmpty());
      var body =
          mvc.perform(get(path(a, id) + "/preview").cookie(a.cookie()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsByteArray();
      assertThat(new String(body, java.nio.charset.StandardCharsets.ISO_8859_1))
          .doesNotContain("PRIVATE_DEVICE_DATA");
    }
  }

  @Test
  @DisplayName("#33-S3 위조 MIME·손상 파일·영상 트랙·5분 초과 음성을 REJECTED로 처리한다")
  void rejectsInvalidMedia() throws Exception {
    for (var sample :
        List.of(
            new Object[] {"image/jpeg", fixture("sample.png")},
            new Object[] {"image/png", new byte[] {1, 2, 3}},
            new Object[] {"audio/mp4", fixture("video.mp4")},
            new Object[] {"audio/ogg", fixture("too-long.ogg")},
            new Object[] {"image/jpeg", Arrays.copyOf(fixture("sample.jpg"), 25)},
            new Object[] {"image/png", fixture("too-many-pixels.png")},
            new Object[] {"audio/mp4", fixture("unsupported.m4a")})) {
      Owner a = owner();
      long id = received(a, (String) sample[0], (byte[]) sample[1]);
      drain();
      assertThat(state(id)).isEqualTo("REJECTED");
      mvc.perform(get(path(a, id) + "/preview").cookie(a.cookie()))
          .andExpect(status().isConflict());
      assertThat(assets.findById(id).orElseThrow().isTemporaryFileDeleted()).isTrue();
    }
  }

  @Test
  @DisplayName("#33-S10 EXIF 방향을 보정하고 원본 메타데이터를 서비스본에서 제거한다")
  void orientationAndPrivacy() throws Exception {
    Owner a = owner();
    long id = received(a, "image/jpeg", fixture("oriented.jpg"));
    drain();
    mvc.perform(get(path(a, id)).cookie(a.cookie()))
        .andExpect(jsonPath("$.status").value("READY"))
        .andExpect(jsonPath("$.width").value(40))
        .andExpect(jsonPath("$.height").value(80));
    byte[] service =
        mvc.perform(get(path(a, id) + "/preview").cookie(a.cookie()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    assertThat(new String(service, java.nio.charset.StandardCharsets.ISO_8859_1))
        .doesNotContain("Exif", "PRIVATE_GPS_DEVICE_DATA");
  }

  @Test
  @DisplayName("#33-S8 미리보기의 인증·소유권·Range·HEAD·416을 검사한다")
  void authenticatedRange() throws Exception {
    Owner a = owner(), b = owner();
    long id = received(a, "audio/ogg", fixture("sample.ogg"));
    drain();
    String url = path(a, id) + "/preview";
    mvc.perform(get(url)).andExpect(status().isUnauthorized());
    mvc.perform(get(url).cookie(b.cookie())).andExpect(status().isForbidden());
    var full =
        mvc.perform(get(url).cookie(a.cookie()))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().string("Cache-Control", "private, no-store"))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    mvc.perform(get(url).cookie(a.cookie()).header("Range", "bytes=0-9"))
        .andExpect(status().isPartialContent())
        .andExpect(header().string("Content-Range", "bytes 0-9/" + full.length))
        .andExpect(content().bytes(Arrays.copyOf(full, 10)));
    mvc.perform(get(url).cookie(a.cookie()).header("Range", "bytes=-10"))
        .andExpect(status().isPartialContent())
        .andExpect(content().bytes(Arrays.copyOfRange(full, full.length - 10, full.length)));
    mvc.perform(head(url).cookie(a.cookie()))
        .andExpect(status().isOk())
        .andExpect(content().bytes(new byte[0]))
        .andExpect(header().string("Content-Length", Integer.toString(full.length)));
    mvc.perform(get(url).cookie(a.cookie()).header("Range", "bytes=" + full.length + "-"))
        .andExpect(status().isRequestedRangeNotSatisfiable());
    mvc.perform(get(path(a, id) + "/thumbnail").cookie(a.cookie()))
        .andExpect(status().isNotFound());
    var client = HttpClient.newHttpClient();
    String cookie = a.cookie().getName() + "=" + a.cookie().getValue();
    var actual =
        client.send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + url))
                .header("Cookie", cookie)
                .header("Range", "bytes=0-9")
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofByteArray());
    assertThat(actual.statusCode()).isEqualTo(206);
    assertThat(actual.body()).isEqualTo(Arrays.copyOf(full, 10));
    var actualHead =
        client.send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + url))
                .header("Cookie", cookie)
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                .build(),
            HttpResponse.BodyHandlers.ofByteArray());
    assertThat(actualHead.statusCode()).isEqualTo(200);
    assertThat(actualHead.body()).isEmpty();
  }

  @Test
  @DisplayName("#33-S7 취소·만료 세션·미사용 READY와 실패 파일을 정리하고 중복 삭제는 허용한다")
  void cancellationAndExpiry() throws Exception {
    Owner a = owner();
    long ready = received(a, "image/png", fixture("sample.png"));
    drain();
    long pending = session(a, "image/png", 3);
    mvc.perform(delete(path(a, ready)).cookie(a.cookie())).andExpect(status().isAccepted());
    drain();
    assertThat(state(ready)).isEqualTo("DELETED");
    mvc.perform(delete(path(a, ready)).cookie(a.cookie())).andExpect(status().isAccepted());
    jdbc.update(
        "update media_assets set created_at=created_at-interval '20 minutes',expires_at=? where"
            + " id=?",
        java.sql.Timestamp.from(Instant.now().minusSeconds(1)),
        pending);
    long expiredReady = received(a, "image/png", fixture("sample.png"));
    drain();
    jdbc.update(
        "update media_assets set created_at=created_at-interval '3"
            + " seconds',ready_at=ready_at-interval '2 seconds',unattached_expires_at=? where id=?",
        java.sql.Timestamp.from(Instant.now().minusSeconds(1)),
        expiredReady);
    long failed = received(a, "image/png", new byte[] {1, 2, 3});
    drain();
    cleanupUseCase.cleanup();
    drain();
    for (long id : List.of(pending, expiredReady, failed))
      assertThat(state(id)).isEqualTo("DELETED");
    for (long id : List.of(ready, expiredReady)) {
      var verified = assets.findById(id).orElseThrow().getVerified();
      assertThat(ROOT.resolve("files").resolve(verified.storageKey() + ".media")).doesNotExist();
      assertThat(ROOT.resolve("files").resolve(verified.storageKey() + ".thumb")).doesNotExist();
    }
  }

  @Test
  @DisplayName("#33-S4 작업 임대 만료 후 재처리하며 이전 임대의 완료는 READY를 덮어쓰지 못한다")
  void leaseRecovery() throws Exception {
    Owner a = owner();
    long id = received(a, "image/png", fixture("sample.png"));
    var old = workTransactions.claim().orElseThrow();
    assertThat(old.assetId()).isEqualTo(id);
    assertThat(workTransactions.claim()).isEmpty();
    jdbc.update(
        "update media_validation_jobs set lease_until=? where asset_id=?",
        java.sql.Timestamp.from(Instant.now().minusSeconds(1)),
        id);
    drain();
    assertThat(state(id)).isEqualTo("READY");
    workTransactions.rejected(old, "STALE_FAILURE");
    assertThat(state(id)).isEqualTo("READY");
    assertThat(jobs.findById(id).orElseThrow().getAttempts()).isEqualTo(2);
  }

  @Test
  @DisplayName("#33-S7 검증 중 취소하면 늦게 온 검증 결과는 파일을 되살리지 않는다")
  void cancelValidation() throws Exception {
    Owner a = owner();
    long id = received(a, "image/png", fixture("sample.png"));
    var work = workTransactions.claim().orElseThrow();
    mvc.perform(delete(path(a, id)).cookie(a.cookie())).andExpect(status().isAccepted());
    var fake =
        new com.pawmento.media.domain.VerifiedMedia(
            UUID.randomUUID().toString(), "image/png", 10, "a".repeat(64), null, 1, 1, null);
    workTransactions.success(work, fake);
    drain();
    assertThat(state(id)).isEqualTo("DELETED");
  }

  @Test
  @DisplayName("#33-S16 오래된 미추적 파일만 제거하며 READY 파일과 현재 작업 파일을 보존한다")
  void orphanCollection() throws Exception {
    Owner a = owner();
    long id = received(a, "image/png", fixture("sample.png"));
    drain();
    Path kept =
        ROOT.resolve("files")
            .resolve(assets.findById(id).orElseThrow().getVerified().storageKey() + ".media");
    var old = java.nio.file.attribute.FileTime.from(Instant.now().minusSeconds(172800));
    Files.setLastModifiedTime(kept, old);
    Path orphan = ROOT.resolve("files").resolve(UUID.randomUUID() + ".media");
    Files.writeString(orphan, "orphan");
    Files.setLastModifiedTime(orphan, old);
    long processing = received(a, "image/png", fixture("sample.png"));
    var lease = workTransactions.claim().orElseThrow();
    Path active = ROOT.resolve("files").resolve(lease.token() + ".media");
    Files.writeString(active, "working");
    Files.setLastModifiedTime(active, old);
    orphans.collect(Instant.now().minusSeconds(86400));
    assertThat(orphan).doesNotExist();
    assertThat(kept).exists();
    assertThat(active).exists();
  }

  @Test
  @DisplayName("#33-S9 삭제 실패를 DB 작업으로 남기고 재시도하여 만료된 파일을 정리한다")
  void durableDeletionRetry() throws Exception {
    Owner a = owner();
    long id = session(a, "image/png", 3);
    var asset = assets.findById(id).orElseThrow();
    Path blocked = ROOT.resolve("tmp/uploads").resolve(asset.getTemporaryStorageKey() + ".upload");
    Files.createDirectory(blocked);
    mvc.perform(delete(path(a, id)).cookie(a.cookie())).andExpect(status().isAccepted());
    drain();
    assertThat(state(id)).isEqualTo("DELETING");
    assertThat(jobs.findById(id).orElseThrow().getStatus()).isEqualTo("RETRY");
    assertThat(jobs.findById(id).orElseThrow().getLastError()).isNotBlank();
    Files.delete(blocked);
    jdbc.update(
        "update media_validation_jobs set next_attempt_at=? where asset_id=?",
        java.sql.Timestamp.from(Instant.now().minusSeconds(1)),
        id);
    drain();
    assertThat(state(id)).isEqualTo("DELETED");
  }

  @Test
  @DisplayName("#33-S9 참조 수가 남아 있는 파일은 취소·만료 정리에서 보존한다")
  void referencedFilePreserved() throws Exception {
    Owner a = owner();
    long id = received(a, "image/png", fixture("sample.png"));
    drain();
    jdbc.update(
        "update media_assets set created_at=created_at-interval '3"
            + " seconds',ready_at=ready_at-interval '2"
            + " seconds',reference_count=1,unattached_expires_at=? where id=?",
        java.sql.Timestamp.from(Instant.now().minusSeconds(1)),
        id);
    mvc.perform(delete(path(a, id)).cookie(a.cookie())).andExpect(status().isConflict());
    cleanupUseCase.cleanup();
    drain();
    assertThat(state(id)).isEqualTo("READY");
    var a1 = assets.findById(id).orElseThrow();
    assertThat(ROOT.resolve("files").resolve(a1.getVerified().storageKey() + ".media")).exists();
  }

  @Test
  @DisplayName("#33-S8 다중 Range의 실제 HTTP 본문 길이는 선언 길이와 일치하고 연결이 끝난다")
  void multipleRanges() throws Exception {
    Owner a = owner();
    long id = received(a, "audio/ogg", fixture("sample.ogg"));
    drain();
    var client = HttpClient.newHttpClient();
    var request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path(a, id) + "/preview"))
            .timeout(Duration.ofSeconds(10))
            .header("Cookie", a.cookie().getName() + "=" + a.cookie().getValue())
            .header("Range", "bytes=0-9,20-29")
            .GET()
            .build();
    var response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
    assertThat(response.statusCode()).isEqualTo(206);
    assertThat(response.headers().firstValue("Content-Type").orElseThrow())
        .startsWith("multipart/byteranges");
    response
        .headers()
        .firstValue("Content-Length")
        .ifPresent(value -> assertThat(Long.parseLong(value)).isEqualTo(response.body().length));
    String body = new String(response.body(), java.nio.charset.StandardCharsets.ISO_8859_1);
    assertThat(body).contains("Content-Range: bytes 0-9/", "Content-Range: bytes 20-29/");
  }

  @Test
  @DisplayName("#33-S15 정리가 끝난 READY 5개 뒤에도 새 업로드를 만들 수 있다")
  void readyDoesNotConsumeActiveQuota() throws Exception {
    Owner a = owner();
    for (int n = 0; n < 6; n++) {
      long id = received(a, "image/png", fixture("sample.png"));
      drain();
      assertThat(state(id)).isEqualTo("READY");
    }
  }

  @Test
  @DisplayName("#33-S3 다중 이미지·뒤따르는 데이터·손상 원본을 공개하지 않는다")
  void staticImageContract() throws Exception {
    byte[] jpg = fixture("sample.jpg"), png = fixture("sample.png"), webp = fixture("sample.webp");
    for (var pair :
        List.of(
            new Object[] {
              "image/jpeg", java.nio.ByteBuffer.allocate(jpg.length * 2).put(jpg).put(jpg).array()
            },
            new Object[] {"image/png", Arrays.copyOf(png, png.length + 1)},
            new Object[] {"image/webp", Arrays.copyOf(webp, webp.length + 1)})) {
      Owner a = owner();
      long id = received(a, (String) pair[0], (byte[]) pair[1]);
      drain();
      assertThat(state(id)).isEqualTo("REJECTED");
    }
  }

  @Test
  @DisplayName("#33-S16 실제 FFmpeg 쓰기 실패 후 원본을 보존하고 재시도하면 READY로 복구한다")
  void realParserOutputFailureRecovery() throws Exception {
    var failFirst = new java.util.concurrent.atomic.AtomicBoolean(true);
    org.mockito.Mockito.doAnswer(
            invocation -> {
              @SuppressWarnings("unchecked")
              var args = (List<String>) invocation.getArgument(0);
              if (args.getLast().endsWith("service.png") && failFirst.getAndSet(false)) {
                var changed = new ArrayList<>(args);
                changed.set(changed.size() - 1, "/dev/full");
                return parserProcess.run(changed);
              }
              return invocation.callRealMethod();
            })
        .when(parserProcess)
        .run(org.mockito.ArgumentMatchers.anyList());
    Owner a = owner();
    byte[] bytes = fixture("sample.png");
    long id = received(a, "image/png", bytes);
    var asset = assets.findById(id).orElseThrow();
    drain();
    assertThat(state(id)).isEqualTo("PROCESSING");
    assertThat(jobs.findById(id).orElseThrow().getStatus()).isEqualTo("RETRY");
    Path original = ROOT.resolve("tmp/uploads").resolve(asset.getTemporaryStorageKey() + ".upload");
    assertThat(Files.readAllBytes(original)).isEqualTo(bytes);
    jdbc.update(
        "update media_validation_jobs set next_attempt_at=? where asset_id=?",
        java.sql.Timestamp.from(Instant.now().minusSeconds(1)),
        id);
    drain();
    assertThat(state(id)).isEqualTo("READY");
    assertThat(original).doesNotExist();
  }

  @Test
  @DisplayName("#33-S3 5분 경계 음성은 컨테이너 패딩 대신 서비스본의 실제 길이로 검증한다")
  void audioDurationBoundary() throws Exception {
    Owner a = owner();
    long id = received(a, "audio/ogg", fixture("five-minutes.ogg"));
    drain();
    assertThat(state(id)).isEqualTo("READY");
    assertThat(assets.findById(id).orElseThrow().getVerified().durationMs())
        .isLessThanOrEqualTo(300000L);
  }

  String path(Owner a, long id) {
    return "/api/v1/pets/" + a.petId() + "/media/" + id;
  }

  ResultActions upload(Owner a, long id, byte[] data) throws Exception {
    return mvc.perform(
        multipart(path(a, id) + "/content")
            .file(new MockMultipartFile("file", "upload.bin", "application/octet-stream", data))
            .cookie(a.cookie())
            .header("Origin", "http://localhost")
            .header("X-Pawmento-Upload", "1"));
  }

  String body(String mime, long size) {
    return "{\"purpose\":\"DIARY_ATTACHMENT\",\"filename\":\"photo.png\",\"mimeType\":\""
        + mime
        + "\",\"sizeBytes\":"
        + size
        + "}";
  }

  ResultActions create(Owner a, String mime, long size) throws Exception {
    return mvc.perform(
        post("/api/v1/pets/" + a.petId() + "/media/uploads")
            .cookie(a.cookie())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body(mime, size)));
  }

  long session(Owner a, String mime, long size) throws Exception {
    return number(
        create(a, mime, size)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andReturn(),
        "$.assetId");
  }

  Owner owner() throws Exception {
    String email = "media-" + UUID.randomUUID() + "@example.com";
    String credentials = "{\"email\":\"" + email + "\",\"password\":\"correct-password\"}";
    mvc.perform(
            post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    credentials.substring(0, credentials.length() - 1) + ",\"nickname\":\"보호자\"}"))
        .andExpect(status().isCreated());
    var login =
        mvc.perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(credentials))
            .andExpect(status().isOk())
            .andReturn();
    Cookie cookie = login.getResponse().getCookie(AccessTokenCookie.NAME);
    var pet =
        mvc.perform(
                post("/api/v1/pets")
                    .cookie(cookie)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"보리\",\"species\":\"DOG\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    return new Owner(cookie, number(login, "$.id"), number(pet, "$.petId"));
  }

  long number(MvcResult r, String path) throws Exception {
    return ((Number) JsonPath.read(r.getResponse().getContentAsString(), path)).longValue();
  }
}
