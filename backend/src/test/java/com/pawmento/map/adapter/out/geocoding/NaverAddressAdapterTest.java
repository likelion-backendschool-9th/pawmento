package com.pawmento.map.adapter.out.geocoding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.sun.net.httpserver.HttpServer;

// 시나리오는 docs/map/record-location.md에 있다. 이 파일은 #19-S3 중 외부 서비스 호출 부분을 확인한다.
// 외부 서비스(네이버)는 가짜 서버로 대신한다. 진짜 키로 하는 호출은 자동 테스트에 넣지 않는다
class NaverAddressAdapterTest {

	private static final String BASE_URL = "http://naver.test";

	private static final String ROAD_ADDRESS_JSON = """
		{
		  "status": {"code": 0, "name": "ok", "message": "done"},
		  "results": [
		    {"name": "addr", "region": {"area1": {"name": "서울특별시"}, "area2": {"name": "강남구"},
		      "area3": {"name": "역삼동"}, "area4": {"name": ""}}, "land": {"name": "", "number1": "737", "number2": ""}},
		    {"name": "roadaddr", "region": {"area1": {"name": "서울특별시"}, "area2": {"name": "강남구"},
		      "area3": {"name": "역삼동"}, "area4": {"name": ""}}, "land": {"name": "테헤란로", "number1": "152", "number2": ""}}
		  ]
		}
		""";

	private static final String LOT_ADDRESS_ONLY_JSON = """
		{
		  "status": {"code": 0, "name": "ok", "message": "done"},
		  "results": [
		    {"name": "addr", "region": {"area1": {"name": "서울특별시"}, "area2": {"name": "강남구"},
		      "area3": {"name": "역삼동"}, "area4": {"name": ""}}, "land": {"name": "", "number1": "737", "number2": "3"}}
		  ]
		}
		""";

	// 실제 네이버 응답(좌표 127.0276,37.4979)을 그대로 옮긴 것이다. 좌표·건물 정보 같은 쓰지 않는 필드가 함께 온다
	private static final String REAL_RESPONSE_JSON = "{\"status\":{\"code\":0,\"name\":\"ok\",\"message\":\"done\"},\"results\":[{\"name\":\"roadaddr\",\"code\":{\"id\":\"1165010800\",\"type\":\"L\",\"mappingId\":\"09650108\"},\"region\":{\"area0\":{\"name\":\"kr\",\"coords\":{\"center\":{\"crs\":\"\",\"x\":0.0,\"y\":0.0}}},\"area1\":{\"name\":\"서울특별시\",\"coords\":{\"center\":{\"crs\":\"EPSG:4326\",\"x\":126.978388,\"y\":37.56661}},\"alias\":\"서울\"},\"area2\":{\"name\":\"서초구\",\"coords\":{\"center\":{\"crs\":\"EPSG:4326\",\"x\":127.032598,\"y\":37.483569}}},\"area3\":{\"name\":\"서초동\",\"coords\":{\"center\":{\"crs\":\"EPSG:4326\",\"x\":127.019508,\"y\":37.490119}}},\"area4\":{\"name\":\"\",\"coords\":{\"center\":{\"crs\":\"\",\"x\":0.0,\"y\":0.0}}}},\"land\":{\"type\":\"\",\"number1\":\"405\",\"number2\":\"2\",\"addition0\":{\"type\":\"building\",\"value\":\"가로판매대\"},\"addition1\":{\"type\":\"zipcode\",\"value\":\"06615\"},\"addition2\":{\"type\":\"roadGroupCode\",\"value\":\"116502102001\"},\"addition3\":{\"type\":\"\",\"value\":\"\"},\"addition4\":{\"type\":\"\",\"value\":\"\"},\"name\":\"강남대로\",\"coords\":{\"center\":{\"crs\":\"\",\"x\":0.0,\"y\":0.0}}}},{\"name\":\"addr\",\"code\":{\"id\":\"1165010800\",\"type\":\"L\",\"mappingId\":\"09650108\"},\"region\":{\"area0\":{\"name\":\"kr\",\"coords\":{\"center\":{\"crs\":\"\",\"x\":0.0,\"y\":0.0}}},\"area1\":{\"name\":\"서울특별시\",\"coords\":{\"center\":{\"crs\":\"EPSG:4326\",\"x\":126.978388,\"y\":37.56661}},\"alias\":\"서울\"},\"area2\":{\"name\":\"서초구\",\"coords\":{\"center\":{\"crs\":\"EPSG:4326\",\"x\":127.032598,\"y\":37.483569}}},\"area3\":{\"name\":\"서초동\",\"coords\":{\"center\":{\"crs\":\"EPSG:4326\",\"x\":127.019508,\"y\":37.490119}}},\"area4\":{\"name\":\"\",\"coords\":{\"center\":{\"crs\":\"\",\"x\":0.0,\"y\":0.0}}}},\"land\":{\"type\":\"1\",\"number1\":\"1373\",\"number2\":\"\",\"addition0\":{\"type\":\"\",\"value\":\"\"},\"addition1\":{\"type\":\"\",\"value\":\"\"},\"addition2\":{\"type\":\"\",\"value\":\"\"},\"addition3\":{\"type\":\"\",\"value\":\"\"},\"addition4\":{\"type\":\"\",\"value\":\"\"},\"name\":\"\",\"coords\":{\"center\":{\"crs\":\"\",\"x\":0.0,\"y\":0.0}}}}]}";

