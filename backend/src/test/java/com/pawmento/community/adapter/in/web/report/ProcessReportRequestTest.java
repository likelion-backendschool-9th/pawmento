package com.pawmento.community.adapter.in.web.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.pawmento.community.adapter.in.web.report.exception.InvalidReportProcessingRequestException;

import tools.jackson.databind.json.JsonMapper;

class ProcessReportRequestTest {
	private final JsonMapper mapper = JsonMapper.builder().build();

	@Test
	@DisplayName("#92-S3 처리 요청은 결정과 기대 버전을 유지하고 메모에 JS trim을 적용한다")
	void validatesDecision() {
		var request = mapper.readValue(
				"""
				{"resolution":"RESOLVED","version":0,"memo":"\uFEFF\u00A0확인\u3000","processedBy":999}
				""", ProcessReportRequest.class);
		assertThat(request.validate()).isEqualTo(new ProcessReportRequest.Validated("RESOLVED", 0, "확인"));
	}

	@ParameterizedTest
	@ValueSource(ints = {1, 500})
	@DisplayName("#92-S5 기각 메모는 정리 후 1자와 500자를 허용한다")
	void acceptsMemoBoundaries(int size) {
		var note = "가".repeat(size);
		var request = new ProcessReportRequest("DISMISSED", mapper.readTree("0"), mapper.valueToTree(" " + note + " "));
		assertThat(request.validate().memo()).isEqualTo(note);
	}

	@ParameterizedTest
	@ValueSource(strings = {"null", "-1", "1.0", "\"1\"", "9223372036854775808"})
	@DisplayName("#92-S5 기대 버전은 0 이상의 JSON 정수 Long이어야 한다")
	void rejectsInvalidVersion(String version) {
		var request = new ProcessReportRequest("RESOLVED", mapper.readTree(version), mapper.valueToTree("확인"));
		assertThatThrownBy(request::validate).isInstanceOf(InvalidReportProcessingRequestException.class)
				.hasMessage("version은 0 이상의 정수여야 해요.");
	}

	@Test
	@DisplayName("#92-S5 결정·메모 형식과 정리 후 메모 길이를 검증한다")
	void rejectsInvalidDecisionAndMemo() {
		var version = mapper.readTree("0");
		assertThatThrownBy(new ProcessReportRequest("PENDING", version, mapper.valueToTree("확인"))::validate)
				.isInstanceOf(InvalidReportProcessingRequestException.class);
		for (var memo : new Object[] {null, 1, "\uFEFF\u3000", "가".repeat(501)}) {
			assertThatThrownBy(new ProcessReportRequest("DISMISSED", version, mapper.valueToTree(memo))::validate)
					.isInstanceOf(InvalidReportProcessingRequestException.class)
					.hasMessage("처리 메모는 1자 이상 500자 이하여야 해요.");
		}
	}
}
