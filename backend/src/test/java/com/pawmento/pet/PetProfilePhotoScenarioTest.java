package com.pawmento.pet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import com.jayway.jsonpath.JsonPath;
import com.pawmento.TestcontainersConfiguration;
import com.pawmento.global.security.AccessTokenCookie;
import com.pawmento.media.adapter.out.persistence.MediaValidationJobJpaRepository;
import com.pawmento.media.application.port.in.ProcessMediaWorkUseCase;
import com.pawmento.media.application.service.MediaCleanupTransactions;
import com.pawmento.pet.application.port.out.HasPetRecordsPort;
import com.pawmento.pet.application.port.out.LockPetPort;

import jakarta.servlet.http.Cookie;

@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
		"pawmento.auth.jwt-secret=test-only-jwt-secret-0123456789-abcdefghij",
		"pawmento.media.worker.enabled=false"
})
class PetProfilePhotoScenarioTest {

	@TempDir
	static Path storage;

	@DynamicPropertySource
	static void mediaStorage(DynamicPropertyRegistry registry) {
		registry.add("pawmento.media.root", () -> storage.toString());
		registry.add("spring.servlet.multipart.location", () -> storage.resolve("tmp/multipart").toString());
	}

	@Autowired MockMvc mvc;
	@Autowired JdbcTemplate jdbc;
	@Autowired ProcessMediaWorkUseCase worker;
	@Autowired MediaCleanupTransactions cleanup;
	@Autowired MediaValidationJobJpaRepository jobs;
	@Autowired TransactionTemplate transactions;
	@MockitoSpyBean LockPetPort petLocks;
	@MockitoSpyBean HasPetRecordsPort petRecords;

