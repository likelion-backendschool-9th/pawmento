package com.pawmento.member;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.pawmento.TestcontainersConfiguration;
import com.pawmento.member.application.exception.NicknameChangeLimitException;
import com.pawmento.member.application.port.in.ChangeMemberNicknameCommand;
import com.pawmento.member.application.port.in.ChangeMemberNicknameUseCase;
import com.pawmento.member.application.port.out.LoadMemberPort;
import com.pawmento.member.application.port.out.SaveMemberPort;
import com.pawmento.member.domain.Member;
import com.pawmento.member.domain.MemberRole;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "pawmento.auth.jwt-secret=test-only-jwt-secret-0123456789-abcdefghij")
class MemberPersistenceTest {
	@Autowired
	private SaveMemberPort saveMemberPort;
	@Autowired
	private LoadMemberPort loadMemberPort;
	@Autowired
	private ChangeMemberNicknameUseCase changeNickname;

	@Test
	@DisplayName("#52-S3 DB와 도메인의 왕복 변환이 회원 정보와 닉네임 변경 시각을 보존한다")
	void roundTripPreservesState() {
		Instant createdAt = Instant.parse("2026-11-01T00:00:00Z");
		Instant changedAt = Instant.parse("2026-11-17T03:00:00Z");
		Member original = Member.register(uniqueEmail(), "password-hash", "보호자", createdAt);
		Member saved = saveMemberPort.save(original);
		Member loaded = loadMemberPort.findById(saved.getId()).orElseThrow();
		loaded.changeNickname("콩이 보호자", changedAt, ZoneId.of("Asia/Seoul"));
		// 순수 도메인을 바꾸는 것만으로 DB가 더티 체킹되지 않는다.
		assertThat(loadMemberPort.findById(saved.getId()).orElseThrow().getNickname()).isEqualTo("보호자");
		saveMemberPort.save(loaded);
		Member restored = loadMemberPort.findByEmail(saved.getEmail()).orElseThrow();
		assertThat(restored.getNickname()).isEqualTo("콩이 보호자");
		assertThat(restored.getNicknameChangedAt()).isEqualTo(changedAt);
		assertThat(restored.getPasswordHash()).isEqualTo("password-hash");
		assertThat(restored.getCreatedAt()).isEqualTo(createdAt);
		assertThat(restored.getRole()).isEqualTo(MemberRole.USER);
		assertThat(restored.getId()).isEqualTo(saved.getId());
	}

	@Test
	@DisplayName("#52-S4 같은 회원의 동시 닉네임 변경 중 하나만 성공하고 한도가 보존된다")
	void simultaneousChangesConsumeLimitOnce() throws Exception {
		Long id = saveMemberPort.save(Member.register(uniqueEmail(), "hash", "보호자", Instant.now())).getId();
		CyclicBarrier start = new CyclicBarrier(2);
		try (var executor = Executors.newFixedThreadPool(2)) {
			Callable<Boolean> first = () -> attempt(start, id, "첫번째");
			Callable<Boolean> second = () -> attempt(start, id, "두번째");
			var a = executor.submit(first);
			var b = executor.submit(second);
			assertThat(List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS)))
					.containsExactlyInAnyOrder(true, false);
		}
		assertThat(loadMemberPort.findById(id).orElseThrow().getNickname()).isIn("첫번째", "두번째");
	}

	private boolean attempt(CyclicBarrier start, Long id, String nickname) throws Exception {
		start.await(5, TimeUnit.SECONDS);
		try {
			changeNickname.changeNickname(new ChangeMemberNicknameCommand(id, nickname));
			return true;
		}
		catch (NicknameChangeLimitException e) {
			return false;
		}
	}

	private String uniqueEmail() {
		return UUID.randomUUID() + "@example.com";
	}
}
