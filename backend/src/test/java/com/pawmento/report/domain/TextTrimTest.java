package com.pawmento.report.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TextTrimTest {

	@Test
	@DisplayName("#112-S9 JavaScript trim()처럼 공백·NBSP·전각 공백·줄 구분자·BOM을 앞뒤에서 지운다")
	void stripsWhatJavaScriptTrimRemoves() {
		for (String space : new String[] {" ", "\t", "\n", "\u000B", "\u000C", "\r", "\u00A0", "\u3000", "\u2028",
			"\u2029", "\uFEFF"}) {
			assertThat(TextTrim.strip(space + "글" + space))
				.as("U+%04X", (int)space.charAt(0))
				.isEqualTo("글");
		}
	}

	@Test
	@DisplayName("#112-S9 JavaScript trim()이 남기는 U+001C~U+001F는 지우지 않는다")
	void keepsWhatJavaScriptTrimKeeps() {
		for (char c = '\u001C'; c <= '\u001F'; c++) {
			String value = c + "글" + c;
			assertThat(TextTrim.strip(value))
				.as("U+%04X", (int)c)
				.isEqualTo(value);
		}
	}
}
