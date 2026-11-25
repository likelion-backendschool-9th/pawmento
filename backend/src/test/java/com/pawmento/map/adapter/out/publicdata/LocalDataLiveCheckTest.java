package com.pawmento.map.adapter.out.publicdata;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.concurrent.TimeUnit;

import com.pawmento.map.application.port.out.LocalDataSnapshot;
import com.pawmento.map.domain.LocalDataExclusion;
import com.pawmento.map.domain.LocalDataPlaceFactory;
import com.pawmento.map.domain.LocalDataRecord;
import com.pawmento.map.domain.PlaceCategory;
import com.pawmento.map.domain.PlaceDraft;

/**
 * 진짜 공공데이터포털(행정안전부 인허가 API)로 병원·미용을 끝까지 받아 가져올 수 있는 데이터인지 확인한다.
 * 환경 변수 PUBLIC_DATA_SERVICE_KEY가 있을 때만 돈다(없으면 건너뛰므로 CI와 평소 테스트에는 영향이 없다). 정부 서버가 느려서
 * 병원은 15분, 미용은 25분쯤 걸릴 수 있다. DB는 쓰지 않는다. 받기 → 판단 → 장소 만들기까지를 진짜 데이터로 확인하고 결과를
 * build/live-check-report.txt에 남긴다(건수와 분포만 적고 인증키·주소·전화는 적지 않는다).
 * 실행: set PUBLIC_DATA_SERVICE_KEY=키 → gradlew test --tests "*LocalDataLiveCheckTest.hospital"
 */
@EnabledIfEnvironmentVariable(named = "PUBLIC_DATA_SERVICE_KEY", matches = ".+")
class LocalDataLiveCheckTest {

	private static final Pattern PHONE = Pattern.compile("0[0-9]{1,2}-[0-9]{3,4}-[0-9]{4}");

	@Test
	@Timeout(value = 90, unit = TimeUnit.MINUTES)
	@DisplayName("진짜 서버: 병원을 끝까지 받아 온전한 스냅샷이고 장소로 만들 수 있다")
	void hospital() throws IOException {
		check(PlaceCategory.HOSPITAL);
	}

	@Test
	@Timeout(value = 120, unit = TimeUnit.MINUTES)
	@DisplayName("진짜 서버: 미용을 끝까지 받아 온전한 스냅샷이고 장소로 만들 수 있다")
	void grooming() throws IOException {
		check(PlaceCategory.GROOMING);
	}

