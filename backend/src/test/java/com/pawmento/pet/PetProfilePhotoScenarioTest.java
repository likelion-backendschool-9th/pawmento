package com.pawmento.pet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.CRC32;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
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
import com.pawmento.media.application.exception.MediaPersistenceUnavailableException;
import com.pawmento.media.application.port.in.ProcessMediaWorkUseCase;
import com.pawmento.media.application.service.MediaCleanupTransactions;
import com.pawmento.media.domain.MediaAsset;
import com.pawmento.pet.application.port.out.LockPetPort;
import com.pawmento.pet.application.port.out.PetProfilePhotoMediaPort;

import jakarta.servlet.http.Cookie;

@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"pawmento.auth.jwt-secret=test-only-jwt-secret-0123456789-abcdefghij",
		"pawmento.media.worker.enabled=false",
		"pawmento.postcard.worker.enabled=false"
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
	@MockitoSpyBean PetProfilePhotoMediaPort photoMedia;
	@LocalServerPort int port;

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
		createUpload(owner, "PET_PROFILE", "image/png", Math.toIntExact(MediaAsset.MAX_FILE_BYTES) + 1).andExpect(status().isPayloadTooLarge());
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
		mvc.perform(get(owner.mediaPath(asset, "")).cookie(owner.cookie()))
			.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
		mvc.perform(get(owner.petPath()).cookie(owner.cookie()))
			.andExpect(status().isOk()).andExpect(jsonPath("$.profilePhoto").value(nullValue()));
	}

	@Test
	@DisplayName("#130-S5 실제 multipart 요청은 15MiB의 유효 PNG를 받고 초과·잘못된 본문을 거절한다")
	void multipartSizeBoundary() throws Exception {
		Owner owner = owner();
		int limit = Math.toIntExact(MediaAsset.MAX_FILE_BYTES);
		long asset = session(owner, "PET_PROFILE", "image/png", limit);
		String boundary = "pawmento-photo-test";
		var header = BodyPublishers.ofString("--" + boundary + "\r\n"
			+ "Content-Disposition: form-data; name=\"file\"; filename=\"photo.png\"\r\n"
			+ "Content-Type: image/png\r\n\r\n");
		var footer = BodyPublishers.ofString("\r\n--" + boundary + "--\r\n");
		try (var client = HttpClient.newHttpClient()) {
			var malformed = sendMultipart(client, owner, asset, boundary, BodyPublishers.ofString("broken multipart"));
			assertThat(malformed.statusCode()).as(malformed.body()).isEqualTo(400);
			var overLimit = sendMultipart(client, owner, asset, boundary,
				BodyPublishers.concat(header, BodyPublishers.ofByteArray(new byte[limit + 1]), footer));
			assertThat(overLimit.statusCode()).as(overLimit.body()).isEqualTo(413);
			var allowed = sendMultipart(client, owner, asset, boundary,
				BodyPublishers.concat(header, BodyPublishers.ofByteArray(pngAtSize(limit)), footer));
			assertThat(allowed.statusCode()).as(allowed.body()).isEqualTo(202);
		}
		mvc.perform(get(owner.mediaPath(asset, "")).cookie(owner.cookie()))
			.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PROCESSING"));
		drain();
		assertAsset(asset, "READY", 0, false);
		attach(owner, asset).andExpect(status().isOk());
	}

	private HttpResponse<String> sendMultipart(HttpClient client, Owner owner, long asset, String boundary,
			HttpRequest.BodyPublisher body) throws Exception {
		String origin = "http://localhost:" + port;
		var request = HttpRequest.newBuilder(URI.create(origin + owner.mediaPath(asset, "/content")))
			.timeout(Duration.ofSeconds(15))
			.header("Origin", origin)
			.header("X-Pawmento-Upload", "1")
			.header("Cookie", owner.cookie().getName() + "=" + owner.cookie().getValue())
			.header("Content-Type", "multipart/form-data; boundary=" + boundary)
			.POST(body).build();
		return client.send(request, HttpResponse.BodyHandlers.ofString());
	}

	private byte[] pngAtSize(int size) throws Exception {
		byte[] png = fixture("sample.png");
		int iend = png.length - 12;
		var output = ByteBuffer.allocate(size);
		output.put(png, 0, iend);

		// IEND 앞에 유효한 tEXt 청크를 넣어 픽셀은 유지하고 파일 크기만 맞춘다.
		byte[] type = "tEXt".getBytes(StandardCharsets.US_ASCII);
		byte[] data = new byte[64 * 1024];
		Arrays.fill(data, (byte) 'a');
		data[1] = 0; // 1바이트 키워드 뒤의 구분자
		while (output.position() < size - 12) {
			int remaining = size - 12 - output.position();
			int length = remaining <= data.length + 12 ? remaining - 12 : Math.min(data.length, remaining - 26);
			var crc = new CRC32();
			crc.update(type);
			crc.update(data, 0, length);
			output.putInt(length).put(type).put(data, 0, length).putInt((int) crc.getValue());
		}
		output.put(png, iend, 12);
		return output.array();
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

		byte[] invalidImage = { 1, 2, 3 };
		long unusable = session(owner, "PET_PROFILE", "image/png", invalidImage.length);
		upload(owner, unusable, invalidImage);
		assertUnusableReplacement(owner, current, unusable, "PROCESSING");
		drain();
		assertUnusableReplacement(owner, current, unusable, "REJECTED");

		long expired = png(owner);
		expire(expired);
		attach(owner, expired).andExpect(status().isConflict());
		assertAsset(expired, "READY", 0, false);
		assertAsset(current, "READY", 1, false);
		mvc.perform(get(owner.petPath()).cookie(owner.cookie()))
			.andExpect(jsonPath("$.profilePhoto.assetId").value(current));
	}

	private void assertUnusableReplacement(Owner owner, long current, long unusable, String state) throws Exception {
		mvc.perform(get(owner.mediaPath(unusable, "")).cookie(owner.cookie()))
			.andExpect(status().isOk()).andExpect(jsonPath("$.status").value(state));
		attach(owner, unusable).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONFLICT"));
		mvc.perform(get(owner.petPath()).cookie(owner.cookie()))
			.andExpect(status().isOk()).andExpect(jsonPath("$.profilePhoto.assetId").value(current));
		assertAsset(current, "READY", 1, false);
		assertAsset(unusable, state, 0, false);
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
	@DisplayName("#130-S7 사진 교체 중 참조 변경 직후의 장애는 기존 사진과 삭제 작업을 복구한다")
	void replacementRollsBackAfterUpdatingReferences() throws Exception {
		Owner owner = owner();
		long current = png(owner);
		long replacement = png(owner);
		attach(owner, current).andExpect(status().isOk());

		failAfterChangingReferences(owner, current, List.of(replacement));
		attach(owner, replacement).andExpect(status().isServiceUnavailable());

		assertPhotoSurvivedRollback(owner, current);
		assertAsset(replacement, "READY", 0, false);
	}

	@Test
	@DisplayName("#130-S7 반려동물 삭제 중 사진 참조 해제 직후의 장애는 반려동물과 사진을 복구한다")
	void petDeletionRollsBackAfterReleasingPhoto() throws Exception {
		Owner owner = owner();
		long current = png(owner);
		attach(owner, current).andExpect(status().isOk());

		failAfterChangingReferences(owner, current, List.of());
		mvc.perform(delete(owner.petPath()).cookie(owner.cookie())).andExpect(status().isServiceUnavailable());

		assertPhotoSurvivedRollback(owner, current);
	}

	private void failAfterChangingReferences(Owner owner, long current, List<Long> requested) {
		doAnswer(invocation -> {
			invocation.callRealMethod();
			assertAsset(current, "DELETING", 0, true);
			throw new MediaPersistenceUnavailableException();
		}).when(photoMedia).changeReferences(anyLong(), eq(owner.petId()), eq(List.of(current)), eq(requested));
	}

	private void assertPhotoSurvivedRollback(Owner owner, long asset) throws Exception {
		mvc.perform(get(owner.petPath()).cookie(owner.cookie()))
			.andExpect(status().isOk()).andExpect(jsonPath("$.profilePhoto.assetId").value(asset));
		assertAsset(asset, "READY", 1, false);
		mvc.perform(get(owner.photoPath() + "/" + asset + "/content").cookie(owner.cookie()))
			.andExpect(status().isOk()).andExpect(content().contentType("image/png"));
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
	@DisplayName("#130-S9 사진만 있는 반려동물을 삭제하고 사진 정리 작업을 등록한다")
	void deletePetWithPhoto() throws Exception {
		Owner owner = owner();
		long asset = png(owner);
		attach(owner, asset).andExpect(status().isOk());

		mvc.perform(delete(owner.petPath()).cookie(owner.cookie())).andExpect(status().isNoContent());
		assertAsset(asset, "DELETING", 0, true);
		mvc.perform(get(owner.petPath()).cookie(owner.cookie())).andExpect(status().isNotFound());
	}

	enum RecordKind { DIARY, QUESTION_ANSWER, POSTCARD }

	@ParameterizedTest(name = "{0}")
	@EnumSource(RecordKind.class)
	@DisplayName("#130-S9 일기·질문 답변·엽서가 각각 있으면 반려동물과 사진을 보존한다")
	void recordsBlockPetDeletion(RecordKind kind) throws Exception {
		Owner owner = owner();
		long asset = png(owner);
		attach(owner, asset).andExpect(status().isOk());
		String petQuery = "?petId=" + owner.petId();

		switch (kind) {
			case DIARY -> createDiary(owner);
			case QUESTION_ANSWER -> {
				long prompt = number(mvc.perform(post("/api/v1/questions/prompts/current" + petQuery)
						.cookie(owner.cookie())).andExpect(status().isCreated()).andReturn(), "$.prompt.promptId");
				mvc.perform(post("/api/v1/questions/prompts/" + prompt + "/answers" + petQuery)
						.cookie(owner.cookie()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"content\":\"산책을 다녀왔어요.\"}"))
					.andExpect(status().isCreated());
			}
			case POSTCARD -> {
				mvc.perform(patch(owner.petPath() + "/mode").cookie(owner.cookie())
						.contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"GARDEN\"}"))
					.andExpect(status().isOk());
				long diary = createDiary(owner);
				mvc.perform(post("/api/v1/postcards" + petQuery).cookie(owner.cookie())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"source\":{\"kind\":\"DIARY\",\"id\":" + diary + "},\"consent\":true}"))
					.andExpect(status().isAccepted());
				// 엽서 하나만 남겨 단독으로 삭제를 막는지 확인한다.
				mvc.perform(delete("/api/v1/diaries/" + diary + petQuery + "&version=0").cookie(owner.cookie()))
					.andExpect(status().isNoContent());
			}
		}

		mvc.perform(delete(owner.petPath()).cookie(owner.cookie())).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CONFLICT"));
		assertAsset(asset, "READY", 1, false);
		mvc.perform(get(owner.petPath()).cookie(owner.cookie()))
			.andExpect(status().isOk()).andExpect(jsonPath("$.profilePhoto.assetId").value(asset));
	}

	private long createDiary(Owner owner) throws Exception {
		String date = LocalDate.now(ZoneId.of("Asia/Seoul")).toString();
		return number(mvc.perform(post("/api/v1/diaries?petId=" + owner.petId()).cookie(owner.cookie())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"content\":\"산책 기록\",\"recordedDate\":\"" + date + "\"}"))
			.andExpect(status().isCreated()).andReturn(), "$.diaryId");
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
