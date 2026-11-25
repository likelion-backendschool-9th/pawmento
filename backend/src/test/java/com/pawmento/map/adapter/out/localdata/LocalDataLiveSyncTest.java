package com.pawmento.map.adapter.out.localdata;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.pawmento.TestcontainersConfiguration;
import com.pawmento.map.application.port.in.ListPlacesQuery;
import com.pawmento.map.application.port.in.ListPlacesResult;
import com.pawmento.map.application.port.in.ListPlacesUseCase;
import com.pawmento.map.application.port.in.SyncLocalDataPlacesCommand;
import com.pawmento.map.application.port.in.SyncLocalDataPlacesResult;
import com.pawmento.map.application.port.in.SyncLocalDataPlacesUseCase;
import com.pawmento.map.application.port.out.CheckPlaceSyncAdminPort;

/**
 * 진짜 공공데이터포털로 병원·미용을 받아 진짜 PostgreSQL(Testcontainers)에 넣고, 다시 넣어도 변하지 않는지와 영역 조회가 되는지
 * 확인한다. 환경 변수 PUBLIC_DATA_SERVICE_KEY가 있을 때만 돈다(없으면 건너뛰므로 CI에는 영향이 없다). Docker가 켜져 있어야 한다.
 * 결과는 build/live-sync-report.txt에 건수와 시간만 적는다(인증키·주소·전화는 적지 않는다).
 * 실행: set PUBLIC_DATA_SERVICE_KEY=키 → gradlew cleanTest test --tests "*LocalDataLiveSyncTest" -i
 * 이 테스트는 시작과 끝에 places 테이블을 비운다. 개발 DB가 아니라 테스트용 컨테이너 DB라서 안전하다.
 * 앱 전체가 아니라 지도 가져오기에 필요한 빈(받아오기·동기화·영역 조회·저장소)만 띄운다. 미디어 저장소(VolumeMediaUploadAdapter)는
 * 리눅스 전용이라 Windows에서는 앱 전체를 띄우는 테스트가 돌지 못하기 때문이다
 */
@EnabledIfEnvironmentVariable(named = "PUBLIC_DATA_SERVICE_KEY", matches = ".+")
@Import(TestcontainersConfiguration.class)
@SpringBootTest(classes = LocalDataLiveSyncTest.LiveConfig.class,
		properties = { "pawmento.map.local-data.base-url=${PUBLIC_DATA_BASE_URL:https://apis.data.go.kr}" })
class LocalDataLiveSyncTest {

	// 테스트 클래스 안의 설정이라 다른 테스트의 컴포넌트 스캔에는 잡히지 않는다(@TestConfiguration으로 두면 스프링 부트가 앱 전체
	// 설정을 같이 넣어 버려서 일반 @Configuration으로 둔다). JPA 엔티티·저장소는 persistence 패키지에서 찾고, 빈은 필요한 것만 이름으로 골라 담는다
	@Configuration(proxyBeanMethods = false)
	@EnableAutoConfiguration
	@AutoConfigurationPackage(basePackages = "com.pawmento.map.adapter.out.persistence")
	@ComponentScan(basePackages = { "com.pawmento.map.application.service", "com.pawmento.map.adapter.out.persistence",
			"com.pawmento.map.adapter.out.localdata" }, useDefaultFilters = false,
			includeFilters = @ComponentScan.Filter(type = FilterType.REGEX,
					pattern = "com\\.pawmento\\.map\\..*\\.(SyncLocalDataPlacesService|ListPlacesService|PlacePersistenceAdapter|LocalDataApiClient|ShelterApiClient|PlaceSyncLockAdapter|LocalDataConfig)"))
	static class LiveConfig {

		// 이 테스트는 관리자가 아니라 시스템 요청(자동 실행과 같음)으로 가져오므로 관리자 확인은 쓰지 않는다
		@Bean
		CheckPlaceSyncAdminPort placeSyncAdminPort() {
			return memberId -> {
			};
		}

	}

	@Autowired
	private SyncLocalDataPlacesUseCase sync;

