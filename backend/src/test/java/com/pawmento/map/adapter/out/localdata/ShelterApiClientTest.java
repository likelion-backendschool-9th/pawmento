package com.pawmento.map.adapter.out.localdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.pawmento.map.application.exception.LocalDataSyncFailedException;
import com.pawmento.map.application.port.out.ShelterSnapshot;
import com.pawmento.map.domain.ShelterRecord;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

// 시나리오는 docs/map/animal-places.md(#172-S8, S9)에 있다. 정부 서버(공공데이터포털)는 가짜 서버로 대신한다.
// 진짜 인증키로 하는 호출은 자동 테스트에 넣지 않는다
class ShelterApiClientTest {

	private static final String KEY = "TEST-KEY+/=value";

	private HttpServer server;

	private final List<String> requests = new CopyOnWriteArrayList<>();

	private volatile Function<HttpExchange, String> handler = exchange -> response(0, List.of());

	private final List<Duration> sleeps = new ArrayList<>();

	@BeforeEach
	void start() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", exchange -> {
			requests.add(exchange.getRequestURI().getRawPath() + "?" + exchange.getRequestURI().getRawQuery());
			byte[] body = handler.apply(exchange).getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(200, body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
		});
		server.start();
	}

	@AfterEach
	void stop() {
		server.stop(0);
	}

	private ShelterApiClient client(String key) {
		var properties = new LocalDataProperties(key, "http://127.0.0.1:" + server.getAddress().getPort(), 100,
				Duration.ofSeconds(2), Duration.ofSeconds(5), 3, Duration.ofSeconds(1), Duration.ofSeconds(30),
				Duration.ofMillis(500));
		return new ShelterApiClient(properties, sleeps::add);
	}

	private static String response(int total, List<String> items) {
		String body = items.isEmpty() ? "\"\"" : "{\"item\":[" + String.join(",", items) + "]}";
		return "{\"response\":{\"header\":{\"resultCode\":\"00\",\"resultMsg\":\"NORMAL SERVICE.\"},\"body\":{\"items\":" + body
				+ ",\"totalCount\":" + total + ",\"pageNo\":1,\"numOfRows\":500}}}";
	}

	// 실제 응답처럼 lat, lng이 숫자이고, 센터마다 있는 항목이 다르다
	private static final String FULL = "{\"careNm\":\"(사)대구수의사회\",\"careRegNo\":\"327346201500001\",\"orgNm\":\"대구광역시 수성구\","
			+ "\"careAddr\":\"대구광역시 북구 호국로 229 (서변동) 6층\",\"jibunAddr\":\" 6층\",\"lat\":35.922276,\"lng\":128.59904,"
			+ "\"weekOprStime\":\"00:00\",\"weekOprEtime\":\"24:00\",\"closeDay\":\"0\",\"careTel\":\"053-764-3708\","
			+ "\"dataStdDt\":\"2025-01-03\",\"쓰지않는항목\":\"무시\"}";

	private static final String SPARSE = "{\"careNm\":\"운영 시간 없는 센터\",\"careRegNo\":\"2\"}";

	@Test
	@DisplayName("#172-S9 한 번의 호출로 numOfRows=500을 요청하며(응답 형식은 이 API의 `_type=json`으로 고른다. `returnType`을 보내면 XML이 온다), 실제 응답처럼 숫자 위도·경도와 한글을 그대로 옮기며 쓰지 않는 항목은 무시한다")
	void readsOnePageOf500() {
		handler = exchange -> response(868, List.of(FULL));

		ShelterSnapshot snapshot = client(KEY).load();

		assertThat(requests).hasSize(1);
		assertThat(requests.get(0)).startsWith("/1543061/animalShelterSrvc_v2/shelterInfo_v2?")
			.contains("pageNo=1")
			.contains("numOfRows=500")
			.contains("&_type=json")
			.doesNotContain("returnType");
		assertThat(snapshot.requestedRows()).isEqualTo(500);
		assertThat(snapshot.records()).containsExactly(new ShelterRecord("327346201500001", "(사)대구수의사회",
				"대구광역시 북구 호국로 229 (서변동) 6층", "053-764-3708", "35.922276", "128.59904", "00:00", "24:00", "0",
				"2025-01-03"));
		assertThat(sleeps).isEmpty();
	}

	@Test
	@DisplayName("#172-S8 센터마다 없는 항목은 null로 읽는다(응답에 항목이 없음)")
	void absentFieldsAreNull() {
		handler = exchange -> response(2, List.of(FULL, SPARSE));

		ShelterRecord sparse = client(KEY).load().records().get(1);

		assertThat(sparse.name()).isEqualTo("운영 시간 없는 센터");
		assertThat(sparse.latitude()).isNull();
		assertThat(sparse.openTime()).isNull();
		assertThat(sparse.closeDay()).isNull();
		// 등록번호와 이름이 있으면 좌표 항목이 없어도 가져온다(장소로 만들 때 좌표 없음으로 제외한다)
		assertThat(sparse.isComplete()).isTrue();
	}

	@Test
	@DisplayName("#172-S9 totalCount가 실제 행 수와 달라도(868, 실제 1) 받은 행을 그대로 돌려준다")
	void ignoresTotalCountMismatch() {
		handler = exchange -> response(868, List.of(FULL));

		assertThat(client(KEY).load().records()).hasSize(1);
	}

	@Test
	@DisplayName("#172-S9 items가 빈 문자열이면 빈 목록이다(실패로 볼지는 서비스가 정한다)")
	void emptyItems() {
		handler = exchange -> response(0, List.of());

		assertThat(client(KEY).load().records()).isEmpty();
	}

	@Test
	@DisplayName("#172-S9 인증키가 없으면 호출하지 않고 중단한다")
	void noKeyDoesNotCall() {
		assertThatThrownBy(() -> client("").load()).isInstanceOf(LocalDataSyncFailedException.class);

		assertThat(requests).isEmpty();
	}

	@Test
	@DisplayName("#172-S9 포털의 영구 오류(키 미등록 30 등)는 다시 시도해도 같아서 바로 중단하고, 예외에 인증키가 없다")
	void permanentPortalErrorStopsImmediately() {
		handler = exchange -> "<OpenAPI_ServiceResponse><cmmMsgHeader><returnReasonCode>30</returnReasonCode></cmmMsgHeader></OpenAPI_ServiceResponse>";

		assertThatThrownBy(() -> client(KEY).load()).isInstanceOf(LocalDataSyncFailedException.class)
			.satisfies(e -> assertThat(String.valueOf(e.getMessage()) + e.getCause()).doesNotContain("TEST-KEY"));

		assertThat(requests).hasSize(1);
		assertThat(sleeps).isEmpty();
	}

	@Test
	@DisplayName("#172-S9 포털의 일시 오류(서비스 시간 초과 05)와 읽을 수 없는 본문은 정한 횟수만큼 쉬는 시간을 두 배씩 늘려 다시 시도하고, 끝내 안 되면 FETCH_FAILED로 중단한다")
	void retriesTransientErrorsThenFails() {
		handler = exchange -> "<OpenAPI_ServiceResponse><cmmMsgHeader><returnReasonCode>05</returnReasonCode></cmmMsgHeader></OpenAPI_ServiceResponse>";
		assertThatThrownBy(() -> client(KEY).load()).isInstanceOf(LocalDataSyncFailedException.class);
		assertThat(requests).hasSize(3);

		requests.clear();
		sleeps.clear();
		handler = exchange -> "not json";
		assertThatThrownBy(() -> client(KEY).load()).isInstanceOf(LocalDataSyncFailedException.class)
			.satisfies(e -> assertThat(((LocalDataSyncFailedException) e).reason())
				.isEqualTo(LocalDataSyncFailedException.Reason.FETCH_FAILED));
		assertThat(requests).hasSize(3);
		assertThat(sleeps).containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(2));
	}

}
