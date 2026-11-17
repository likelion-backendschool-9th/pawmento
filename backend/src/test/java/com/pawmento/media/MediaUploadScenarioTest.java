package com.pawmento.media;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import com.pawmento.TestcontainersConfiguration;
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
    properties = "pawmento.auth.jwt-secret=test-only-jwt-secret-0123456789-abcdefghij")
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
                  } catch (MediaUploadException e) {
                    assertThat(e.kind()).isEqualTo(MediaUploadException.Kind.CONFLICT);
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
