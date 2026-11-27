package com.pawmento.report.domain;

// 신고 설명의 앞뒤 공백을 지운다. 기준은 프론트엔드의 trim()이고 community의 Post와 같다.
// global.text로 합치기 전까지 report가 community를 import하지 않도록 같은 함수를 둔다
public final class TextTrim {

	private TextTrim() {
	}

	public static String strip(String value) {
		int start = 0;
		int end = value.length();
		while (start < end && isSpace(value.charAt(start))) {
			start++;
		}
		while (end > start && isSpace(value.charAt(end - 1))) {
			end--;
		}
		return value.substring(start, end);
	}

	// JavaScript trim()이 지우는 문자: 탭·줄바꿈·U+000B·U+000C·U+000D, BOM(U+FEFF),
	// 공백 범주(Zs, U+00A0·U+3000 포함)와 줄·문단 구분자(U+2028·U+2029).
	// Character.isWhitespace는 U+001C~U+001F도 공백으로 보지만 trim()은 남기므로 쓰지 않는다
	private static boolean isSpace(char c) {
		return (c >= '\t' && c <= '\r') || c == '\uFEFF' || Character.isSpaceChar(c);
	}
}
