package com.pawmento.pet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.pawmento.TestcontainersConfiguration;
import com.pawmento.global.security.AccessTokenCookie;
import com.pawmento.record.application.port.in.CreateRecordCommand;
import com.pawmento.record.application.port.in.CreateRecordUseCase;

import jakarta.servlet.http.Cookie;

// 시나리오는 docs/pet/manage.md 에 있다
@Import({ TestcontainersConfiguration.class, PetScenarioTest.FixedClockConfig.class })
@SpringBootTest(properties = {
		"pawmento.auth.jwt-secret=test-only-jwt-secret-0123456789-abcdefghij",
		"spring.main.allow-bean-definition-overriding=true"
})
@AutoConfigureMockMvc
class PetScenarioTest {

	private static final String PASSWORD = "correct-password";
	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private CreateRecordUseCase createRecordUseCase;

	@TestConfiguration
	static class FixedClockConfig {

		@Bean
		@Primary
		Clock clock() {
			// #46-S2 Given: 오늘(Asia/Seoul)이 2026-11-17
			return Clock.fixed(Instant.parse("2026-11-16T15:00:00Z"), SEOUL);
		}

	}

	@Test
	@DisplayName("#46-S1 이름·종으로 반려동물을 등록하면 201과 ACTIVE 상세를 받고 목록에 보인다")
	void createPet() throws Exception {
		Cookie cookie = loginAsNewMember();

		MvcResult created = createPet(cookie,
				"""
						{"name":"콩이","species":"DOG","breed":"코기","birthday":"2015-03-02"}
						""")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.petId").isNumber())
			.andExpect(jsonPath("$.name").value("콩이"))
			.andExpect(jsonPath("$.species").value("DOG"))
			.andExpect(jsonPath("$.mode").value("ACTIVE"))
			.andReturn();

		Long petId = readLong(created, "$.petId");

		mockMvc.perform(get("/api/v1/pets").cookie(cookie))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.petId == %d)].name", petId).value("콩이"));
	}

	@Test
	@DisplayName("#46-S2 이름·종·미래 생일이 규칙에 맞지 않으면 400 VALIDATION이고 목록이 늘지 않는다")
	void createPetWithInvalidInput() throws Exception {
		Cookie cookie = loginAsNewMember();
		int before = listSize(cookie);

		createPet(cookie, """
				{"name":"","species":"DOG"}
				""")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION"));

		createPet(cookie, """
				{"name":"%s","species":"DOG"}
				""".formatted("가".repeat(21)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION"));

		createPet(cookie, """
				{"name":"콩이","species":"BIRD"}
				""")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION"));

		createPet(cookie, """
				{"name":"콩이","species":"DOG","birthday":"2026-12-15"}
				""")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION"));

		assertThat(listSize(cookie)).isEqualTo(before);
	}

	@Test
	@DisplayName("#46-S3 소유자는 목록과 상세에서 같은 이름·종·모드를 본다")
	void listAndGetPet() throws Exception {
		Cookie cookie = loginAsNewMember();
		Long petId = createPetId(cookie, """
				{"name":"콩이","species":"DOG","breed":"코기","birthday":"2015-03-02"}
				""");

		mockMvc.perform(get("/api/v1/pets").cookie(cookie))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.petId == %d)].name", petId).value("콩이"))
			.andExpect(jsonPath("$[?(@.petId == %d)].species", petId).value("DOG"))
			.andExpect(jsonPath("$[?(@.petId == %d)].mode", petId).value("ACTIVE"));

		mockMvc.perform(get("/api/v1/pets/{petId}", petId).cookie(cookie))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("콩이"))
			.andExpect(jsonPath("$.species").value("DOG"))
			.andExpect(jsonPath("$.mode").value("ACTIVE"));
	}

	@Test
	@DisplayName("#46-S4 소유자가 이름을 수정하면 상세와 재조회에 반영된다")
	void editPet() throws Exception {
		Cookie cookie = loginAsNewMember();
		Long petId = createPetId(cookie, """
				{"name":"콩이","species":"DOG"}
				""");

		mockMvc
			.perform(patch("/api/v1/pets/{petId}", petId).cookie(cookie)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"콩콩","species":"DOG"}
						"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("콩콩"));

		mockMvc.perform(get("/api/v1/pets/{petId}", petId).cookie(cookie))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("콩콩"));
	}

	@Test
	@DisplayName("#46-S5 같은 이름으로 두 마리를 등록하면 태그가 서로 다르다")
	void createPetsWithSameName() throws Exception {
		Cookie cookie = loginAsNewMember();

		MvcResult first = createPet(cookie, """
				{"name":"보리","species":"DOG"}
				""").andExpect(status().isCreated()).andReturn();
		MvcResult second = createPet(cookie, """
				{"name":"보리","species":"DOG"}
				""").andExpect(status().isCreated()).andReturn();

		String tag1 = readString(first, "$.tag");
		String tag2 = readString(second, "$.tag");
		assertThat(tag1).isNotBlank();
		assertThat(tag2).isNotBlank();
		assertThat(tag1).isNotEqualTo(tag2);

		MvcResult list = mockMvc.perform(get("/api/v1/pets").cookie(cookie)).andExpect(status().isOk()).andReturn();
		assertThat(listSize(cookie)).isEqualTo(2);
		assertThat((java.util.List<?>) JsonPath.read(list.getResponse().getContentAsString(), "$[?(@.name == '보리')]"))
			.hasSize(2);
	}

	@Test
	@DisplayName("#46-S6 소유자가 모드를 GARDEN과 ACTIVE로 바꿀 수 있다")
	void changePetMode() throws Exception {
		Cookie cookie = loginAsNewMember();
		Long petId = createPetId(cookie, """
				{"name":"콩이","species":"DOG"}
				""");

		mockMvc
			.perform(patch("/api/v1/pets/{petId}/mode", petId).cookie(cookie)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"mode":"GARDEN"}
						"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.mode").value("GARDEN"));

		mockMvc
			.perform(patch("/api/v1/pets/{petId}/mode", petId).cookie(cookie)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"mode":"ACTIVE"}
						"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.mode").value("ACTIVE"));
	}

	@Test
	@DisplayName("#46-S7 잘못된 모드면 400 VALIDATION이고 모드는 그대로다")
	void changePetModeWithInvalidValue() throws Exception {
		Cookie cookie = loginAsNewMember();
		Long petId = createPetId(cookie, """
				{"name":"콩이","species":"DOG"}
				""");

		mockMvc
			.perform(patch("/api/v1/pets/{petId}/mode", petId).cookie(cookie)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"mode":"PARK"}
						"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION"));

		mockMvc.perform(get("/api/v1/pets/{petId}", petId).cookie(cookie))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.mode").value("ACTIVE"));
	}

	@Test
	@DisplayName("#46-S8 로그인 없이 목록을 부르면 401이고 로그인 페이지로 넘기지 않는다")
	void listPetsWithoutLogin() throws Exception {
		mockMvc.perform(get("/api/v1/pets"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
			.andExpect(header().doesNotExist(HttpHeaders.LOCATION));
	}

	@Test
	@DisplayName("#46-S9 다른 회원의 반려동물은 조회·수정·모드 변경·삭제할 수 없다")
	void accessOtherMembersPet() throws Exception {
		Cookie owner = loginAsNewMember();
		Long petId = createPetId(owner, """
				{"name":"콩이","species":"DOG"}
				""");
		Cookie other = loginAsNewMember();

		mockMvc.perform(get("/api/v1/pets/{petId}", petId).cookie(other)).andExpect(status().isForbidden());
		mockMvc
			.perform(patch("/api/v1/pets/{petId}", petId).cookie(other)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"훔친콩"}
						"""))
			.andExpect(status().isForbidden());
		mockMvc
			.perform(patch("/api/v1/pets/{petId}/mode", petId).cookie(other)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"mode":"GARDEN"}
						"""))
			.andExpect(status().isForbidden());
		mockMvc.perform(delete("/api/v1/pets/{petId}", petId).cookie(other)).andExpect(status().isForbidden());

		mockMvc.perform(get("/api/v1/pets/{petId}", petId).cookie(owner))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("콩이"))
			.andExpect(jsonPath("$.mode").value("ACTIVE"));
	}

	@Test
	@DisplayName("#46-S10 기록이 없으면 삭제되고, 있으면 409 CONFLICT다")
	void deletePet() throws Exception {
		Cookie cookie = loginAsNewMember();
		Long withoutRecords = createPetId(cookie, """
				{"name":"콩이","species":"DOG"}
				""");
		Long withRecords = createPetId(cookie, """
				{"name":"보리","species":"DOG"}
				""");
		createRecordUseCase.create(new CreateRecordCommand(withRecords, "보리의 하루"));

		mockMvc.perform(delete("/api/v1/pets/{petId}", withoutRecords).cookie(cookie))
			.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/v1/pets/{petId}", withoutRecords).cookie(cookie))
			.andExpect(status().isNotFound());

		mockMvc.perform(delete("/api/v1/pets/{petId}", withRecords).cookie(cookie))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CONFLICT"));
		mockMvc.perform(get("/api/v1/pets/{petId}", withRecords).cookie(cookie))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("보리"));
	}

	private Cookie loginAsNewMember() throws Exception {
		String email = "guardian-" + UUID.randomUUID() + "@example.com";
		mockMvc
			.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"%s","password":"%s","nickname":"보호자"}
						""".formatted(email, PASSWORD)))
			.andExpect(status().isCreated());
		MvcResult login = mockMvc
			.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"%s","password":"%s"}
						""".formatted(email, PASSWORD)))
			.andExpect(status().isOk())
			.andReturn();
		Cookie cookie = login.getResponse().getCookie(AccessTokenCookie.NAME);
		assertThat(cookie).isNotNull();
		return new Cookie(AccessTokenCookie.NAME, cookie.getValue());
	}

	private ResultActions createPet(Cookie cookie, String body) throws Exception {
		return mockMvc.perform(post("/api/v1/pets").cookie(cookie)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private Long createPetId(Cookie cookie, String body) throws Exception {
		MvcResult created = createPet(cookie, body).andExpect(status().isCreated()).andReturn();
		return readLong(created, "$.petId");
	}

	private int listSize(Cookie cookie) throws Exception {
		MvcResult result = mockMvc.perform(get("/api/v1/pets").cookie(cookie)).andExpect(status().isOk()).andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.length()");
	}

	private static Long readLong(MvcResult result, String path) throws Exception {
		Number value = JsonPath.read(result.getResponse().getContentAsString(), path);
		return value.longValue();
	}

	private static String readString(MvcResult result, String path) throws Exception {
		return JsonPath.read(result.getResponse().getContentAsString(), path);
	}

}
