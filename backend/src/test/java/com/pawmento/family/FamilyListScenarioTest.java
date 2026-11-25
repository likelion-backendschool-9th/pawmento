package com.pawmento.family;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.pawmento.TestcontainersConfiguration;
import com.pawmento.family.application.port.in.IssueInviteCodeCommand;
import com.pawmento.family.application.port.in.IssueInviteCodeUseCase;
import com.pawmento.family.application.port.in.JoinFamilyCommand;
import com.pawmento.family.application.port.in.JoinFamilyUseCase;
import com.pawmento.family.application.port.out.SavePetFamilyMemberPort;
import com.pawmento.family.domain.PetFamilyMember;
import com.pawmento.member.application.port.in.MemberResult;
import com.pawmento.member.application.port.in.SignUpMemberCommand;
import com.pawmento.member.application.port.in.SignUpMemberUseCase;
import com.pawmento.pet.application.port.in.CreatePetCommand;
import com.pawmento.pet.application.port.in.CreatePetUseCase;

// 시나리오는 docs/family/membership.md의 #133-S6이다. 실제 회원·반려동물·가족 테이블로 목록 응답을 확인한다
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "pawmento.auth.jwt-secret=test-only-jwt-secret-0123456789-abcdefghij")
@AutoConfigureMockMvc
class FamilyListScenarioTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private SignUpMemberUseCase signUp;

	@Autowired
	private CreatePetUseCase createPet;

	@Autowired
	private IssueInviteCodeUseCase issue;

	@Autowired
	private JoinFamilyUseCase join;

	@Autowired
	private SavePetFamilyMemberPort saveFamily;

	@Test
	@DisplayName("#133-S6 소유주와 가족은 소유주와 가족 목록을 닉네임·태그·가입 시각과 함께 가입 순서대로 본다")
	void ownerAndFamilySeeList() throws Exception {
		MemberResult owner = member("다윗");
		MemberResult first = member("수민");
		MemberResult second = member("보라");
		long pet = createPet.create(new CreatePetCommand(owner.id(), "보리", "DOG", null, null)).petId();
		joinWithCode(owner.id(), pet, first.id());
		joinWithCode(first.id(), pet, second.id()); // 가족이 만든 코드로도 들어온다

		for (MemberResult viewer : new MemberResult[] { owner, first, second }) {
			list(viewer.id(), pet)
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.owner.memberId").value(equalTo(owner.id()), Long.class))
					.andExpect(jsonPath("$.owner.nickname").value("다윗"))
					.andExpect(jsonPath("$.owner.tag").value(owner.tag()))
					.andExpect(jsonPath("$.owner.joinedAt").doesNotExist())
					.andExpect(jsonPath("$.members.length()").value(2))
					.andExpect(jsonPath("$.members[0].memberId").value(equalTo(first.id()), Long.class))
					.andExpect(jsonPath("$.members[0].nickname").value("수민"))
					.andExpect(jsonPath("$.members[0].tag").value(first.tag()))
					.andExpect(jsonPath("$.members[0].joinedAt").isString())
					.andExpect(jsonPath("$.members[1].memberId").value(equalTo(second.id()), Long.class))
					.andExpect(jsonPath("$.members[1].nickname").value("보라"));
		}
	}

	@Test
	@DisplayName("#133-S6 가족이 없으면 소유주만 있고 members는 빈 배열이다")
	void ownerOnly() throws Exception {
		MemberResult owner = member("다윗");
		long pet = createPet.create(new CreatePetCommand(owner.id(), "콩이", "CAT", null, null)).petId();

		list(owner.id(), pet)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.owner.nickname").value("다윗"))
				.andExpect(jsonPath("$.members.length()").value(0));
	}

	@Test
	@DisplayName("#133-S6 관계없는 회원은 403, 없는 반려동물은 404, petId가 없거나 1 미만이거나 숫자가 아니면 400 VALIDATION, 로그인하지 않으면 401이다")
	void refusals() throws Exception {
		MemberResult owner = member("다윗");
		long pet = createPet.create(new CreatePetCommand(owner.id(), "하루", "DOG", null, null)).petId();

		list(member("남").id(), pet)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("FORBIDDEN"));
		list(owner.id(), Long.MAX_VALUE)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"));
		// petId 형식 오류는 조회 전에 공통 형식의 400 VALIDATION이다
		mvc.perform(get("/api/v1/families").with(jwt().jwt(j -> j.subject("" + owner.id()))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION"));
		for (String bad : new String[] { "0", "-1", "abc" }) {
			mvc.perform(get("/api/v1/families").param("petId", bad)
					.with(jwt().jwt(j -> j.subject("" + owner.id()))))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("VALIDATION"));
		}
		mvc.perform(get("/api/v1/families").param("petId", "" + pet))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("#133-S6 회원 정보를 찾지 못한 가족도 목록에 남고 닉네임·태그만 비어 있다")
	void familyWithoutMemberRow() throws Exception {
		MemberResult owner = member("다윗");
		long pet = createPet.create(new CreatePetCommand(owner.id(), "모카", "DOG", null, null)).petId();
		long unknown = ThreadLocalRandom.current().nextLong(1_000_000_000L, Long.MAX_VALUE / 2);
		saveFamily.save(PetFamilyMember.join(pet, unknown, Instant.now()));

		list(owner.id(), pet)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.members.length()").value(1))
				.andExpect(jsonPath("$.members[0].memberId").value(equalTo(unknown), Long.class))
				.andExpect(jsonPath("$.members[0].nickname").value(nullValue()))
				.andExpect(jsonPath("$.members[0].tag").value(nullValue()));
	}

	@Test
	@DisplayName("#133-S23 가족이 되면 GET /families/pets에 그 반려동물이 들어간 순서대로 보이고, 소유주 자신의 반려동물은 들어가지 않는다")
	void myFamilyPets() throws Exception {
		MemberResult owner = member("다윗");
		MemberResult joiner = member("수민");
		long first = createPet.create(new CreatePetCommand(owner.id(), "보리", "DOG", null, null)).petId();
		long second = createPet.create(new CreatePetCommand(owner.id(), "콩이", "CAT", null, null)).petId();
		joinWithCode(owner.id(), first, joiner.id());
		joinWithCode(owner.id(), second, joiner.id());

		myPets(joiner.id())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].petId").value(equalTo(first), Long.class))
				.andExpect(jsonPath("$[0].name").value("보리"))
				.andExpect(jsonPath("$[0].tag").isString())
				.andExpect(jsonPath("$[0].joinedAt").isString())
				.andExpect(jsonPath("$[1].petId").value(equalTo(second), Long.class))
				.andExpect(jsonPath("$[1].name").value("콩이"));
		myPets(owner.id())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
		mvc.perform(get("/api/v1/families/pets"))
				.andExpect(status().isUnauthorized());
	}

	private ResultActions myPets(long member) throws Exception {
		return mvc.perform(get("/api/v1/families/pets").with(jwt().jwt(j -> j.subject("" + member))));
	}

	private MemberResult member(String nickname) {
		String email = "family-" + UUID.randomUUID() + "@pawmento.test";
		return signUp.signUp(new SignUpMemberCommand(email, "correct-password", nickname));
	}

	private void joinWithCode(long issuer, long pet, long joiner) {
		String code = issue.issue(new IssueInviteCodeCommand(issuer, pet)).code();
		join.join(new JoinFamilyCommand(joiner, code));
	}

	private ResultActions list(long member, long pet) throws Exception {
		return mvc.perform(get("/api/v1/families")
				.param("petId", "" + pet)
				.with(jwt().jwt(j -> j.subject("" + member))));
	}
}
