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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.pawmento.map.application.exception.LocalDataSyncFailedException;
import com.pawmento.map.application.exception.LocalDataSyncFailedException.Reason;
import com.pawmento.map.application.port.out.LocalDataSnapshot;
import com.pawmento.map.domain.LocalDataRecord;
import com.pawmento.map.domain.PlaceCategory;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

// 시나리오는 docs/map/animal-places.md(#172-S5, S20)에 있다. 정부 서버(공공데이터포털)는 가짜 서버로 대신한다.
// 진짜 인증키로 하는 호출은 자동 테스트에 넣지 않는다
class LocalDataApiClientTest {

	private static final String KEY = "TEST-KEY+/=value";

	private HttpServer server;

	private ExecutorService executor;

	// 가짜 서버가 받은 요청의 경로와 쿼리
	private final List<String> requests = new CopyOnWriteArrayList<>();

	// 요청(경로+쿼리)을 보고 응답을 정하는 규칙. 기본은 빈 응답
	private volatile Function<HttpExchange, Reply> handler = exchange -> Reply.json(200, page(0, 1, 100, List.of()));

	// 다시 시도 전에 쉰 시간(실제로 쉬지 않고 기록만 한다)
	private final List<Duration> sleeps = new ArrayList<>();

	private record Reply(int status, String body) {

		static Reply json(int status, String body) {
			return new Reply(status, body);
		}
	}

