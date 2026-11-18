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

// 시나리오는 docs/map/walk-route.md에 있다. API를 만들기 전이라 저장소가 지키는 부분만 확인한다.
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
	@DisplayName("#73-S1 없는 산책을 읽으면 비어 있다")
	void loadMissing() {
		assertThat(adapter.loadById(-1L)).isEmpty();
		assertThat(adapter.loadByIdForUpdate(-1L)).isEmpty();
	}

	@Test
	@DisplayName("#73-S2 산책을 끝내고 다시 저장하면 같은 행이 바뀌고 새로 쌓이지 않는다")
	void saveAgainUpdatesState() {
		Long id = saveWalk(PET, MEMBER, NOW);
		Walk walk = adapter.loadById(id).orElseThrow();
		Instant endedAt = NOW.plusSeconds(120);
		walk.end(endedAt);

		adapter.save(walk);
		flushAndClear();

		Walk loaded = adapter.loadById(id).orElseThrow();
		assertThat(loaded.getEndedAt()).isEqualTo(endedAt);
		assertThat(loaded.status(endedAt)).isEqualTo(WalkStatus.ENDED);
		assertThat(jdbcTemplate.queryForObject("select count(*) from walks", Integer.class)).isEqualTo(1);
	}

	@Test
	@DisplayName("#73-S4 좌표를 받아 마지막 활동 시각이 바뀐 산책을 저장하면 그 값이 남는다")
	void saveRefreshedActivity() {
		Long id = saveWalk(PET, MEMBER, NOW);
		Walk walk = adapter.loadById(id).orElseThrow();
		Instant later = NOW.plusSeconds(200);
		walk.acceptPoints(later, 4);

		adapter.save(walk);
		flushAndClear();

		assertThat(adapter.loadById(id).orElseThrow().getLastActivityAt()).isEqualTo(later);
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
		first.end(firstEndedAt);
		adapter.save(first);
		flushAndClear();
		second.end(NOW.plusSeconds(90));

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
		walk.end(NOW.plusSeconds(60));
		adapter.save(walk);
		flushAndClear();

		Walk again = adapter.loadById(id).orElseThrow();
		adapter.save(again);
		flushAndClear();

		assertThat(adapter.loadById(id).orElseThrow().getEndedAt()).isEqualTo(NOW.plusSeconds(60));
	}

	@Test
	@DisplayName("#73-S6 같은 기록 시각의 좌표를 다시 쌓으면 새로 쌓이지 않고 먼저 쌓인 값이 그대로다")
	void insertIfAbsentSkipsDuplicates() {
		Long walkId = saveWalk(PET, MEMBER, NOW);
		WalkPoint first = WalkPoint.of(walkId, 37.5, 127.0, NOW);

		int firstInserted = adapter.insertIfAbsent(List.of(first));
		int resent = adapter.insertIfAbsent(List.of(WalkPoint.of(walkId, 10.0, 20.0, NOW)));
		flushAndClear();

		assertThat(firstInserted).isEqualTo(1);
		assertThat(resent).isZero();
		List<WalkPoint> points = adapter.loadByWalkId(walkId);
		assertThat(points).hasSize(1);
		assertThat(points.get(0).getLatitude()).isEqualTo(37.5);
		assertThat(adapter.countByWalkId(walkId)).isEqualTo(1);
	}

	@Test
	@DisplayName("#73-S6 일부만 겹치는 묶음을 다시 보내면 새 좌표만 쌓이고 쌓인 개수를 돌려준다")
	void insertIfAbsentCountsOnlyNewPoints() {
		Long walkId = saveWalk(PET, MEMBER, NOW);
		adapter.insertIfAbsent(List.of(WalkPoint.of(walkId, 37.5, 127.0, NOW)));

		int inserted = adapter.insertIfAbsent(List.of(WalkPoint.of(walkId, 37.5, 127.0, NOW),
				WalkPoint.of(walkId, 37.6, 127.1, NOW.plusSeconds(15)),
				WalkPoint.of(walkId, 37.7, 127.2, NOW.plusSeconds(30))));

		assertThat(inserted).isEqualTo(2);
		assertThat(adapter.countByWalkId(walkId)).isEqualTo(3);
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
	@DisplayName("#73-S4 좌표는 쌓은 순서가 아니라 기록 시각 순으로 읽힌다")
	void pointsAreOrderedByRecordedAt() {
		Long walkId = saveWalk(PET, MEMBER, NOW);

		adapter.insertIfAbsent(List.of(WalkPoint.of(walkId, 3.0, 3.0, NOW.plusSeconds(30)),
				WalkPoint.of(walkId, 1.0, 1.0, NOW), WalkPoint.of(walkId, 2.0, 2.0, NOW.plusSeconds(15))));
		flushAndClear();

		assertThat(adapter.loadByWalkId(walkId)).extracting(WalkPoint::getLatitude).containsExactly(1.0, 2.0, 3.0);
	}

	@Test
	@DisplayName("#73-S4 좌표가 없는 산책은 빈 목록과 0개다")
	void noPoints() {
		Long walkId = saveWalk(PET, MEMBER, NOW);

		assertThat(adapter.loadByWalkId(walkId)).isEmpty();
		assertThat(adapter.countByWalkId(walkId)).isZero();
	}

	@Test
	@DisplayName("#73-S7 날짜 구간에는 시작한 시각이 구간에 드는 산책만 시작 시각 순으로 나온다")
	void loadStartedBetweenByStartDate() {
		Instant dayStart = seoul(11, 17, 0, 0);
		Instant nextDayStart = seoul(11, 18, 0, 0);
		Long morning = saveWalk(PET, MEMBER, seoul(11, 17, 10, 0));
		Long evening = saveWalk(PET, MEMBER, seoul(11, 17, 23, 50));
		saveWalk(PET, MEMBER, seoul(11, 16, 23, 59));
		saveWalk(PET, MEMBER, seoul(11, 18, 9, 0));

		List<Walk> walks = adapter.loadStartedBetween(MEMBER, PET, dayStart, nextDayStart);

		assertThat(walks).extracting(Walk::getId).containsExactly(morning, evening);
	}

	@Test
	@DisplayName("#73-S7 자정을 넘겨 중단된 산책은 시작한 날에만 조회되고 다음 날에는 나오지 않는다")
	void walkCrossingMidnightBelongsToStartDay() {
		Long crossing = saveWalk(PET, MEMBER, seoul(11, 17, 23, 50));
		// 11월 18일 00:20에 마지막 좌표를 받고 끊긴 산책이다. 끝내지 않았다
		jdbcTemplate.update("update walks set last_activity_at = ? where id = ?",
				java.sql.Timestamp.from(seoul(11, 18, 0, 20)), crossing);
		flushAndClear();

		List<Walk> onStartDay = adapter.loadStartedBetween(MEMBER, PET, seoul(11, 17, 0, 0), seoul(11, 18, 0, 0));
		List<Walk> nextDay = adapter.loadStartedBetween(MEMBER, PET, seoul(11, 18, 0, 0), seoul(11, 19, 0, 0));

		assertThat(onStartDay).extracting(Walk::getId).containsExactly(crossing);
		assertThat(nextDay).isEmpty();
		Walk walk = onStartDay.get(0);
		assertThat(walk.getEndedAt()).isNull();
		assertThat(walk.getLastActivityAt()).isEqualTo(seoul(11, 18, 0, 20));
		assertThat(walk.status(seoul(11, 18, 9, 0))).isEqualTo(WalkStatus.INTERRUPTED);
	}

	@Test
	@DisplayName("#73-S7 구간의 시작 시각은 포함하고 끝 시각은 포함하지 않는다")
	void loadStartedBetweenBounds() {
		Instant dayStart = seoul(11, 17, 0, 0);
		Instant nextDayStart = seoul(11, 18, 0, 0);
		Long atStart = saveWalk(PET, MEMBER, dayStart);
		saveWalk(PET, MEMBER, nextDayStart);

		assertThat(adapter.loadStartedBetween(MEMBER, PET, dayStart, nextDayStart)).extracting(Walk::getId)
			.containsExactly(atStart);
	}

	@Test
	@DisplayName("#73-S8 시작한 산책이 없는 날은 빈 목록이다")
	void loadStartedBetweenEmpty() {
		saveWalk(PET, MEMBER, seoul(11, 18, 9, 0));

		assertThat(adapter.loadStartedBetween(MEMBER, PET, seoul(11, 17, 0, 0), seoul(11, 18, 0, 0))).isEmpty();
	}

	@Test
	@DisplayName("#73-S9 다른 회원의 산책이나 다른 반려동물의 산책은 조회되지 않는다")
	void loadStartedBetweenScopedToMemberAndPet() {
		Long mine = saveWalk(PET, MEMBER, seoul(11, 17, 10, 0));
		saveWalk(PET, MEMBER + 1, seoul(11, 17, 11, 0));
		saveWalk(PET + 1, MEMBER, seoul(11, 17, 12, 0));

		assertThat(adapter.loadStartedBetween(MEMBER, PET, seoul(11, 17, 0, 0), seoul(11, 18, 0, 0)))
			.extracting(Walk::getId)
			.containsExactly(mine);
		assertThat(adapter.loadStartedBetween(MEMBER + 2, PET, seoul(11, 17, 0, 0), seoul(11, 18, 0, 0))).isEmpty();
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
	@DisplayName("#73-S12 끝내지 않았고 기준 시각 이후에 활동한 산책이 있으면 진행 중인 산책이 있는 것이다")
	void existsOpenWalkActiveSince() {
		saveWalk(PET, MEMBER, NOW);

		assertThat(adapter.existsOpenWalkActiveSince(PET, NOW.minusSeconds(300))).isTrue();
		assertThat(adapter.existsOpenWalkActiveSince(PET + 1, NOW.minusSeconds(300))).isFalse();
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
		walk.end(NOW.plusSeconds(30));
		adapter.save(walk);
		flushAndClear();

		assertThat(stale).isNotNull();
		assertThat(adapter.existsOpenWalkActiveSince(PET, NOW.minusSeconds(300))).isFalse();
		assertThat(adapter.existsOpenWalkActiveSince(PET + 1, NOW.minusSeconds(300))).isFalse();
	}

	@Test
	@DisplayName("#73-S14 행을 잠그고 읽으면 같은 산책이 돌아온다")
	void loadByIdForUpdate() {
		Long id = saveWalk(PET, MEMBER, NOW);

		assertThat(adapter.loadByIdForUpdate(id)).map(Walk::getId).contains(id);
	}

}