	@Autowired
	private ListPlacesUseCase list;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	@Timeout(value = 180, unit = TimeUnit.MINUTES)
	@DisplayName("#172-S8 #172-S9 진짜 서버: 병원·미용·보호센터를 DB에 가져오고, 다시 가져와도 그대로이며, 영역 조회가 된다")
	void importAndQuery() throws IOException {
		StringBuilder report = new StringBuilder("===== 진짜 서버 → 진짜 DB 가져오기 확인 =====\n");
		List<String> problems = new ArrayList<>();
		jdbc.update("delete from places");
		try {
			for (String category : categories()) {
				importCategory(category, report, problems);
			}
			queries(report, problems);
			databaseChecks(report, problems);
		}
		finally {
			jdbc.update("delete from places");
		}
		report.append(problems.isEmpty() ? "\n결론: 통과 - 진짜 서버의 데이터를 DB에 가져와 조회까지 정상입니다.\n"
				: "\n결론: 확인 필요 - " + String.join("; ", problems) + "\n");
		Path file = Path.of("build", "live-sync-report.txt");
		Files.createDirectories(file.getParent());
		Files.writeString(file, report.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE,
				StandardOpenOption.APPEND);
		System.out.println(report);
		assertThat(problems).as("진짜 가져오기 확인에서 문제가 발견됨(build/live-sync-report.txt)").isEmpty();
	}

	// LIVE_SYNC_CATEGORIES=SHELTER처럼 쉼표로 적으면 그 종류만 가져온다(보호센터만 빠르게 확인할 때). 없으면 세 종류 모두 가져온다
	private static List<String> categories() {
		String chosen = System.getenv("LIVE_SYNC_CATEGORIES");
		if (chosen == null || chosen.isBlank()) {
			return List.of("HOSPITAL", "GROOMING", "SHELTER");
		}
		return java.util.Arrays.stream(chosen.split(",")).map(String::strip).filter(name -> !name.isEmpty()).toList();
	}

	private void importCategory(String category, StringBuilder report, List<String> problems) {
		long t0 = System.currentTimeMillis();
		SyncLocalDataPlacesResult first = sync.sync(new SyncLocalDataPlacesCommand(category));
		long firstSeconds = (System.currentTimeMillis() - t0) / 1000;
		long t1 = System.currentTimeMillis();
		SyncLocalDataPlacesResult again = sync.sync(new SyncLocalDataPlacesCommand(category));
		long againSeconds = (System.currentTimeMillis() - t1) / 1000;
		int stored = jdbc.queryForObject("select count(*) from places where category = ?", Integer.class, category);

		report.append("\n--- ").append(category).append(" ---\n");
		line(report, "첫 가져오기", first.read() + "행 읽음, 저장 " + first.saved() + ", 제외 " + first.excluded() + " (폐업 "
				+ first.closed() + ", 좌표 없음 " + first.noCoordinates() + ", 범위 밖 " + first.outOfRange() + ")");
		line(report, "  추가/갱신/삭제", first.inserted() + " / " + first.updated() + " / " + first.deleted() + "  (" + firstSeconds + "초)");
		line(report, "바로 이어 다시 가져오기", "추가/갱신/삭제 " + again.inserted() + " / " + again.updated() + " / " + again.deleted() + "  ("
				+ againSeconds + "초)");
		line(report, "DB에 저장된 " + category, stored + "행");

		if (first.saved() == 0) {
			problems.add(category + ": 저장된 장소가 없음");
		}
		if (first.inserted() != first.saved() || first.updated() != 0 || first.deleted() != 0) {
			problems.add(category + ": 빈 DB에 처음 넣었는데 추가 수가 저장 수와 다르거나 갱신·삭제가 있음");
		}
		if (stored != first.saved()) {
			problems.add(category + ": DB 행 수 " + stored + "가 저장 수 " + first.saved() + "와 다름");
		}
		// 원본이 몇 분 사이에 바뀔 수는 있어서 아주 작은 변화까지는 허용한다. 크게 바뀌면 같은 데이터가 매번 다르게 들어가는 문제다
		if (again.inserted() + again.deleted() > 5 || again.updated() > Math.max(5, first.saved() / 100)) {
			problems.add(category + ": 같은 데이터를 다시 가져왔는데 많이 바뀜(추가 " + again.inserted() + ", 갱신 " + again.updated()
					+ ", 삭제 " + again.deleted() + ")");
		}
	}