	// 도로명 주소 없이 산 지번(land.type "2")만 오는 응답
	private static final String MOUNTAIN_LOT_JSON = """
		{
		  "status": {"code": 0, "name": "ok", "message": "done"},
		  "results": [
		    {"name": "addr", "region": {"area1": {"name": "강원특별자치도"}, "area2": {"name": "홍천군"},
		      "area3": {"name": "서면"}, "area4": {"name": "팔봉리"}},
		      "land": {"type": "2", "number1": "12", "number2": "3", "name": ""}}
		  ]
		}
		""";

	private static NaverMapProperties properties(String clientId, String clientSecret, String baseUrl,
			Duration readTimeout) {
		return new NaverMapProperties(clientId, clientSecret, baseUrl, Duration.ofSeconds(1), readTimeout);
	}

	private record Fixture(NaverAddressAdapter adapter, MockRestServiceServer server) {
	}

	private static Fixture fixture(String clientId, String clientSecret) {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		NaverMapProperties properties = properties(clientId, clientSecret, BASE_URL, Duration.ofSeconds(2));
		return new Fixture(new NaverAddressAdapter(properties, builder), server);
	}

	@Test
	@DisplayName("#19-S3 좌표를 보내면 도로명 주소를 받고, 키는 헤더로만 보낸다")
	void loadsRoadAddress() {
		Fixture f = fixture("test-id", "test-secret");
		f.server().expect(method(HttpMethod.GET))
			.andExpect(requestTo(org.hamcrest.Matchers.startsWith(BASE_URL + "/map-reversegeocode/v2/gc")))
			// 네이버는 경도,위도 순서로 받는다
			.andExpect(queryParam("coords", "127.0276,37.4979"))
			.andExpect(header("X-NCP-APIGW-API-KEY-ID", "test-id"))
			.andExpect(header("X-NCP-APIGW-API-KEY", "test-secret"))
			.andRespond(withSuccess(ROAD_ADDRESS_JSON, MediaType.APPLICATION_JSON));

		Optional<String> address = f.adapter().loadByCoordinates(37.4979, 127.0276);

		assertThat(address).contains("서울특별시 강남구 테헤란로 152");
		f.server().verify();
	}

	@Test
	@DisplayName("#19-S3 실제 네이버 응답 모양에서도 도로명 주소를 받는다")
	void loadsAddressFromRealResponseShape() {
		Fixture f = fixture("test-id", "test-secret");
		f.server().expect(method(HttpMethod.GET))
			.andRespond(withSuccess(REAL_RESPONSE_JSON, MediaType.APPLICATION_JSON));

		Optional<String> address = f.adapter().loadByCoordinates(37.4979, 127.0276);

		assertThat(address).contains("서울특별시 서초구 강남대로 405-2");
	}

