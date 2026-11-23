package com.pawmento.map.adapter.in.web;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.pawmento.map.adapter.in.web.exception.CoordinateRange;
import com.pawmento.map.adapter.in.web.exception.InvalidMapRequestException;
import com.pawmento.map.application.port.in.ListPlacesQuery;

/**
 * 요청의 원본 쿼리 문자열만 읽어 이 API가 받는 키의 값을 엄격하게 해석한다.
 * 스프링의 모델 바인딩은 URL에 없는 값을 같은 이름의 요청 헤더로 채우고 날짜의 offset 접미사(2026-11-17Z)도 받아서
 * "필수 쿼리를 YYYY-MM-DD로 받는다"는 계약과 달라진다. 그래서 키마다 값이 정확히 하나인지, 형식이 맞는지를 여기서 직접 본다.
 * 이 API가 받지 않는 키는 값과 인코딩을 보지 않고 지나간다.
 */
final class MapQueryParameters {

	private static final Pattern DIGITS = Pattern.compile("[0-9]+");

	private static final Pattern DATE = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}");

	// 부호 있는 십진수. 지수(1e-7)는 받고 NaN, Infinity, 16진수(0x1p3), 접미사(1d, 1f), 앞뒤 공백은 받지 않는다.
	// 자바스크립트의 숫자 문자열이 아주 작은 값을 지수로 만들 수 있어서 지수는 허용한다
	private static final Pattern DECIMAL = Pattern.compile("-?[0-9]+(\\.[0-9]+)?([eE][+-]?[0-9]+)?");

	private final Map<String, String> values;

	private MapQueryParameters(Map<String, String> values) {
		this.values = Map.copyOf(values);
	}

	/**
	 * @param rawQuery 디코딩하기 전의 쿼리 문자열(없으면 null)
	 * @param accepted 이 API가 받는 키
	 */
	static MapQueryParameters parse(String rawQuery, String... accepted) {
		Set<String> keys = Set.of(accepted);
		Map<String, String> values = new HashMap<>();
		if (rawQuery != null && !rawQuery.isEmpty()) {
			for (String pair : rawQuery.split("&", -1)) {
				String[] parts = pair.split("=", 2);
				String key = decodeOrNull(parts[0]);
				// 디코딩할 수 없는 키와 받지 않는 키는 이 API와 상관없다. 값은 받는 키일 때만 디코딩한다
				if (key == null || !keys.contains(key)) {
					continue;
				}
				String value = parts.length == 2 ? decode(parts[1]) : "";
				if (value.isEmpty()) {
					throw InvalidMapRequestException.missing(key);
				}
				// %70etId처럼 인코딩한 같은 키도 중복으로 본다
				if (values.putIfAbsent(key, value) != null) {
					throw InvalidMapRequestException.duplicated(key);
				}
			}
		}
		return new MapQueryParameters(values);
	}

	// 1 이상의 Long 정수. 소수, 부호, 공백, 범위를 넘는 값은 거절한다
	long positiveLong(String name) {
		String value = required(name);
		if (DIGITS.matcher(value).matches()) {
			try {
				long number = Long.parseLong(value);
				if (number >= 1) {
					return number;
				}
			}
			catch (NumberFormatException ignored) {
				// 아래에서 형식 오류로 거절한다
			}
		}
		throw InvalidMapRequestException.invalidPositiveInteger(name);
	}

	// -90 이상 90 이하의 위도. 숫자가 아니거나 NaN·무한대이거나 범위를 벗어나면 거절한다. 반올림하지 않도록 십진수 그대로 돌려준다
	BigDecimal latitude(String name) {
		BigDecimal value = decimal(name);
		if (value.abs().compareTo(BigDecimal.valueOf(CoordinateRange.MAX_LATITUDE_DEGREES)) > 0) {
			throw InvalidMapRequestException.latitudeOutOfRange();
		}
		return value;
	}

	// -180 이상 180 이하의 경도
	BigDecimal longitude(String name) {
		BigDecimal value = decimal(name);
		if (value.abs().compareTo(BigDecimal.valueOf(CoordinateRange.MAX_LONGITUDE_DEGREES)) > 0) {
			throw InvalidMapRequestException.longitudeOutOfRange();
		}
		return value;
	}

	private BigDecimal decimal(String name) {
		String value = required(name);
		// 1e999처럼 double로 나타낼 수 없게 큰 지수는 숫자로 인정하지 않는다
		if (DECIMAL.matcher(value).matches() && Double.isFinite(Double.parseDouble(value))) {
			try {
				return new BigDecimal(value);
			}
			catch (NumberFormatException ignored) {
				// 아래에서 형식 오류로 거절한다
			}
		}
		throw InvalidMapRequestException.invalidNumber(name);
	}

	// 정확히 YYYY-MM-DD인 날짜. offset 접미사(Z, +09:00)나 시각, 달력에 없는 날(2026-02-30)은 거절한다
	LocalDate date(String name) {
		String value = required(name);
		if (DATE.matcher(value).matches()) {
			try {
				return LocalDate.parse(value);
			}
			catch (DateTimeException ignored) {
				// 아래에서 형식 오류로 거절한다
			}
		}
		throw InvalidMapRequestException.invalidDate(name);
	}

	// 쉼표로 구분한 장소 종류의 이름. 보내지 않으면 모든 종류다. 소문자, 없는 종류, 빈 항목, 공백, 중복은 거절한다
	Set<String> placeCategories(String name) {
		String value = values.get(name);
		if (value == null) {
			return new LinkedHashSet<>(ListPlacesQuery.CATEGORIES);
		}
		Set<String> categories = new LinkedHashSet<>();
		for (String item : value.split(",", -1)) {
			if (!ListPlacesQuery.CATEGORIES.contains(item) || !categories.add(item)) {
				throw InvalidMapRequestException.invalidCategories(name);
			}
		}
		return categories;
	}

	private String required(String name) {
		String value = values.get(name);
		if (value == null) {
			throw InvalidMapRequestException.missing(name);
		}
		return value;
	}

	private static String decodeOrNull(String raw) {
		try {
			return decode(raw);
		}
		catch (InvalidMapRequestException e) {
			return null;
		}
	}

	// 퍼센트 인코딩을 한 번만 풀고 +는 공백으로 본다. 잘못된 %XX와 UTF-8이 아닌 바이트는 거절한다
	private static String decode(String raw) {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		byte[] input = raw.getBytes(StandardCharsets.UTF_8);
		for (int i = 0; i < input.length; i++) {
			if (input[i] == '%') {
				if (i + 2 >= input.length) {
					throw InvalidMapRequestException.malformedEncoding();
				}
				int high = Character.digit((char) input[++i], 16);
				int low = Character.digit((char) input[++i], 16);
				if (high < 0 || low < 0) {
					throw InvalidMapRequestException.malformedEncoding();
				}
				bytes.write(high * 16 + low);
			}
			else {
				bytes.write(input[i] == '+' ? ' ' : input[i]);
			}
		}
		try {
			return StandardCharsets.UTF_8.newDecoder()
				.onMalformedInput(CodingErrorAction.REPORT)
				.onUnmappableCharacter(CodingErrorAction.REPORT)
				.decode(ByteBuffer.wrap(bytes.toByteArray()))
				.toString();
		}
		catch (CharacterCodingException e) {
			throw InvalidMapRequestException.malformedEncoding();
		}
	}

}
