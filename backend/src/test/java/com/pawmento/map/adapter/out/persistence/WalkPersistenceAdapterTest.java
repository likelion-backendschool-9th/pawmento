package com.pawmento.map.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.pawmento.TestcontainersConfiguration;
import com.pawmento.map.domain.Walk;
import com.pawmento.map.domain.WalkPoint;
import com.pawmento.map.domain.WalkStatus;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

// 시나리오는 docs/map/walk-route.md에 있다. 날짜 구간·좌표 재전송·순서처럼 API로 보이는 동작은 Walk*ScenarioTest가 확인한다.
// 여기에는 요청으로 만들기 어려운 저장소 규칙(정확한 경계값, 오래된 값 덮어쓰기 거절, 영속성 컨텍스트)만 남긴다.
// 진짜 PostgreSQL에서 확인하고, 각 테스트가 끝나면 되돌린다
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "pawmento.auth.jwt-secret=test-only-jwt-secret-0123456789-abcdefghij")
@Transactional
class WalkPersistenceAdapterTest {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
	private static final long MEMBER = 1L;
	private static final long PET = 10L;
	private static final Instant NOW = Instant.parse("2026-11-17T01:00:00Z");

	@Autowired
	private WalkPersistenceAdapter adapter;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@PersistenceContext
	private EntityManager entityManager;

	// 같은 트랜잭션의 영속성 컨텍스트에서 읽으면 DB가 아니라 메모리에 있는 엔티티가 돌아온다.
	// 저장한 뒤에는 DB에 반영하고 컨텍스트를 비워서, 다음 조회가 실제 행을 읽게 한다
	private void flushAndClear() {
		entityManager.flush();
		entityManager.clear();
	}

	private Long saveWalk(long petId, long memberId, Instant startedAt) {
		Long id = adapter.save(Walk.start(petId, memberId, startedAt));
		flushAndClear();
		return id;
	}

	private static Instant seoul(int month, int day, int hour, int minute) {
		return LocalDate.of(2026, month, day).atTime(hour, minute).atZone(SEOUL).toInstant();
	}

	@Test
	@DisplayName("#73-S1 산책을 저장하고 다시 읽으면 같은 값이고, 만든 시각과 수정 시각이 채워진다")
	void saveAndLoad() {
		Long id = saveWalk(PET, MEMBER, NOW);

		Walk loaded = adapter.loadById(id).orElseThrow();

		assertThat(id).isNotNull();
		assertThat(loaded.getId()).isEqualTo(id);
		assertThat(loaded.getPetId()).isEqualTo(PET);
		assertThat(loaded.getCreatedBy()).isEqualTo(MEMBER);
		assertThat(loaded.getStartedAt()).isEqualTo(NOW);
		assertThat(loaded.getEndedAt()).isNull();
		assertThat(loaded.getLastActivityAt()).isEqualTo(NOW);
		assertThat(loaded.status(NOW)).isEqualTo(WalkStatus.IN_PROGRESS);
		Map<String, Object> row = jdbcTemplate.queryForMap("select * from walks where id = ?", id);
		assertThat(row.get("created_at")).isNotNull();
		assertThat(row.get("updated_at")).isNotNull();
	}

	@Test
	@DisplayName("#73-S2 산책을 끝내고 다시 저장하면 같은 행이 바뀌고 새로 쌓이지 않는다")
	void saveAgainUpdatesState() {
		Long id = saveWalk(PET, MEMBER, NOW);
		Walk walk = adapter.loadById(id).orElseThrow();
		Instant endedAt = NOW.plusSeconds(120);
		walk.end(1L, endedAt);

		adapter.save(walk);
		flushAndClear();

		Walk loaded = adapter.loadById(id).orElseThrow();
		assertThat(loaded.getEndedAt()).isEqualTo(endedAt);
		assertThat(loaded.status(endedAt)).isEqualTo(WalkStatus.ENDED);
		assertThat(jdbcTemplate.queryForObject("select count(*) from walks", Integer.class)).isEqualTo(1);
	}

	@Test
	@DisplayName("#73-S4 이미 읽어 둔 산책이 있어도 잠금 조회는 그사이 커밋된 끝난 시각을 반영한 최신 상태를 돌려준다")
	void lockedReadIsFreshEvenIfAlreadyLoaded() {
		Long id = saveWalk(PET, MEMBER, NOW);
		flushAndClear();
		// 읽어 두면 영속성 컨텍스트에 오래된 상태가 남는다
		Walk stale = adapter.loadById(id).orElseThrow();
		assertThat(stale.getEndedAt()).isNull();
		// 다른 트랜잭션이 그사이 산책을 끝내고 커밋한 것과 같다
		jdbcTemplate.update("update walks set ended_at = ?, last_activity_at = ? where id = ?",
				java.sql.Timestamp.from(NOW.plusSeconds(60)), java.sql.Timestamp.from(NOW.plusSeconds(60)), id);

		Walk locked = adapter.loadByIdForUpdate(id).orElseThrow();

		assertThat(locked.getEndedAt()).isEqualTo(NOW.plusSeconds(60));
		assertThat(locked.getLastActivityAt()).isEqualTo(NOW.plusSeconds(60));
	}