	@Test
	@DisplayName("#19-S3 도로명 주소가 없으면 지번 주소를 받는다")
	void fallsBackToLotAddress() {
		Fixture f = fixture("test-id", "test-secret");
		f.server().expect(method(HttpMethod.GET)).andRespond(withSuccess(LOT_ADDRESS_ONLY_JSON, MediaType.APPLICATION_JSON));

		Optional<String> address = f.adapter().loadByCoordinates(37.4979, 127.0276);

		assertThat(address).contains("서울특별시 강남구 역삼동 737-3");
	}

	@Test
	@DisplayName("#19-S3 산 지번이면 번호 앞에 산을 붙여 일반 지번과 구분한다")
	void marksMountainLot() {
		Fixture f = fixture("test-id", "test-secret");
		f.server().expect(method(HttpMethod.GET)).andRespond(withSuccess(MOUNTAIN_LOT_JSON, MediaType.APPLICATION_JSON));

		Optional<String> address = f.adapter().loadByCoordinates(37.7, 127.6);

		assertThat(address).contains("강원특별자치도 홍천군 서면 팔봉리 산 12-3");
	}

	@Test
	@DisplayName("#19-S3 주소가 없는 좌표이면 빈 값을 받는다")
	void emptyWhenNoAddress() {
		Fixture f = fixture("test-id", "test-secret");
		f.server().expect(method(HttpMethod.GET))
			.andRespond(withSuccess("{\"status\":{\"code\":3,\"name\":\"no results\"},\"results\":[]}",
					MediaType.APPLICATION_JSON));

		assertThat(f.adapter().loadByCoordinates(0.0, 0.0)).isEmpty();
	}

	@Test
	@DisplayName("#19-S3 외부 서비스가 오류를 돌려주어도 예외 없이 빈 값을 받는다")
	void emptyWhenServerError() {
		Fixture f = fixture("test-id", "test-secret");
		f.server().expect(method(HttpMethod.GET)).andRespond(withServerError());

		assertThat(f.adapter().loadByCoordinates(37.4979, 127.0276)).isEmpty();
	}

	@Test
	@DisplayName("#19-S3 알 수 없는 응답이어도 예외 없이 빈 값을 받는다")
	void emptyWhenUnexpectedBody() {
		Fixture f = fixture("test-id", "test-secret");
		f.server().expect(method(HttpMethod.GET)).andRespond(withSuccess("not json", MediaType.APPLICATION_JSON));

		assertThat(f.adapter().loadByCoordinates(37.4979, 127.0276)).isEmpty();
	}

	@Test
	@DisplayName("#19-S3 키가 설정되지 않으면 외부 서비스를 부르지 않고 빈 값을 받는다")
	void skipsCallWithoutCredentials() {
		Fixture f = fixture("", "");

		assertThat(f.adapter().loadByCoordinates(37.4979, 127.0276)).isEmpty();
		// 기대한 요청이 없으므로 호출이 있었다면 여기까지 오기 전에 실패한다
		f.server().verify();
	}

	@Test
	@DisplayName("#19-S3 외부 서비스가 제한 시간 안에 답하지 않으면 기다리지 않고 빈 값을 받는다")
	void emptyWhenTimeout() throws Exception {
		HttpServer slowServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		slowServer.createContext("/", exchange -> {
			try {
				Thread.sleep(1500);
				byte[] body = ROAD_ADDRESS_JSON.getBytes(StandardCharsets.UTF_8);
				exchange.getResponseHeaders().add("Content-Type", "application/json");
				exchange.sendResponseHeaders(200, body.length);
				exchange.getResponseBody().write(body);
			}
			catch (Exception ignored) {
				// 클라이언트가 먼저 끊으면 쓰기가 실패한다
			}
			finally {
				exchange.close();
			}
		});
		slowServer.start();
		try {
			String baseUrl = "http://127.0.0.1:" + slowServer.getAddress().getPort();
			NaverAddressAdapter adapter = new NaverAddressAdapter(
					properties("test-id", "test-secret", baseUrl, Duration.ofMillis(200)));

			long start = System.nanoTime();
			Optional<String> address = adapter.loadByCoordinates(37.4979, 127.0276);
			Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

			assertThat(address).isEmpty();
			assertThat(elapsed).isLessThan(Duration.ofMillis(1200));
		}
		finally {
			slowServer.stop(0);
		}
	}

