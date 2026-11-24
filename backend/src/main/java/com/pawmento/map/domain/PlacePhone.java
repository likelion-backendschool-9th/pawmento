package com.pawmento.map.domain;

import java.util.regex.Pattern;

/**
 * 공공데이터의 전화번호를 "걸 수 있는 번호"로 정리한다. 걸 수 있다고 확신할 수 없으면 null(없음)로 둔다.
 * ① 값이 숫자만이거나 번호 하나가 공백·하이픈(지역번호는 괄호도)으로만 나뉜 모양일 때만 받는다. 내선·설명·복수 번호가 붙어
 * 있으면 그 숫자가 본 번호에 합쳐져 전혀 다른 번호가 되므로(02-733-9006 (내선 1) → 02-7339-0061) 받지 않는다.
 * 그다음 숫자만 남겨 ② 8자리는 서울 주소일 때만 앞에 02를 붙이고 ③ 0으로 시작하는 번호만 받으며 ④ 자릿수가 맞을 때만
 * 하이픈을 붙인다. 지역번호를 알 수 없는 번호를 임의로 고치지 않는다(#172-S6).
 */
public final class PlacePhone {

	// 숫자만 8~11자리
	private static final Pattern DIGITS_ONLY = Pattern.compile("[0-9]{8,11}");

	// 지역번호(괄호 가능) + 국번 + 끝 4자리. 구분은 공백이나 하이픈 하나다
	private static final Pattern AREA_FORM = Pattern.compile("\\(?[0-9]{2,3}\\)?[ -]?[0-9]{3,4}[ -][0-9]{4}");

	// 지역번호 없는 8자리: 국번 4자리 + 끝 4자리
	private static final Pattern LOCAL_FORM = Pattern.compile("[0-9]{4}[ -][0-9]{4}");

	private PlacePhone() {
	}

	/**
	 * @param raw 원본 전화번호(하이픈이 있어도 없어도 된다)
	 * @param address 원본 주소. 8자리 번호의 지역번호를 정하는 데만 쓴다
	 * @return 하이픈을 붙인 번호, 걸 수 있는 번호가 아니면 null
	 */
	public static String normalize(String raw, String address) {
		if (raw == null) {
			return null;
		}
		String value = raw.strip();
		if (!DIGITS_ONLY.matcher(value).matches() && !AREA_FORM.matcher(value).matches()
				&& !LOCAL_FORM.matcher(value).matches()) {
			return null;
		}
		String digits = value.replaceAll("[^0-9]", "");
		if (digits.length() == 8) {
			if (address == null || !address.startsWith("서울")) {
				return null;
			}
			digits = "02" + digits;
		}
		if (!digits.startsWith("0")) {
			return null;
		}
		if (digits.startsWith("02")) {
			// 서울: 02-XXX-XXXX(9자리), 02-XXXX-XXXX(10자리)
			return switch (digits.length()) {
				case 9 -> digits.substring(0, 2) + "-" + digits.substring(2, 5) + "-" + digits.substring(5);
				case 10 -> digits.substring(0, 2) + "-" + digits.substring(2, 6) + "-" + digits.substring(6);
				default -> null;
			};
		}
		// 그 밖의 지역번호·휴대전화: 0XX-XXX-XXXX(10자리), 0XX-XXXX-XXXX(11자리)
		return switch (digits.length()) {
			case 10 -> digits.substring(0, 3) + "-" + digits.substring(3, 6) + "-" + digits.substring(6);
			case 11 -> digits.substring(0, 3) + "-" + digits.substring(3, 7) + "-" + digits.substring(7);
			default -> null;
		};
	}

}
