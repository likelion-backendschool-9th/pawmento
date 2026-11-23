package com.pawmento.map.domain;

/**
 * 공공데이터의 전화번호를 "걸 수 있는 번호"로 정리한다. 걸 수 있다고 확신할 수 없으면 null(없음)로 둔다.
 * 숫자만 남긴 뒤 ① 8자리는 서울 주소일 때만 앞에 02를 붙이고 ② 0으로 시작하는 번호만 받으며 ③ 자릿수가 맞을 때만
 * 하이픈을 붙인다. 지역번호를 알 수 없는 번호를 임의로 고치지 않는다(#172-S6).
 */
public final class PlacePhone {

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
		String digits = raw.replaceAll("[^0-9]", "");
		// 문자가 섞인 값(예: "없음")은 숫자가 하나도 없어 여기서 걸러진다
		if (digits.isEmpty()) {
			return null;
		}
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