	private static final String ROAD_WITHOUT_NUMBER_JSON = """
		{
		  "status": {"code": 0, "name": "ok", "message": "done"},
		  "results": [
		    {"name": "roadaddr", "region": {"area1": {"name": "서울특별시"}, "area2": {"name": "성동구"},
		      "area3": {"name": "성수동1가"}, "area4": {"name": ""}}, "land": {"name": "서울숲2길", "number1": "", "number2": ""}}
		  ]
		}
		""";

	// 도로명이 비어 있고 건물번호만 있는 응답
	private static final String NUMBER_WITHOUT_ROAD_NAME_JSON = """
		{
		  "status": {"code": 0, "name": "ok", "message": "done"},
		  "results": [
		    {"name": "roadaddr", "region": {"area1": {"name": "서울특별시"}, "area2": {"name": "성동구"},
		      "area3": {"name": "성수동1가"}, "area4": {"name": ""}}, "land": {"name": "", "number1": "32", "number2": "14"}}
		  ]
		}
		""";

	// 지역(region) 없이 도로명과 건물번호만 있는 응답. 그대로 쓰면 건물번호만 남는다
	private static final String ROAD_WITHOUT_REGION_JSON = """
		{
		  "status": {"code": 0, "name": "ok", "message": "done"},
		  "results": [
		    {"name": "roadaddr", "land": {"name": "서울숲2길", "number1": "32", "number2": "14"}}
		  ]
		}
		""";

	// 시·군·구가 없는 세종특별자치시의 도로명 주소
	private static final String SEJONG_ROAD_JSON = """
		{
		  "status": {"code": 0, "name": "ok", "message": "done"},
		  "results": [
		    {"name": "roadaddr", "region": {"area1": {"name": "세종특별자치시"}, "area2": {"name": ""},
		      "area3": {"name": "어진동"}, "area4": {"name": ""}}, "land": {"name": "한누리대로", "number1": "2130", "number2": ""}}
		  ]
		}
		""";

	// 이하는 장소 카드의 가려진 주소 보완(#239)이 쓰는 도로명 전용 조회다. 일기 위치(#19)의 지번 폴백은 위에서 그대로 확인한다

	@Test
	@DisplayName("#239-S1 도로명 전용 조회는 도로명과 건물번호를 받는다")
	void loadsRoadAddressOnly() {
		Fixture f = fixture("test-id", "test-secret");
		f.server().expect(method(HttpMethod.GET))
			.andExpect(queryParam("coords", "127.0276,37.4979"))
			.andExpect(header("X-NCP-APIGW-API-KEY-ID", "test-id"))
			.andRespond(withSuccess(ROAD_ADDRESS_JSON, MediaType.APPLICATION_JSON));

		Optional<String> address = new NaverRoadAddressAdapter(f.adapter()).loadRoadAddressByCoordinates(37.4979, 127.0276);

		assertThat(address).contains("서울특별시 강남구 테헤란로 152");
		f.server().verify();
	}

	@Test
	@DisplayName("#239-S9 도로명 주소 없이 지번만 있으면 도로명 전용 조회는 빈 값이다")
	void roadOnlyLookupDoesNotFallBackToLotAddress() {
		Fixture f = fixture("test-id", "test-secret");
		f.server().expect(method(HttpMethod.GET)).andRespond(withSuccess(LOT_ADDRESS_ONLY_JSON, MediaType.APPLICATION_JSON));

		assertThat(new NaverRoadAddressAdapter(f.adapter()).loadRoadAddressByCoordinates(37.4979, 127.0276)).isEmpty();
	}

