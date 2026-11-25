package com.pawmento.map.adapter.out.geocoding;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.pawmento.map.application.port.out.LoadAddressPort;
import com.pawmento.map.application.port.out.LoadRoadAddressPort;

/**
 * 네이버 Reverse Geocoding으로 좌표를 주소로 바꾼다. 일기 위치(#19)는 도로명이 없으면 지번으로 돌아가는 조회를,
 * 장소 카드(#239)는 도로명과 건물번호가 있을 때만 받아들이는 조회를 쓴다. 둘은 같은 호출과 설정을 쓴다.
 * 키가 없거나 외부 호출이 실패하면 빈 값을 돌려준다. 내부 프로그래밍 오류는 전파한다.
 */
@Component
class NaverAddressAdapter implements LoadAddressPort, LoadRoadAddressPort {

	private static final Logger log = LoggerFactory.getLogger(NaverAddressAdapter.class);

	private static final String PATH = "/map-reversegeocode/v2/gc";

	// 도로명 주소를 먼저 찾고, 없으면 지번 주소를 쓴다
	private static final String ORDERS = "roadaddr,addr";

	// 지번 주소(addr)의 land.type 값. 일반 지번은 "1", 산은 "2"
	private static final String MOUNTAIN_LOT_TYPE = "2";

	private final NaverMapProperties properties;
	private final RestClient restClient;

	@Autowired
	NaverAddressAdapter(NaverMapProperties properties) {
		this(properties, builderWithTimeouts(properties));
	}

	// 테스트에서 가짜 서버를 연결한 builder를 넣을 수 있게 한다
	NaverAddressAdapter(NaverMapProperties properties, RestClient.Builder builder) {
		this.properties = properties;
		this.restClient = builder.baseUrl(properties.baseUrl()).build();
	}

	private static RestClient.Builder builderWithTimeouts(NaverMapProperties properties) {
		HttpClient httpClient = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(properties.readTimeout());
		return RestClient.builder().requestFactory(requestFactory);
	}

	@Override
	public Optional<String> loadByCoordinates(double latitude, double longitude) {
		return fetch(latitude, longitude).flatMap(NaverAddressAdapter::toAddress);
	}

	// 도로명 주소와 건물번호가 있을 때만 받는다. 지번 주소로 돌아가지 않는다(#239-S9)
	@Override
	public Optional<String> loadRoadAddressByCoordinates(double latitude, double longitude) {
		return fetch(latitude, longitude).flatMap(NaverAddressAdapter::toRoadAddress);
	}

	private Optional<ReverseGeocodeResponse> fetch(double latitude, double longitude) {
		if (!properties.hasCredentials()) {
			log.warn("네이버 지도 키가 설정되지 않아 주소를 조회하지 않습니다.");
			return Optional.empty();
		}
		try {
			// 네이버는 좌표를 경도,위도 순서로 받는다. 문서 예시처럼 쉼표를 그대로 보낸다
			String coords = plain(longitude) + "," + plain(latitude);
			ReverseGeocodeResponse response = restClient.get()
				.uri(uriBuilder -> uriBuilder.path(PATH)
					.queryParam("coords", coords)
					.queryParam("orders", ORDERS)
					.queryParam("output", "json")
					.build())
				.header("X-NCP-APIGW-API-KEY-ID", properties.clientId())
				.header("X-NCP-APIGW-API-KEY", properties.clientSecret())
				.retrieve()
				.body(ReverseGeocodeResponse.class);
			return Optional.ofNullable(response);
		}
		catch (org.springframework.web.client.RestClientException e) {
			// 요청 헤더(키)는 로그에 남기지 않는다
			log.warn("주소 조회에 실패했습니다: {}", e.getClass().getSimpleName());
			return Optional.empty();
		}
	}

	private static String plain(double value) {
		return BigDecimal.valueOf(value).toPlainString();
	}

	private static boolean isOk(ReverseGeocodeResponse response) {
		return response.status() != null && response.status().code() == 0 && response.results() != null;
	}

	private static Optional<String> toRoadAddress(ReverseGeocodeResponse response) {
		if (!isOk(response)) {
			return Optional.empty();
		}
		return findResult(response, "roadaddr")
			.filter(result -> result.land() != null && result.land().number1() != null
					&& !result.land().number1().isBlank())
			.map(NaverAddressAdapter::compose)
			.filter(address -> !address.isBlank());
	}

	private static Optional<String> toAddress(ReverseGeocodeResponse response) {
		if (!isOk(response)) {
			return Optional.empty();
		}
		return findResult(response, "roadaddr").or(() -> findResult(response, "addr"))
			.map(NaverAddressAdapter::compose)
			.filter(address -> !address.isBlank());
	}

	private static Optional<Result> findResult(ReverseGeocodeResponse response, String name) {
		return response.results().stream().filter(java.util.Objects::nonNull).filter(result -> name.equals(result.name())).findFirst();
	}

	// 시·도, 시·군·구, (도로명 또는 동·리), 번호 순으로 이어 붙인다
	private static String compose(Result result) {
		List<String> parts = new ArrayList<>();
		if (result.region() != null) {
			add(parts, result.region().area1());
			add(parts, result.region().area2());
			if ("roadaddr".equals(result.name())) {
				addText(parts, result.land() == null ? null : result.land().name());
			}
			else {
				add(parts, result.region().area3());
				add(parts, result.region().area4());
			}
		}
		if (result.land() != null) {
			addText(parts, number(result));
		}
		return String.join(" ", parts);
	}

	// 지번 주소에서 type이 "2"이면 산이다. 산 번호와 일반 번호는 다른 필지이므로 "산"을 붙여 구분한다
	private static String number(Result result) {
		Land land = result.land();
		if (land.number1() == null || land.number1().isBlank()) {
			return null;
		}
		String number = land.number2() == null || land.number2().isBlank()
				? land.number1()
				: land.number1() + "-" + land.number2();
		boolean mountain = "addr".equals(result.name()) && MOUNTAIN_LOT_TYPE.equals(land.type());
		return mountain ? "산 " + number : number;
	}

	private static void add(List<String> parts, Area area) {
		if (area != null) {
			addText(parts, area.name());
		}
	}

	private static void addText(List<String> parts, String text) {
		if (text != null && !text.isBlank()) {
			parts.add(text);
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record ReverseGeocodeResponse(Status status, List<Result> results) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Status(int code) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Result(String name, Region region, Land land) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Region(Area area1, Area area2, Area area3, Area area4) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Area(String name) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Land(String type, String name, String number1, String number2) {
	}

}
