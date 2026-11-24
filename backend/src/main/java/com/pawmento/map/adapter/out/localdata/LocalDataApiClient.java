package com.pawmento.map.adapter.out.localdata;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.pawmento.map.application.exception.LocalDataSyncFailedException;
import com.pawmento.map.application.port.out.LoadLocalDataPort;
import com.pawmento.map.application.port.out.LocalDataSnapshot;
import com.pawmento.map.domain.LocalDataRecord;
import com.pawmento.map.domain.PlaceCategory;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 행정안전부 인허가 조회서비스(병원 animal_hospitals, 미용 pet_grooming)를 페이지 끝까지 받는다. (#172-S5, S20)
 * 정부 서버가 자주 시간 초과·504를 내서 페이지마다 간격을 늘려 가며 여러 번 다시 시도한다. 그래도 받지 못하면 일부만 돌려주지 않고
 * 가져오기 전체를 중단한다. 인증키는 요청 주소에 들어가므로, 예외의 메시지와 원인에 요청 주소나 원래 예외를 넣지 않고
 * 로그에도 남기지 않는다.
 */
@Component
class LocalDataApiClient implements LoadLocalDataPort {

	private static final Logger log = LoggerFactory.getLogger(LocalDataApiClient.class);

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final LocalDataProperties properties;

	private final Consumer<Duration> sleeper;

	private final RestClient restClient;

	@Autowired
	LocalDataApiClient(LocalDataProperties properties) {
		this(properties, LocalDataApiClient::sleep);
	}

	// 테스트에서 쉬는 시간을 기록만 하고 실제로 쉬지 않게 한다
	LocalDataApiClient(LocalDataProperties properties, Consumer<Duration> sleeper) {
		this.properties = properties;
		this.sleeper = sleeper;
		HttpClient httpClient = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(properties.readTimeout());
		this.restClient = RestClient.builder().requestFactory(requestFactory).build();
	}

	@Override
	public LocalDataSnapshot load(PlaceCategory category) {
		String path = pathOf(category);
		if (!properties.hasServiceKey()) {
			log.warn("공공데이터 인증키(PUBLIC_DATA_SERVICE_KEY)가 설정되지 않아 가져오기를 중단합니다.");
			throw LocalDataSyncFailedException.fetchFailed(new CallFailure("인증키가 설정되지 않음"));
		}

		Page first = fetchPage(path, 1);
		int pages = first.totalCount() == 0 ? 0 : (first.totalCount() + properties.pageSize() - 1) / properties.pageSize();
		List<LocalDataRecord> records = new ArrayList<>(first.records());
		int totalCountAtEnd = first.totalCount();
		for (int pageNo = 2; pageNo <= pages; pageNo++) {
			sleeper.accept(properties.pageInterval());
			Page page = fetchPage(path, pageNo);
			records.addAll(page.records());
			totalCountAtEnd = page.totalCount();
		}
		return new LocalDataSnapshot(first.totalCount(), totalCountAtEnd, records);
	}

	private static String pathOf(PlaceCategory category) {
		return switch (category) {
			case HOSPITAL -> "/1741000/animal_hospitals/info";
			case GROOMING -> "/1741000/pet_grooming/info";
			default -> throw new IllegalArgumentException("이 API로 받을 수 없는 종류입니다: " + category);
		};
	}

	// 한 페이지를 받는다. 일시 오류(시간 초과, 5xx, 읽을 수 없는 본문)는 간격을 늘려 가며 다시 시도하고,
	// 다시 시도해도 같은 결과일 오류(인증키 문제, 포털의 오류 응답)는 바로 중단한다
	private Page fetchPage(String path, int pageNo) {
		Duration wait = properties.backoff();
		CallFailure last = null;
		for (int attempt = 1; attempt <= properties.maxAttempts(); attempt++) {
			try {
				return parse(call(path, pageNo));
			}
			catch (CallFailure failure) {
				last = failure;
				if (failure.permanent) {
					break;
				}
				log.warn("공공데이터 {}쪽 호출 실패({}번째 시도): {}", pageNo, attempt, failure.getMessage());
				if (attempt < properties.maxAttempts()) {
					sleeper.accept(wait);
					wait = wait.multipliedBy(2).compareTo(properties.maxBackoff()) > 0 ? properties.maxBackoff()
							: wait.multipliedBy(2);
				}
			}
		}
		throw LocalDataSyncFailedException.fetchFailed(last);
	}

	// 요청을 보낸다. 원래 예외는 주소(인증키)가 들어 있을 수 있어 버리고, 종류만 담은 CallFailure로 바꾼다
	private String call(String path, int pageNo) {
		URI uri = URI.create(properties.baseUrl() + path + "?serviceKey=" + encodedKey() + "&pageNo=" + pageNo
				+ "&numOfRows=" + properties.pageSize() + "&returnType=json");
		try {
			// 응답 헤더에 문자 인코딩이 없으면 스프링이 ISO-8859-1로 읽어 한글이 깨진다. JSON은 UTF-8이므로 바이트로 받아 직접 해석한다
			byte[] bytes = restClient.get().uri(uri).retrieve().body(byte[].class);
			String body = bytes == null ? "" : new String(bytes, StandardCharsets.UTF_8);
			if (body.isBlank()) {
				throw new CallFailure("빈 응답", false);
			}
			return body;
		}
		catch (RestClientResponseException e) {
			HttpStatusCode status = e.getStatusCode();
			// 400번대는 인증키·요청이 잘못된 것이라 다시 시도해도 같다. 408(시간 초과)과 429(너무 많은 요청)만 일시 오류로 본다
			boolean permanent = status.is4xxClientError() && status.value() != 408 && status.value() != 429;
			throw new CallFailure("HTTP " + status.value(), permanent);
		}
		catch (CallFailure e) {
			throw e;
		}
		catch (RuntimeException e) {
			// 시간 초과, 연결 오류 등. 예외 메시지에는 요청 주소가 있을 수 있어 종류만 남긴다
			throw new CallFailure(e.getClass().getSimpleName(), false);
		}
	}

	// 포털의 일반 인증키는 Encoding(%가 들어 있음)과 Decoding(+ / = 가 그대로 있음) 두 가지로 보인다. 이미 인코딩됐으면 그대로 쓴다
	private String encodedKey() {
		String key = properties.serviceKey();
		return key.contains("%") ? key : URLEncoder.encode(key, StandardCharsets.UTF_8);
	}

	private static Page parse(String body) {
		// 포털은 오류를 JSON이 아니라 XML(OpenAPI_ServiceResponse)을 200으로 돌려준다. returnReasonCode에 따라 다시 시도하면
		// 성공할 수 있는 일시 오류(서비스 시간 초과 05 등)와 다시 해도 같은 영구 오류(키·권한·한도)로 나눈다
		if (body.stripLeading().startsWith("<")) {
			String code = reasonCode(body);
			throw new CallFailure(code == null ? "포털의 오류 응답(XML)" : "포털의 오류 응답(XML) 코드 " + code, isPermanentPortalCode(code));
		}
		JsonNode root;
		try {
			root = JSON.readTree(body);
		}
		catch (RuntimeException e) {
			throw new CallFailure("읽을 수 없는 응답", false);
		}
		JsonNode response = root.get("response");
		if (response == null) {
			throw new CallFailure("응답 모양이 다름", false);
		}
		String resultCode = text(response.path("header"), "resultCode");
		if (resultCode != null && !resultCode.equals("0") && !resultCode.equals("00")) {
			throw new CallFailure("포털 결과 코드 " + resultCode, isPermanentPortalCode(resultCode));
		}
		JsonNode body2 = response.path("body");
		JsonNode totalCount = body2.get("totalCount");
		if (totalCount == null || !totalCount.canConvertToInt()) {
			throw new CallFailure("totalCount 없음", false);
		}
		return new Page(totalCount.asInt(), records(body2.path("items")));
	}

	private static final Pattern REASON_CODE = Pattern.compile("<returnReasonCode>\\s*([0-9]{1,3})\\s*</returnReasonCode>");

	// 공공데이터포털 공통 오류 코드 중 다시 시도해도 같은 결과인 것: 요청이 잘못됨(10 잘못된 파라미터, 11 필수 파라미터 없음, 12 없는
	// 서비스), 접근 거부(20, 21 일시적으로 쓸 수 없는 키), 일일 호출 한도 초과(22), 키 미등록(30)·기간 만료(31)·미등록 IP(32)·
	// 서명 없는 호출(33). 그 밖(01 애플리케이션 오류, 02 DB 오류, 04 HTTP 오류, 05 서비스 시간 초과, 99 알 수 없음)과 모르는 코드,
	// 코드가 없는 응답은 다시 시도하면 성공할 수 있어 일시 오류로 본다
	private static final Set<String> PERMANENT_PORTAL_CODES = Set.of("10", "11", "12", "20", "21", "22", "30", "31", "32",
			"33");

	private static String reasonCode(String xml) {
		Matcher matcher = REASON_CODE.matcher(xml);
		return matcher.find() ? matcher.group(1) : null;
	}

	private static boolean isPermanentPortalCode(String code) {
		return code != null && PERMANENT_PORTAL_CODES.contains(code.strip());
	}

	// items는 행이 없으면 빈 문자열, 하나면 객체, 여럿이면 배열로 온다
	private static List<LocalDataRecord> records(JsonNode items) {
		List<LocalDataRecord> records = new ArrayList<>();
		if (!items.isObject()) {
			return records;
		}
		JsonNode item = items.get("item");
		if (item == null) {
			return records;
		}
		if (item.isArray()) {
			item.forEach(node -> records.add(record(node)));
		}
		else if (item.isObject()) {
			records.add(record(item));
		}
		return records;
	}

	private static LocalDataRecord record(JsonNode node) {
		return new LocalDataRecord(text(node, "MNG_NO"), text(node, "SALS_STTS_NM"), text(node, "BPLC_NM"),
				text(node, "ROAD_NM_ADDR"), text(node, "LOTNO_ADDR"), text(node, "TELNO"), text(node, "CRD_INFO_X"),
				text(node, "CRD_INFO_Y"), text(node, "DAT_UPDT_PNT"));
	}

	// 항목이 없거나 null이면 null(응답에 없음), 빈 문자열이면 빈 문자열(값 없음)
	private static String text(JsonNode node, String name) {
		JsonNode value = node.get(name);
		return value == null || value.isNull() ? null : value.asText();
	}

	private static void sleep(Duration duration) {
		try {
			Thread.sleep(duration);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw LocalDataSyncFailedException.fetchFailed(new CallFailure("가져오기가 중단됨", true));
		}
	}

	private record Page(int totalCount, List<LocalDataRecord> records) {
	}

	// 호출 실패의 종류만 담는다. 요청 주소와 원래 예외의 메시지에는 인증키가 있을 수 있어 넣지 않는다
	private static final class CallFailure extends RuntimeException {

		private final boolean permanent;

		CallFailure(String summary) {
			this(summary, true);
		}

		CallFailure(String summary, boolean permanent) {
			super(summary, null, false, false);
			this.permanent = permanent;
		}

	}

}