	@Test
	@DisplayName("#73-S9 소유자 조회는 반려동물과 시작한 회원만 읽고, 산책 엔티티를 영속성 컨텍스트에 남기지 않는다")
	void loadOwnershipReadsOnlyIdentity() {
		Long id = saveWalk(PET, MEMBER, NOW);
		flushAndClear();

		var ownership = adapter.loadOwnership(id).orElseThrow();
		jdbcTemplate.update("update walks set ended_at = ? where id = ?", java.sql.Timestamp.from(NOW.plusSeconds(60)),
				id);

		assertThat(ownership.petId()).isEqualTo(PET);
		assertThat(ownership.createdBy()).isEqualTo(MEMBER);
		// 소유자 조회가 엔티티를 남겼다면 아래 조회가 오래된 상태(끝나지 않음)를 돌려준다
		assertThat(adapter.loadById(id).orElseThrow().getEndedAt()).isEqualTo(NOW.plusSeconds(60));
		assertThat(adapter.loadOwnership(999_999_999L)).isEmpty();
	}

	@Test
	@DisplayName("#73-S7 여러 산책의 좌표를 한 번에 읽어 산책별로 기록 시각 순서로 묶고, 좌표가 없는 산책은 빈 목록이다")
	void loadPointsOfSeveralWalks() {
		Long first = saveWalk(PET, MEMBER, NOW);
		Long second = saveWalk(PET, MEMBER, NOW.plusSeconds(3600));
		Long empty = saveWalk(PET, MEMBER, NOW.plusSeconds(7200));
		adapter.insertIfAbsent(List.of(WalkPoint.of(second, 37.3, 127.3, NOW.plusSeconds(3630)),
				WalkPoint.of(first, 37.2, 127.2, NOW.plusSeconds(30)),
				WalkPoint.of(second, 37.1, 127.1, NOW.plusSeconds(3615)),
				WalkPoint.of(first, 37.0, 127.0, NOW.plusSeconds(15))));
		flushAndClear();

		var grouped = adapter.loadByWalkIds(List.of(first, second, empty));

		assertThat(grouped.keySet()).containsExactly(first, second, empty);
		assertThat(grouped.get(first)).extracting(WalkPoint::getLatitude).containsExactly(37.0, 37.2);
		assertThat(grouped.get(second)).extracting(WalkPoint::getLatitude).containsExactly(37.1, 37.3);
		assertThat(grouped.get(empty)).isEmpty();
		assertThat(adapter.loadByWalkIds(List.of())).isEmpty();
	}