	@Test
	@DisplayName("#130-S1 #130-S2 READY 사진 하나를 연결하고 목록·상세와 가공 파일로 조회한다")
	void attachAndRead() throws Exception {
		Owner owner = owner();
		long withoutPhoto = createPet(owner.cookie());
		byte[] original = fixture("oriented.jpg");
		assertThat(new String(original, StandardCharsets.ISO_8859_1)).contains("Exif");
		long asset = ready(owner, "image/jpeg", original);
		String path = owner.photoPath() + "/" + asset;

		for (int i = 0; i < 2; i++) {
			attach(owner, asset)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.profilePhoto.assetId").value(asset))
				.andExpect(jsonPath("$.profilePhoto.contentUrl").value(path + "/content"))
				.andExpect(jsonPath("$.profilePhoto.thumbnailUrl").value(path + "/thumbnail"));
		}
		assertAsset(asset, "READY", 1, false);
		mvc.perform(get(owner.petPath()).cookie(owner.cookie()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.profilePhoto.assetId").value(asset));
		mvc.perform(get("/api/v1/pets").cookie(owner.cookie()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].profilePhoto.assetId").value(asset))
			.andExpect(jsonPath("$[1].petId").value(withoutPhoto))
			.andExpect(jsonPath("$[1].profilePhoto").value(nullValue()));

		for (String variant : new String[] { "/content", "/thumbnail" }) {
			var response = mvc.perform(get(path + variant).cookie(owner.cookie()))
				.andExpect(status().isOk())
				.andExpect(content().contentType("image/jpeg"))
				.andExpect(header().string("Cache-Control", containsString("no-store")))
				.andReturn().getResponse();
			byte[] bytes = response.getContentAsByteArray();
			assertThat(bytes.length).isEqualTo(response.getContentLengthLong());
			assertThat(ImageIO.read(new ByteArrayInputStream(bytes))).isNotNull();
			assertThat(new String(bytes, StandardCharsets.ISO_8859_1)).doesNotContain("Exif");
		}
		mvc.perform(get(owner.mediaPath(asset, "")).cookie(owner.cookie()))
			.andExpect(jsonPath("$.gps").doesNotExist())
			.andExpect(jsonPath("$.locationCandidate").doesNotExist());
	}

	@Test
	@DisplayName("#130-S3 #130-S10 교체한 사진은 숨기고 제거 재시도 시 참조와 삭제 작업을 중복 갱신하지 않는다")
	void replaceAndRemove() throws Exception {
		Owner owner = owner();
		Owner other = owner();
		long first = png(owner);
		long second = png(owner);
		mvc.perform(get(owner.mediaPath(first, "/preview")).cookie(owner.cookie())).andExpect(status().isOk());
		mvc.perform(get(owner.mediaPath(first, "/preview")).cookie(other.cookie())).andExpect(status().isForbidden());
		attach(owner, first).andExpect(status().isOk());
		attach(owner, second).andExpect(status().isOk());
		assertAsset(first, "DELETING", 0, true);
		assertAsset(second, "READY", 1, false);
		mvc.perform(get(owner.photoPath() + "/" + first + "/content").cookie(owner.cookie()))
			.andExpect(status().isNotFound());
		mvc.perform(get(owner.photoPath() + "/" + second + "/content").cookie(owner.cookie()))
			.andExpect(status().isOk()).andExpect(content().contentType("image/png"));

		mvc.perform(delete(owner.photoPath()).cookie(owner.cookie())).andExpect(status().isNoContent());
		var deletion = jobs.findById(second).orElseThrow().getRequestedAt();
		mvc.perform(delete(owner.photoPath()).cookie(owner.cookie())).andExpect(status().isNoContent());
		assertAsset(second, "DELETING", 0, true);
		assertThat(jobs.findById(second).orElseThrow().getRequestedAt()).isEqualTo(deletion);
		mvc.perform(get(owner.petPath()).cookie(owner.cookie()))
			.andExpect(jsonPath("$.profilePhoto").value(nullValue()));
		mvc.perform(get(owner.photoPath() + "/" + second + "/thumbnail").cookie(owner.cookie()))
			.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("#130-S4 비로그인과 타 회원은 사진을 읽거나 업로드·교체·제거할 수 없다")
	void ownerOnly() throws Exception {
		Owner owner = owner();
		Owner other = owner();
		long asset = png(owner);
		attach(owner, asset).andExpect(status().isOk());

		for (var request : List.of(
				get(owner.photoPath() + "/" + asset + "/content"),
				get(owner.photoPath() + "/" + asset + "/thumbnail"),
				put(owner.photoPath()).contentType(MediaType.APPLICATION_JSON).content("{\"assetId\":" + asset + "}"),
				delete(owner.photoPath()),
				post("/api/v1/media/uploads?petId=" + owner.petId()).contentType(MediaType.APPLICATION_JSON)
					.content(uploadBody("PET_PROFILE", "image/png", 1)))) {
			mvc.perform(request).andExpect(status().isUnauthorized());
			mvc.perform(request.cookie(other.cookie())).andExpect(status().isForbidden());
		}
		assertAsset(asset, "READY", 1, false);
	}

	@Test
	@DisplayName("#130-S5 프로필 사진은 지원 이미지와 기존 크기·픽셀 한도를 지킨다")
	void validatesPhotoUploads() throws Exception {
		Owner owner = owner();
		createUpload(owner, "PET_PROFILE", "audio/ogg", 3).andExpect(status().isUnsupportedMediaType());
		createUpload(owner, "PET_PROFILE", "video/mp4", 3).andExpect(status().isUnsupportedMediaType());
		createUpload(owner, "PET_PROFILE", "image/png", 15 * 1024 * 1024 + 1).andExpect(status().isPayloadTooLarge());
		createUpload(owner, "PET_PROFILE", "image/png", 0).andExpect(status().isBadRequest());
		for (String invalid : List.of("null", "0", "-1", "1.2", "\"1\"", "9223372036854775808")) {
			mvc.perform(put(owner.photoPath()).cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
					.content("{\"assetId\":" + invalid + "}"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION"));
		}
		byte[] oversized = fixture("too-many-pixels.png");
		long asset = session(owner, "PET_PROFILE", "image/png", oversized.length);
		upload(owner, asset, oversized);
		drain();
		assertAsset(asset, "REJECTED", 0, false);
	}

	@Test
	@DisplayName("#130-S6 #130-S7 다른 대상·용도나 미완료 자산으로 바꿀 때 연결과 참조가 롤백된다")
	void invalidReplacementKeepsCurrentPhoto() throws Exception {
		Owner owner = owner();
		long current = png(owner);
		attach(owner, current).andExpect(status().isOk());
		Owner otherOwner = owner();
		long other = session(otherOwner, "PET_PROFILE", "image/png", 3);
		Owner otherPet = new Owner(owner.cookie(), createPet(owner.cookie()));
		long wrongPet = session(otherPet, "PET_PROFILE", "image/png", 3);
		long wrongPurpose = session(owner, "DIARY_ATTACHMENT", "image/png", 3);
		for (long invalid : new long[] { other, wrongPet, wrongPurpose }) {
			attach(owner, invalid).andExpect(status().isNotFound());
		}
		long pending = session(owner, "PET_PROFILE", "image/png", 3);
		attach(owner, pending).andExpect(status().isConflict());
		assertAsset(pending, "PENDING", 0, false);

		long expired = png(owner);
		expire(expired);
		attach(owner, expired).andExpect(status().isConflict());
		assertAsset(expired, "READY", 0, false);
		assertAsset(current, "READY", 1, false);
		mvc.perform(get(owner.petPath()).cookie(owner.cookie()))
			.andExpect(jsonPath("$.profilePhoto.assetId").value(current));
	}

	@Test
	@DisplayName("#130-S7 먼저 읽은 버전이 잠금 전에 바뀌면 두 번째 교체는 409다")
	void concurrentReplacement() throws Exception {
		Owner owner = owner();
		long current = png(owner);
		long first = png(owner);
		long second = png(owner);
		attach(owner, current).andExpect(status().isOk());
		var secondRead = new CountDownLatch(1);
		var firstCommitted = new CountDownLatch(1);
		var secondRequest = new AtomicReference<Thread>();
		doAnswer(invocation -> {
			if (Thread.currentThread() == secondRequest.get()) {
				secondRead.countDown();
				assertThat(firstCommitted.await(10, TimeUnit.SECONDS)).isTrue();
			}
			return invocation.callRealMethod();
		}).when(petLocks).lock(owner.petId());

		try (var executor = Executors.newSingleThreadExecutor()) {
			var losingRequest = executor.submit(() -> {
				secondRequest.set(Thread.currentThread());
				attach(owner, second).andExpect(status().isConflict());
				return null;
			});
			try {
				assertThat(secondRead.await(10, TimeUnit.SECONDS)).isTrue();
				attach(owner, first).andExpect(status().isOk());
			}
			finally {
				firstCommitted.countDown();
			}
			losingRequest.get(10, TimeUnit.SECONDS);
		}
		mvc.perform(get(owner.petPath()).cookie(owner.cookie()))
			.andExpect(jsonPath("$.profilePhoto.assetId").value(first));
		assertAsset(current, "DELETING", 0, true);
		assertAsset(first, "READY", 1, false);
		assertAsset(second, "READY", 0, false);
	}

	@Test
	@DisplayName("#130-S8 참조 수가 0이어도 실제 연결된 사진은 조회·직접 삭제·정리에서 보호한다")
	void protectsActualPhotoReference() throws Exception {
		Owner owner = owner();
		long active = png(owner);
		long orphan = png(owner);
		attach(owner, active).andExpect(status().isOk());
		expire(active);
		expire(orphan);

		for (String variant : new String[] { "/content", "/thumbnail" }) {
			mvc.perform(get(owner.photoPath() + "/" + active + variant).cookie(owner.cookie()))
				.andExpect(status().isOk());
		}
		mvc.perform(delete(owner.mediaPath(active, "")).cookie(owner.cookie())).andExpect(status().isConflict());
		cleanup.schedule(active);
		cleanup.schedule(orphan);
		assertAsset(active, "READY", 0, false);
		assertAsset(orphan, "DELETING", 0, true);
	}

	@Test
	@DisplayName("#130-S9 사진만 있는 반려동물은 삭제하고 기록이 있는 반려동물과 사진은 보존한다")
	void deletePetWithPhoto() throws Exception {
		Owner owner = owner();
		long asset = png(owner);
		attach(owner, asset).andExpect(status().isOk());

		Owner blocked = owner();
		long blockedAsset = png(blocked);
		attach(blocked, blockedAsset).andExpect(status().isOk());
		doReturn(true).when(petRecords).existsByPetId(blocked.petId());
		mvc.perform(delete(blocked.petPath()).cookie(blocked.cookie())).andExpect(status().isConflict());
		assertAsset(blockedAsset, "READY", 1, false);
		mvc.perform(get(blocked.petPath()).cookie(blocked.cookie()))
			.andExpect(jsonPath("$.profilePhoto.assetId").value(blockedAsset));

		mvc.perform(delete(owner.petPath()).cookie(owner.cookie())).andExpect(status().isNoContent());
		assertAsset(asset, "DELETING", 0, true);
		mvc.perform(get(owner.petPath()).cookie(owner.cookie())).andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("#130-S1 기존 PostgreSQL 용도 제약을 갱신하면 기존 자산과 새 프로필 사진을 함께 저장한다")
	void upgradesExistingPurposeConstraint() throws Exception {
		String schema = new ClassPathResource("db/media-schema.sql").getContentAsString(StandardCharsets.UTF_8);
		transactions.executeWithoutResult(transaction -> {
			jdbc.execute("""
					create temporary table media_assets (
						purpose varchar(40) not null check (purpose in ('DIARY_ATTACHMENT', 'QUESTION_ANSWER_ATTACHMENT'))
					) on commit drop
					""");
			jdbc.update("insert into pg_temp.media_assets values ('DIARY_ATTACHMENT')");
			jdbc.execute(schema);
			jdbc.execute(schema);
			jdbc.update("insert into pg_temp.media_assets values ('PET_PROFILE')");
			assertThat(jdbc.queryForList("select purpose from pg_temp.media_assets", String.class))
				.containsExactlyInAnyOrder("DIARY_ATTACHMENT", "PET_PROFILE");
		});
	}

	private void expire(long assetId) {
		jdbc.update("""
				update media_assets set reference_count=0, created_at=created_at-interval '3 seconds',
				ready_at=ready_at-interval '2 seconds', unattached_expires_at=? where id=?
				""", Timestamp.from(Instant.now().minusSeconds(1)), assetId);
	}

	private void assertAsset(long assetId, String status, int references, boolean deletion) {
		var asset = jdbc.queryForMap("select status, reference_count from media_assets where id=?", assetId);
		assertThat(asset).containsEntry("status", status).containsEntry("reference_count", references);
		if (deletion) {
			assertThat(jobs.findById(assetId).orElseThrow().getKind()).isEqualTo("DELETE");
		}
		else {
			assertThat(jobs.findById(assetId).filter(job -> "DELETE".equals(job.getKind()))).isEmpty();
		}
	}

	private long png(Owner owner) throws Exception {
		return ready(owner, "image/png", fixture("sample.png"));
	}

	private long ready(Owner owner, String mime, byte[] bytes) throws Exception {
		long asset = session(owner, "PET_PROFILE", mime, bytes.length);
		upload(owner, asset, bytes);
		drain();
		assertThat(jdbc.queryForObject("select status from media_assets where id=?", String.class, asset))
			.isEqualTo("READY");
		return asset;
	}

	private void drain() {
		for (int i = 0; i < 40 && worker.processNext(); i++) {
			// Runs the same validation and deletion worker used in production.
		}
	}

	private void upload(Owner owner, long asset, byte[] bytes) throws Exception {
		mvc.perform(multipart(owner.mediaPath(asset, "/content"))
				.file(new MockMultipartFile("file", "photo.bin", "application/octet-stream", bytes))
				.cookie(owner.cookie()).header("Origin", "http://localhost").header("X-Pawmento-Upload", "1"))
			.andExpect(status().isAccepted());
	}

	private ResultActions attach(Owner owner, long assetId) throws Exception {
		return mvc.perform(put(owner.photoPath()).cookie(owner.cookie())
			.contentType(MediaType.APPLICATION_JSON).content("{\"assetId\":" + assetId + "}"));
	}

	private long session(Owner owner, String purpose, String mime, int bytes) throws Exception {
		return number(createUpload(owner, purpose, mime, bytes).andExpect(status().isCreated()).andReturn(), "$.assetId");
	}

	private ResultActions createUpload(Owner owner, String purpose, String mime, int bytes) throws Exception {
		return mvc.perform(post("/api/v1/media/uploads?petId=" + owner.petId()).cookie(owner.cookie())
			.contentType(MediaType.APPLICATION_JSON).content(uploadBody(purpose, mime, bytes)));
	}

	private String uploadBody(String purpose, String mime, int bytes) {
		String body = """
				{"purpose":"%s","filename":"photo","mimeType":"%s","sizeBytes":%d}
				""".formatted(purpose, mime, bytes).strip();
		if ("PET_PROFILE".equals(purpose)) {
			return body.substring(0, body.length() - 1) + ",\"locationConsent\":{\"granted\":true}}";
		}
		return body;
	}

	private byte[] fixture(String name) throws Exception {
		try (var input = getClass().getResourceAsStream("/media/" + name)) {
			return input.readAllBytes();
		}
	}

	private Owner owner() throws Exception {
		String email = "photo-" + UUID.randomUUID() + "@example.com";
		String credentials = """
				"email":"%s","password":"correct-password"
				""".formatted(email);
		mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
				.content("{" + credentials + ",\"nickname\":\"보호자\"}"))
			.andExpect(status().isCreated());
		Cookie cookie = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{" + credentials + "}"))
			.andExpect(status().isOk()).andReturn().getResponse().getCookie(AccessTokenCookie.NAME);
		return new Owner(cookie, createPet(cookie));
	}

	private long createPet(Cookie cookie) throws Exception {
		return number(mvc.perform(post("/api/v1/pets").cookie(cookie).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"보리\",\"species\":\"DOG\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.profilePhoto").value(nullValue())).andReturn(), "$.petId");
	}

	private long number(MvcResult result, String path) throws Exception {
		return ((Number) JsonPath.read(result.getResponse().getContentAsString(), path)).longValue();
	}

	private record Owner(Cookie cookie, long petId) {
		String petPath() {
			return "/api/v1/pets/" + petId;
		}

		String photoPath() {
			return petPath() + "/profile-photo";
		}

		String mediaPath(long assetId, String suffix) {
			return "/api/v1/media/" + assetId + suffix + "?petId=" + petId;
		}
	}

}