	@BeforeEach
	void start() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", exchange -> {
			requests.add(exchange.getRequestURI().getRawPath() + "?" + exchange.getRequestURI().getRawQuery());
			Reply reply = handler.apply(exchange);
			byte[] body = reply.body().getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(reply.status(), body.length == 0 ? -1 : body.length);
			if (body.length > 0) {
				exchange.getResponseBody().write(body);
			}
			exchange.close();
		});
		// 한 요청이 오래 걸려도(시간 초과 시험) 다음 요청을 받을 수 있게 여러 스레드로 받는다
		executor = Executors.newFixedThreadPool(4);
		server.setExecutor(executor);
		server.start();
	}

	@AfterEach
	void stop() {
		server.stop(0);
		executor.shutdownNow();
	}

	private LocalDataApiClient client(String key, int maxAttempts, Duration readTimeout) {
		var properties = new LocalDataProperties(key, "http://127.0.0.1:" + server.getAddress().getPort(), 100,
				Duration.ofSeconds(2), readTimeout, maxAttempts, Duration.ofSeconds(1), Duration.ofSeconds(30),
				Duration.ofMillis(500));
		return new LocalDataApiClient(properties, sleeps::add);
	}

	private LocalDataApiClient client() {
		return client(KEY, 4, Duration.ofSeconds(5));
	}

	// 공공데이터포털 응답 모양: response.header, response.body.items.item
	private static String page(int total, int pageNo, int numOfRows, List<String> itemJsons) {
		String items = itemJsons.isEmpty() ? "\"\"" : "{\"item\":[" + String.join(",", itemJsons) + "]}";
		return "{\"response\":{\"header\":{\"resultCode\":\"0\",\"resultMsg\":\"정상\"},\"body\":{\"items\":" + items
				+ ",\"totalCount\":" + total + ",\"pageNo\":" + pageNo + ",\"numOfRows\":" + numOfRows + "}}}";
	}

	private static String item(String id) {
		return "{\"MNG_NO\":\"" + id + "\",\"SALS_STTS_NM\":\"영업/정상\",\"BPLC_NM\":\"병원 " + id
				+ "\",\"ROAD_NM_ADDR\":\"서울 종로구 1\",\"LOTNO_ADDR\":\"서울 종로구 동숭동 1\",\"TELNO\":\"0222733233\","
				+ "\"CRD_INFO_X\":\"199947.178659\",\"CRD_INFO_Y\":\"453593.826987\",\"DAT_UPDT_PNT\":\"2026-11-21 21:31:41\","
				+ "\"쓰지않는항목\":\"무시\"}";
	}

	private static List<String> items(int from, int count) {
		List<String> items = new ArrayList<>();
		for (int i = from; i < from + count; i++) {
			items.add(item("id-" + i));
		}
		return items;
	}

	private static int pageNo(HttpExchange exchange) {
		for (String pair : exchange.getRequestURI().getRawQuery().split("&")) {
			if (pair.startsWith("pageNo=")) {
				return Integer.parseInt(pair.substring("pageNo=".length()));
			}
		}
		throw new IllegalStateException("pageNo 없음");
	}

	@Test
	@DisplayName("#172-S20 totalCount가 250이면 pageNo를 1, 2, 3으로 올려 3번 호출하고, 받은 행을 순서대로 모은다")
	void readsAllPages() {
		handler = exchange -> {
			int no = pageNo(exchange);
			return Reply.json(200, page(250, no, 100, items((no - 1) * 100, no == 3 ? 50 : 100)));
		};

		LocalDataSnapshot snapshot = client().load(PlaceCategory.HOSPITAL);

		assertThat(requests).hasSize(3);
		assertThat(requests.get(0)).startsWith("/1741000/animal_hospitals/info?").contains("pageNo=1")
			.contains("numOfRows=100")
			.contains("returnType=json");
		assertThat(requests.get(2)).contains("pageNo=3");
		assertThat(snapshot.totalCountAtStart()).isEqualTo(250);
		assertThat(snapshot.totalCountAtEnd()).isEqualTo(250);
		assertThat(snapshot.records()).hasSize(250);
		assertThat(snapshot.records().get(0).sourceId()).isEqualTo("id-0");
		assertThat(snapshot.records().get(249).sourceId()).isEqualTo("id-249");
		// 페이지 사이에만 간격을 둔다(첫 호출 앞에는 두지 않는다)
		assertThat(sleeps).containsExactly(Duration.ofMillis(500), Duration.ofMillis(500));
	}

	@Test
	@DisplayName("#172-S20 미용은 pet_grooming 주소를 부른다")
	void groomingUsesItsOwnPath() {
		handler = exchange -> Reply.json(200, page(1, 1, 100, items(0, 1)));

		client().load(PlaceCategory.GROOMING);

		assertThat(requests.get(0)).startsWith("/1741000/pet_grooming/info?");
	}

	@Test
	@DisplayName("#172-S1 행의 항목을 그대로 옮기고, 응답에 없는 항목은 null, 값이 비어 있으면 빈 문자열이며, 쓰지 않는 항목은 무시한다")
	void mapsFieldsAndKeepsAbsenceDistinctFromEmpty() {
		String sparse = "{\"MNG_NO\":\"a\",\"SALS_STTS_NM\":\"폐업\",\"BPLC_NM\":\"이름\",\"CRD_INFO_X\":\"\",\"CRD_INFO_Y\":\"\"}";
		handler = exchange -> Reply.json(200, page(2, 1, 100, List.of(item("full"), sparse)));

		List<LocalDataRecord> records = client().load(PlaceCategory.HOSPITAL).records();

		assertThat(records.get(0)).isEqualTo(new LocalDataRecord("full", "영업/정상", "병원 full", "서울 종로구 1",
				"서울 종로구 동숭동 1", "0222733233", "199947.178659", "453593.826987", "2026-11-21 21:31:41"));
		LocalDataRecord second = records.get(1);
		assertThat(second.roadAddress()).isNull();
		assertThat(second.phone()).isNull();
		assertThat(second.updatedAt()).isNull();
		assertThat(second.x()).isEmpty();
		assertThat(second.isComplete()).isTrue();
	}

	@Test
	@DisplayName("#172-S5 필수 항목이 응답에 없는 행은 null로 읽어 isComplete가 false다")
	void missingRequiredFieldIsNull() {
		handler = exchange -> Reply.json(200, page(1, 1, 100, List.of("{\"MNG_NO\":\"a\",\"SALS_STTS_NM\":\"영업/정상\"}")));

		LocalDataRecord record = client().load(PlaceCategory.HOSPITAL).records().get(0);

		assertThat(record.name()).isNull();
		assertThat(record.isComplete()).isFalse();
	}

	@Test
	@DisplayName("#172-S1 items가 빈 문자열이면 빈 목록이고, 행이 하나뿐이라 배열이 아니라 객체로 와도 읽는다")
	void emptyAndSingleItems() {
		handler = exchange -> Reply.json(200, page(0, 1, 100, List.of()));
		assertThat(client().load(PlaceCategory.HOSPITAL).records()).isEmpty();

		requests.clear();
		handler = exchange -> Reply.json(200,
				"{\"response\":{\"header\":{\"resultCode\":\"0\"},\"body\":{\"items\":{\"item\":" + item("only")
						+ "},\"totalCount\":1,\"pageNo\":1,\"numOfRows\":100}}}");
		assertThat(client().load(PlaceCategory.HOSPITAL).records()).extracting(LocalDataRecord::sourceId)
			.containsExactly("only");
	}

	@Test
	@DisplayName("#172-S5 수집하는 동안 totalCount가 바뀌면 처음과 끝의 값이 다르게 돌아온다")
	void reportsTotalCountChange() {
		handler = exchange -> {
			int no = pageNo(exchange);
			return Reply.json(200, page(no == 1 ? 150 : 151, no, 100, items((no - 1) * 100, no == 2 ? 50 : 100)));
		};

		LocalDataSnapshot snapshot = client().load(PlaceCategory.HOSPITAL);

		assertThat(snapshot.totalCountAtStart()).isEqualTo(150);
		assertThat(snapshot.totalCountAtEnd()).isEqualTo(151);
	}

	@Test
	@DisplayName("#172-S5 오류(504)는 간격을 늘려 가며 다시 시도하고, 성공하면 그 페이지를 이어서 받는다")
	void retriesTransientErrorsWithBackoff() {
		int[] failuresLeft = { 2 };
		handler = exchange -> {
			int no = pageNo(exchange);
			if (no == 2 && failuresLeft[0]-- > 0) {
				return Reply.json(504, "");
			}
			return Reply.json(200, page(150, no, 100, items((no - 1) * 100, no == 2 ? 50 : 100)));
		};

		LocalDataSnapshot snapshot = client().load(PlaceCategory.HOSPITAL);

		assertThat(snapshot.records()).hasSize(150);
		// 페이지 사이 간격 500ms, 2쪽의 실패 두 번에 1초와 2초(두 배씩 늘림)
		assertThat(sleeps).containsExactly(Duration.ofMillis(500), Duration.ofSeconds(1), Duration.ofSeconds(2));
	}

	@Test
	@DisplayName("#172-S5 다시 시도 간격은 최대값(30초)을 넘지 않는다")
	void backoffIsCapped() {
		handler = exchange -> Reply.json(504, "");

		assertThatThrownBy(() -> client(KEY, 8, Duration.ofSeconds(5)).load(PlaceCategory.HOSPITAL))
			.isInstanceOf(LocalDataSyncFailedException.class);

		assertThat(sleeps).containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(4),
				Duration.ofSeconds(8), Duration.ofSeconds(16), Duration.ofSeconds(30), Duration.ofSeconds(30));
	}

	@Test
	@DisplayName("#172-S5 정한 횟수만큼 다시 시도해도 실패하면 FETCH_FAILED로 중단하고, 메시지와 원인에 인증키와 주소가 없다")
	void failsAfterMaxAttemptsWithoutLeakingKey() {
		handler = exchange -> Reply.json(504, "");

		assertThatThrownBy(() -> client().load(PlaceCategory.HOSPITAL)).isInstanceOfSatisfying(
				LocalDataSyncFailedException.class, e -> {
					assertThat(e.reason()).isEqualTo(Reason.FETCH_FAILED);
					assertThat(requests).hasSize(4);
					assertThat(everyMessage(e)).doesNotContain("TEST-KEY").doesNotContain("127.0.0.1").doesNotContain("serviceKey");
				});
	}

	@Test
	@DisplayName("#172-S5 시간 초과도 다시 시도하고, 다음에 응답하면 성공한다")
	void retriesTimeouts() {
		int[] calls = { 0 };
		handler = exchange -> {
			if (calls[0]++ == 0) {
				try {
					Thread.sleep(600);
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
			return Reply.json(200, page(1, 1, 100, items(0, 1)));
		};

		LocalDataSnapshot snapshot = client(KEY, 3, Duration.ofMillis(200)).load(PlaceCategory.HOSPITAL);

		assertThat(snapshot.records()).hasSize(1);
		assertThat(requests).hasSize(2);
	}

	private static String portalError(String reasonCode) {
		return "<OpenAPI_ServiceResponse><cmmMsgHeader><errMsg>SERVICE ERROR</errMsg><returnAuthMsg>ERROR</returnAuthMsg>"
				+ (reasonCode == null ? "" : "<returnReasonCode>" + reasonCode + "</returnReasonCode>")
				+ "</cmmMsgHeader></OpenAPI_ServiceResponse>";
	}

	private static String resultCode(String code) {
		return "{\"response\":{\"header\":{\"resultCode\":\"" + code + "\",\"resultMsg\":\"오류\"}}}";
	}

	@Test
	@DisplayName("#172-S5 인증키 문제(401·403)와 포털의 영구 오류 코드(요청 잘못됨 10~12, 접근 거부 20·21, 한도 초과 22, 키·기간·IP 30~33)는 다시 시도해도 같아서 바로 중단한다")
	void doesNotRetryPermanentFailures() {
		var replies = new ArrayList<Reply>(List.of(Reply.json(401, ""), Reply.json(403, "forbidden")));
		for (String code : List.of("10", "11", "12", "20", "21", "22", "30", "31", "32", "33")) {
			replies.add(Reply.json(200, portalError(code)));
			replies.add(Reply.json(200, resultCode(code)));
		}
		for (Reply reply : replies) {
			requests.clear();
			sleeps.clear();
			handler = exchange -> reply;

			assertThatThrownBy(() -> client().load(PlaceCategory.HOSPITAL))
				.isInstanceOfSatisfying(LocalDataSyncFailedException.class,
						e -> assertThat(e.reason()).isEqualTo(Reason.FETCH_FAILED));

			assertThat(requests).as("다시 시도하지 않는다: %s", reply).hasSize(1);
			assertThat(sleeps).isEmpty();
		}
	}

	@Test
	@DisplayName("#172-S5 포털의 일시 오류 코드(01 애플리케이션 오류, 02 DB 오류, 04 HTTP 오류, 05 서비스 시간 초과, 99)와 모르는 코드, 코드가 없는 XML은 다시 시도하고 다음에 정상 응답이 오면 이어서 받는다")
	void retriesTransientPortalErrors() {
		var replies = new ArrayList<Reply>();
		for (String code : List.of("01", "02", "04", "05", "99", "77")) {
			replies.add(Reply.json(200, portalError(code)));
			replies.add(Reply.json(200, resultCode(code)));
		}
		replies.add(Reply.json(200, portalError(null)));
		replies.add(Reply.json(200, "<html><body>잠시 후 다시 시도해 주세요</body></html>"));
		for (Reply error : replies) {
			requests.clear();
			sleeps.clear();
			var calls = new AtomicInteger();
			handler = exchange -> calls.getAndIncrement() == 0 ? error : Reply.json(200, page(1, 1, 100, items(0, 1)));

			var snapshot = client().load(PlaceCategory.HOSPITAL);

			assertThat(snapshot.records()).as("다시 시도해서 받는다: %s", error).hasSize(1);
			assertThat(requests).hasSize(2);
			assertThat(sleeps).containsExactly(Duration.ofSeconds(1));
		}
	}

	@Test
	@DisplayName("#172-S5 읽을 수 없는 본문(JSON이 아님)은 다시 시도한 뒤 FETCH_FAILED다")
	void brokenBodyIsRetried() {
		handler = exchange -> Reply.json(200, "{깨진 json");

		assertThatThrownBy(() -> client().load(PlaceCategory.HOSPITAL)).isInstanceOfSatisfying(
				LocalDataSyncFailedException.class, e -> assertThat(e.reason()).isEqualTo(Reason.FETCH_FAILED));
		assertThat(requests).hasSize(4);
	}

	@Test
	@DisplayName("#172-S5 인증키가 없으면 호출하지 않고 FETCH_FAILED로 중단한다")
	void missingKeyStopsBeforeCalling() {
		assertThatThrownBy(() -> client("", 4, Duration.ofSeconds(5)).load(PlaceCategory.HOSPITAL))
			.isInstanceOfSatisfying(LocalDataSyncFailedException.class,
					e -> assertThat(e.reason()).isEqualTo(Reason.FETCH_FAILED));
		assertThat(requests).isEmpty();
	}

	@Test
	@DisplayName("#172-S5 포털의 Decoding 키(+ / = 포함)는 인코딩해서 보내고, 이미 인코딩된 Encoding 키(%가 있음)는 그대로 보낸다")
	void sendsBothKeyFormatsCorrectly() {
		handler = exchange -> Reply.json(200, page(1, 1, 100, items(0, 1)));

		client("ab+c/d=e", 4, Duration.ofSeconds(5)).load(PlaceCategory.HOSPITAL);
		client("ab%2Bc%2Fd%3De", 4, Duration.ofSeconds(5)).load(PlaceCategory.HOSPITAL);

		assertThat(requests.get(0)).contains("serviceKey=ab%2Bc%2Fd%3De&");
		assertThat(requests.get(1)).contains("serviceKey=ab%2Bc%2Fd%3De&");
	}

	@Test
	@DisplayName("#172-S20 보호센터는 이 클라이언트로 받지 않는다")
	void shelterIsNotSupported() {
		assertThatThrownBy(() -> client().load(PlaceCategory.SHELTER)).isInstanceOf(IllegalArgumentException.class);
		assertThat(requests).isEmpty();
	}

	@Test
	@DisplayName("#172-S1 응답 헤더에 문자 인코딩이 없거나 charset=UTF-8이어도 한글이 깨지지 않는다")
	void decodesUtf8RegardlessOfContentTypeCharset() {
		for (String contentType : new String[] { null, "application/json", "application/json;charset=UTF-8" }) {
			requests.clear();
			server.removeContext("/");
			server.createContext("/", exchange -> {
				requests.add(exchange.getRequestURI().getRawPath());
				byte[] body = page(1, 1, 100, items(0, 1)).getBytes(StandardCharsets.UTF_8);
				if (contentType != null) {
					exchange.getResponseHeaders().add("Content-Type", contentType);
				}
				exchange.sendResponseHeaders(200, body.length);
				exchange.getResponseBody().write(body);
				exchange.close();
			});

			LocalDataRecord record = client().load(PlaceCategory.HOSPITAL).records().get(0);

			assertThat(record.name()).as("Content-Type=%s", contentType).isEqualTo("병원 id-0");
			assertThat(record.businessStatus()).isEqualTo("영업/정상");
		}
	}

	private static String everyMessage(Throwable error) {
		StringBuilder all = new StringBuilder();
		for (Throwable e = error; e != null; e = e.getCause()) {
			all.append(e).append('\n');
		}
		return all.toString();
	}

}