	private void queries(StringBuilder report, List<String> problems) {
		report.append("\n--- 영역 조회(서비스 계층) ---\n");
		// 전국, 서울 전체, 서울 도심(좁은 영역)
		double[][] areas = { { 33, 124, 39, 132 }, { 37.42, 126.76, 37.70, 127.18 }, { 37.55, 126.97, 37.59, 127.03 } };
		String[] names = { "전국", "서울", "서울 도심" };
		for (int i = 0; i < areas.length; i++) {
			for (Set<String> categories : List.of(Set.of("HOSPITAL"), Set.of("GROOMING"), Set.of("SHELTER"),
					Set.of("HOSPITAL", "GROOMING", "SHELTER"))) {
				long t = System.nanoTime();
				ListPlacesResult result = list.list(new ListPlacesQuery(BigDecimal.valueOf(areas[i][0]),
						BigDecimal.valueOf(areas[i][1]), BigDecimal.valueOf(areas[i][2]), BigDecimal.valueOf(areas[i][3]),
						categories));
				long millis = (System.nanoTime() - t) / 1_000_000;
				line(report, names[i] + " " + categories, result.places().size() + "곳" + (result.truncated() ? " (500곳에서 잘림)" : "") + ", "
						+ millis + "ms");
				if (millis > 5000) {
					problems.add(names[i] + " " + categories + " 조회가 " + millis + "ms로 너무 느림(5초 초과)");
				}
				if (result.places().size() > 500) {
					problems.add(names[i] + " 조회 결과가 500곳을 넘음");
				}
			}
		}
		// 병원과 미용을 함께 조회하면 같은 이름 30m 안의 미용이 빠진다
		ListPlacesQuery seoul = new ListPlacesQuery(BigDecimal.valueOf(37.55), BigDecimal.valueOf(126.97),
				BigDecimal.valueOf(37.59), BigDecimal.valueOf(127.03), Set.of("HOSPITAL", "GROOMING"));
		int both = list.list(seoul).places().size();
		int hospitals = list.list(new ListPlacesQuery(seoul.minLatitude(), seoul.minLongitude(), seoul.maxLatitude(),
				seoul.maxLongitude(), Set.of("HOSPITAL"))).places().size();
		int groomings = list.list(new ListPlacesQuery(seoul.minLatitude(), seoul.minLongitude(), seoul.maxLatitude(),
				seoul.maxLongitude(), Set.of("GROOMING"))).places().size();
		line(report, "서울 도심 겹침 제거", "병원 " + hospitals + " + 미용 " + groomings + " → 함께 조회 " + both + " (겹쳐서 빠진 미용 "
				+ (hospitals + groomings - both) + ", 500곳으로 잘리지 않았을 때만 의미 있음)");
	}

	private void databaseChecks(StringBuilder report, List<String> problems) {
		report.append("\n--- DB 점검 ---\n");
		int outside = jdbc.queryForObject(
				"select count(*) from places where latitude not between 33 and 39 or longitude not between 124 and 132",
				Integer.class);
		int badPhone = jdbc.queryForObject(
				"select count(*) from places where phone is not null and phone !~ '^0[0-9]{1,2}-[0-9]{3,4}-[0-9]{4}$'",
				Integer.class);
		int blankName = jdbc.queryForObject("select count(*) from places where btrim(name) = ''", Integer.class);
		int nullUpdated = jdbc.queryForObject("select count(*) from places where source_updated_at is null", Integer.class);
		line(report, "한국 범위 밖 좌표", String.valueOf(outside));
		line(report, "형식이 이상한 전화", String.valueOf(badPhone));
		line(report, "이름이 빈 행", String.valueOf(blankName));
		line(report, "원본 갱신 시각이 없는 행", String.valueOf(nullUpdated));
		int shelters = jdbc.queryForObject("select count(*) from places where category = 'SHELTER'", Integer.class);
		int shelterHours = jdbc.queryForObject(
				"select count(*) from places where category = 'SHELTER' and opening_hours is not null", Integer.class);
		int shelter24h = jdbc.queryForObject(
				"select count(*) from places where category = 'SHELTER' and open24h and open24h_basis = 'HOURS'",
				Integer.class);
		int shelterClosedDays = jdbc.queryForObject(
				"select count(*) from places where category = 'SHELTER' and closed_days is not null", Integer.class);
		line(report, "보호센터 저장 / 운영 시간 있음 / 24시간 / 휴무일 있음",
				shelters + " / " + shelterHours + " / " + shelter24h + " / " + shelterClosedDays);
		if (outside > 0) {
			problems.add("한국 범위 밖 좌표가 DB에 " + outside + "개");
		}
		if (badPhone > 0) {
			problems.add("형식이 이상한 전화가 DB에 " + badPhone + "개");
		}
		if (blankName > 0) {
			problems.add("이름이 빈 행이 DB에 " + blankName + "개");
		}
	}

	private static void line(StringBuilder report, String label, String value) {
		report.append(String.format("%-30s %s%n", label, value));
	}

}