	@Test
	@DisplayName("#73-S2 다른 산책의 id로 저장하면 거절하고 값을 바꾸지 않는다")
	void saveRejectsDifferentIdentity() {
		Long id = saveWalk(PET, MEMBER, NOW);
		Walk other = Walk.restore(id, PET + 1, MEMBER, NOW, null, NOW);

		assertThatThrownBy(() -> adapter.save(other)).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> adapter.save(Walk.restore(-1L, PET, MEMBER, NOW, null, NOW)))
			.isInstanceOf(IllegalStateException.class);
		flushAndClear();
		assertThat(adapter.loadById(id).orElseThrow().getPetId()).isEqualTo(PET);
	}

	@Test
	@DisplayName("#73-S2 먼저 정해진 끝낸 시각은 오래된 값을 가진 산책이 다시 저장해도 덮이지 않는다")
	void savedEndedAtIsNotOverwritten() {
		Long id = saveWalk(PET, MEMBER, NOW);
		// 두 요청이 잠그지 않고 같은 산책을 읽은 상황이다. 둘 다 아직 끝나지 않은 상태를 들고 있다
		Walk first = adapter.loadById(id).orElseThrow();
		Walk second = adapter.loadById(id).orElseThrow();
		Instant firstEndedAt = NOW.plusSeconds(60);
		first.end(1L, firstEndedAt);
		adapter.save(first);
		flushAndClear();
		second.end(1L, NOW.plusSeconds(90));

		assertThatThrownBy(() -> {
			adapter.save(second);
			flushAndClear();
		}).isInstanceOf(IllegalStateException.class);

		entityManager.clear();
		assertThat(adapter.loadById(id).orElseThrow().getEndedAt()).isEqualTo(firstEndedAt);
	}

	@Test
	@DisplayName("#73-S2 같은 끝낸 시각으로 다시 저장하는 것은 허용한다")
	void savingSameEndedAtIsAllowed() {
		Long id = saveWalk(PET, MEMBER, NOW);
		Walk walk = adapter.loadById(id).orElseThrow();
		walk.end(1L, NOW.plusSeconds(60));
		adapter.save(walk);
		flushAndClear();

		Walk again = adapter.loadById(id).orElseThrow();
		adapter.save(again);
		flushAndClear();

		assertThat(adapter.loadById(id).orElseThrow().getEndedAt()).isEqualTo(NOW.plusSeconds(60));
	}

	@Test
	@DisplayName("#73-S6 같은 기록 시각이어도 다른 산책에는 따로 쌓인다")
	void sameRecordedAtInDifferentWalks() {
		Long walkA = saveWalk(PET, MEMBER, NOW);
		Long walkB = saveWalk(PET + 1, MEMBER, NOW);

		adapter.insertIfAbsent(List.of(WalkPoint.of(walkA, 37.5, 127.0, NOW)));
		int inserted = adapter.insertIfAbsent(List.of(WalkPoint.of(walkB, 37.5, 127.0, NOW)));

		assertThat(inserted).isEqualTo(1);
		assertThat(adapter.countByWalkId(walkA)).isEqualTo(1);
		assertThat(adapter.countByWalkId(walkB)).isEqualTo(1);
	}

	@Test
	@DisplayName("#73-S9 조회 조건이 비어 있으면 거절한다")
	void loadStartedBetweenRequiresArguments() {
		Instant from = seoul(11, 17, 0, 0);
		Instant to = seoul(11, 18, 0, 0);

		assertThatThrownBy(() -> adapter.loadStartedBetween(null, PET, from, to))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> adapter.loadStartedBetween(MEMBER, null, from, to))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> adapter.loadStartedBetween(MEMBER, PET, null, to))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> adapter.loadStartedBetween(MEMBER, PET, from, null))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("#73-S12 마지막 활동이 정확히 기준 시각이면 진행 중으로 보고, 조금이라도 오래되면 보지 않는다")
	void existsOpenWalkActiveSinceIncludesBoundary() {
		saveWalk(PET, MEMBER, NOW);

		assertThat(adapter.existsOpenWalkActiveSince(PET, NOW)).isTrue();
		assertThat(adapter.existsOpenWalkActiveSince(PET, NOW.plusNanos(1000))).isFalse();
	}

	@Test
	@DisplayName("#73-S13 마지막 활동이 기준 시각보다 오래됐거나 끝낸 산책은 진행 중인 산책으로 보지 않는다")
	void existsOpenWalkActiveSinceIgnoresStaleAndEnded() {
		Long stale = saveWalk(PET, MEMBER, NOW.minusSeconds(3600));
		Long ended = saveWalk(PET + 1, MEMBER, NOW);
		Walk walk = adapter.loadById(ended).orElseThrow();
		walk.end(1L, NOW.plusSeconds(30));
		adapter.save(walk);
		flushAndClear();

		assertThat(stale).isNotNull();
		assertThat(adapter.existsOpenWalkActiveSince(PET, NOW.minusSeconds(300))).isFalse();
		assertThat(adapter.existsOpenWalkActiveSince(PET + 1, NOW.minusSeconds(300))).isFalse();
	}

	@Test
	@DisplayName("#167-S3 마지막 활동이 정확히 기준 시각이면 포함하고, 조금이라도 오래됐거나 끝낸 산책은 읽지 않는다")
	void loadOpenWalkActiveSinceBoundaryAndEnded() {
		Long walkId = saveWalk(PET, MEMBER, NOW);
		Long endedId = saveWalk(PET + 1, MEMBER, NOW);
		Walk ended = adapter.loadById(endedId).orElseThrow();
		ended.end(MEMBER, NOW.plusSeconds(30));
		adapter.save(ended);
		flushAndClear();

		assertThat(adapter.loadOpenWalkActiveSince(MEMBER, PET, NOW)).map(Walk::getId).contains(walkId);
		assertThat(adapter.loadOpenWalkActiveSince(MEMBER, PET, NOW.plusNanos(1000))).isEmpty();
		assertThat(adapter.loadOpenWalkActiveSince(MEMBER, PET + 1, NOW.minusSeconds(300))).isEmpty();
	}

	@Test
	@DisplayName("#167-S1 진행 중인 산책이 둘로 보이는 이상한 경우에도 가장 늦게 시작한 것 하나만 읽는다")
	void loadOpenWalkActiveSincePicksTheLatestStarted() {
		saveWalk(PET, MEMBER, NOW.minusSeconds(600));
		Long later = saveWalk(PET, MEMBER, NOW.minusSeconds(60));

		assertThat(adapter.loadOpenWalkActiveSince(MEMBER, PET, NOW.minusSeconds(900))).map(Walk::getId)
			.contains(later);
	}
}