	private static void check(PlaceCategory category) throws IOException {
		String baseUrl = System.getenv().getOrDefault("PUBLIC_DATA_BASE_URL", "https://apis.data.go.kr");
		var defaults = new PublicDataProperties(System.getenv("PUBLIC_DATA_SERVICE_KEY"), baseUrl, 100, Duration.ofSeconds(5),
				Duration.ofSeconds(20), 8, Duration.ofSeconds(1), Duration.ofSeconds(30), Duration.ofMillis(500));
		AtomicInteger sleeps = new AtomicInteger();
		long[] slept = { 0 };
		LocalDataApiClient client = new LocalDataApiClient(defaults, duration -> {
			sleeps.incrementAndGet();
			slept[0] += duration.toMillis();
			try {
				Thread.sleep(duration);
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException("확인이 중단됨");
			}
		});

		long started = System.currentTimeMillis();
		LocalDataSnapshot snapshot = client.load(category);
		long seconds = (System.currentTimeMillis() - started) / 1000;

		List<String> problems = new ArrayList<>();
		StringBuilder report = new StringBuilder();
		report.append("===== ").append(category).append(" 진짜 서버 확인 =====\n");

		// 1) 스냅샷이 믿을 수 있는가(가져오기가 반영하려면 모두 만족해야 한다)
		int total = snapshot.totalCountAtStart();
		Set<String> ids = new HashSet<>();
		int missingRequired = 0;
		for (LocalDataRecord record : snapshot.records()) {
			if (!record.isComplete()) {
				missingRequired++;
			}
			else {
				ids.add(record.sourceId());
			}
		}
		int pages = (total + 99) / 100;
		line(report, "totalCount(처음/끝)", total + " / " + snapshot.totalCountAtEnd());
		line(report, "받은 행 / 고유 관리번호", snapshot.records().size() + " / " + ids.size() + " (겹친 행 " + (snapshot.records().size() - missingRequired - ids.size()) + ")");
		line(report, "필수 항목이 빠진 행", String.valueOf(missingRequired));
		line(report, "걸린 시간", seconds + "초, 쉰 횟수 " + sleeps.get() + "번(페이지 사이 " + Math.max(0, pages - 1) + "번 제외한 다시 시도 약 "
				+ Math.max(0, sleeps.get() - Math.max(0, pages - 1)) + "번)");
		if (total != snapshot.totalCountAtEnd()) {
			problems.add("수집하는 동안 totalCount가 바뀜: " + total + " → " + snapshot.totalCountAtEnd());
		}
		if (missingRequired > 0) {
			problems.add("필수 항목이 빠진 행이 " + missingRequired + "개");
		}
		if (ids.size() != total) {
			problems.add("고유 관리번호 " + ids.size() + "개가 totalCount " + total + "와 다름. 가져오기가 항상 중단됨");
		}

		// 2) 장소로 만들 수 있는가
		Map<String, Integer> excluded = new TreeMap<>();
		int saved = 0;
		int withPhone = 0;
		int badPhone = 0;
		int open24h = 0;
		int masked = 0;
		int noAddress = 0;
		int noUpdatedAt = 0;
		Set<String> seen = new HashSet<>();
		for (LocalDataRecord record : snapshot.records()) {
			if (!record.isComplete() || !seen.add(record.sourceId())) {
				continue;
			}
			var outcome = LocalDataPlaceFactory.create(category, record);
			if (outcome.excluded()) {
				excluded.merge(outcome.exclusion().name(), 1, Integer::sum);
				continue;
			}
			PlaceDraft draft = outcome.draft();
			saved++;
			if (draft.phone() != null) {
				withPhone++;
				if (!PHONE.matcher(draft.phone()).matches()) {
					badPhone++;
				}
			}
			if (draft.open24h()) {
				open24h++;
			}
			if (draft.address() == null) {
				noAddress++;
			}
			else if (draft.address().contains("*")) {
				masked++;
			}
			if (draft.sourceUpdatedAt() == null) {
				noUpdatedAt++;
			}
			if (!(draft.latitude() >= 33 && draft.latitude() <= 39 && draft.longitude() >= 124 && draft.longitude() <= 132)) {
				problems.add("한국 범위 밖 좌표가 저장 대상에 섞임: " + record.sourceId());
			}
		}
		int read = seen.size();
		int excludedTotal = excluded.values().stream().mapToInt(Integer::intValue).sum();
		line(report, "읽은 행(고유)", String.valueOf(read));
		line(report, "저장 대상", saved + " (" + percent(saved, read) + ")");
		line(report, "제외", excludedTotal + " " + excluded + "  [CLOSED=영업/정상 아님, NO_COORDINATES=좌표 없음, OUT_OF_RANGE=범위 밖]");
		line(report, "전화 있음 / 형식 이상", withPhone + " (" + percent(withPhone, saved) + ") / " + badPhone);
		line(report, "24시(이름 기준)", String.valueOf(open24h));
		line(report, "주소 없음 / 번지가 가려짐(*)", noAddress + " / " + masked);
		line(report, "원본 갱신 시각을 읽지 못한 행", String.valueOf(noUpdatedAt));
		line(report, "(참고) LocalDataExclusion 종류", java.util.Arrays.toString(LocalDataExclusion.values()));
		if (badPhone > 0) {
			problems.add("형식이 이상한 전화번호가 " + badPhone + "개");
		}
		if (saved == 0) {
			problems.add("저장 대상이 하나도 없음");
		}

		report.append(problems.isEmpty() ? "\n결론: 통과 - 이 데이터는 그대로 가져올 수 있습니다.\n"
				: "\n결론: 확인 필요 - " + String.join("; ", problems) + "\n");
		Path file = Path.of("build", "live-check-report.txt");
		Files.createDirectories(file.getParent());
		Files.writeString(file, report.toString(), StandardCharsets.UTF_8,
				java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
		System.out.println(report);

		assertThat(problems).as("진짜 데이터 확인에서 문제가 발견됨(자세한 내용은 build/live-check-report.txt)").isEmpty();
	}

	private static void line(StringBuilder report, String label, String value) {
		report.append(String.format("%-34s %s%n", label, value));
	}

	private static String percent(int part, int whole) {
		return whole == 0 ? "-" : String.format("%.1f%%", 100.0 * part / whole);
	}

}