	@Test
	@DisplayName("#239-S9 같은 응답에서 일기 위치의 조회(#19)는 여전히 지번을 받는다")
	void lotLookupForDiaryLocationIsUnchanged() {
		Fixture f = fixture("test-id", "test-secret");
		f.server().expect(method(HttpMethod.GET)).andRespond(withSuccess(LOT_ADDRESS_ONLY_JSON, MediaType.APPLICATION_JSON));

		assertThat(f.adapter().loadByCoordinates(37.4979, 127.0276)).contains("서울특별시 강남구 역삼동 737-3");
	}

	@Test
	@DisplayName("#239-S9 도로명은 있지만 건물번호가 없으면 도로명 전용 조회는 빈 값이다")
	void roadOnlyLookupNeedsBuildingNumber() {
		Fixture f = fixture("test-id", "test-secret");
		f.server().expect(method(HttpMethod.GET))
			.andRespond(withSuccess(ROAD_WITHOUT_NUMBER_JSON, MediaType.APPLICATION_JSON));

		assertThat(new NaverRoadAddressAdapter(f.adapter()).loadRoadAddressByCoordinates(37.5445, 127.0374)).isEmpty();
	}

	@Test
	@DisplayName("#239-S9 건물번호는 있지만 도로명이 비어 있으면 도로명 전용 조회는 빈 값이다")
	void roadOnlyLookupNeedsRoadName() {
		Fixture f = fixture("test-id", "test-secret");
		f.server().expect(method(HttpMethod.GET))
			.andRespond(withSuccess(NUMBER_WITHOUT_ROAD_NAME_JSON, MediaType.APPLICATION_JSON));

		assertThat(new NaverRoadAddressAdapter(f.adapter()).loadRoadAddressByCoordinates(37.5445, 127.0374)).isEmpty();
	}

	@Test
	@DisplayName("#239-S9 지역 정보 없이 도로명과 건물번호만 있으면 불완전한 주소라 빈 값이다")
	void roadOnlyLookupNeedsRegion() {
		Fixture f = fixture("test-id", "test-secret");
		f.server().expect(method(HttpMethod.GET))
			.andRespond(withSuccess(ROAD_WITHOUT_REGION_JSON, MediaType.APPLICATION_JSON));

		assertThat(new NaverRoadAddressAdapter(f.adapter()).loadRoadAddressByCoordinates(37.5445, 127.0374)).isEmpty();
	}

	@Test
	@DisplayName("#239-S9 시·군·구가 없는 세종특별자치시의 도로명 주소는 그대로 받는다")
	void roadOnlyLookupAcceptsSejongWithoutDistrict() {
		Fixture f = fixture("test-id", "test-secret");
		f.server().expect(method(HttpMethod.GET)).andRespond(withSuccess(SEJONG_ROAD_JSON, MediaType.APPLICATION_JSON));

		assertThat(new NaverRoadAddressAdapter(f.adapter()).loadRoadAddressByCoordinates(36.48, 127.28))
			.contains("세종특별자치시 한누리대로 2130");
	}

	@Test
	@DisplayName("#239-S3 도로명 전용 조회도 주소가 없는 좌표, 서버 오류, 키 없음이면 빈 값이다")
	void roadOnlyLookupIsEmptyOnFailures() {
		Fixture noAddress = fixture("test-id", "test-secret");
		noAddress.server().expect(method(HttpMethod.GET))
			.andRespond(withSuccess("{\"status\":{\"code\":3,\"name\":\"no results\"},\"results\":[]}",
					MediaType.APPLICATION_JSON));
		assertThat(new NaverRoadAddressAdapter(noAddress.adapter()).loadRoadAddressByCoordinates(37.0, 127.0)).isEmpty();

		Fixture serverError = fixture("test-id", "test-secret");
		serverError.server().expect(method(HttpMethod.GET)).andRespond(withServerError());
		assertThat(new NaverRoadAddressAdapter(serverError.adapter()).loadRoadAddressByCoordinates(37.0, 127.0)).isEmpty();

		Fixture noKey = fixture("", "");
		assertThat(new NaverRoadAddressAdapter(noKey.adapter()).loadRoadAddressByCoordinates(37.0, 127.0)).isEmpty();
		noKey.server().verify();
	}

}
