package com.pawmento.community.adapter.in.web.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.pawmento.community.adapter.in.web.report.exception.InvalidReportIdException;
import com.pawmento.community.adapter.in.web.report.exception.InvalidReportProcessingRequestException;
import com.pawmento.community.application.port.in.report.ProcessReportCommand;
import com.pawmento.community.application.port.in.report.ProcessReportCommand.Resolution;

import tools.jackson.databind.json.JsonMapper;

class ProcessReportRequestTest {
	private final JsonMapper mapper = JsonMapper.builder().build();

	@ParameterizedTest
	@EnumSource(Resolution.class)
	@DisplayName("#92-S3 처리·기각에 최초 버전 0과 요청 메모를 전달한다")
	void mapsResolutionAndInitialVersion(Resolution resolution) {
		var request = read("""
				{"resolution":"%s","version":0,"memo":"확인 완료"}
				""".formatted(resolution));

		assertThat(request.toCommand(101, 8)).isEqualTo(new ProcessReportCommand(8, 101, resolution, 0, "확인 완료"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"null", "\"0\"", "0.0", "-1", "9223372036854775808"})
	@DisplayName("#92-S5 버전의 형식이나 범위가 잘못되면 요청을 거절한다")
	void rejectsInvalidVersion(String value) {
		var request = read("""
				{"resolution":"RESOLVED","version":%s}
				""".formatted(value));

		assertThatThrownBy(() -> request.toCommand(101, 8))
				.isInstanceOf(InvalidReportProcessingRequestException.class)
				.hasMessageContaining("version");
	}

	@ParameterizedTest
	@ValueSource(strings = {"{\"version\":0}", "{\"resolution\":\"PENDING\",\"version\":0}",
			"{\"resolution\":\"resolved\",\"version\":0}"})
	@DisplayName("#92-S5 처리 결정이 없거나 허용된 최종 결정이 아니면 거절한다")
	void rejectsInvalidResolution(String body) {
		assertThatThrownBy(() -> read(body).toCommand(101, 8))
				.isInstanceOf(InvalidReportProcessingRequestException.class)
				.hasMessageContaining("resolution");
	}

	@Test
	@DisplayName("#92-S5 버전 생략은 최초 버전 0으로 해석하지 않는다")
	void rejectsOmittedVersion() {
		var request = read("{\"resolution\":\"RESOLVED\"}");

		assertThatThrownBy(() -> request.toCommand(101, 8))
				.isInstanceOf(InvalidReportProcessingRequestException.class)
				.hasMessageContaining("version");
	}

	@Test
	@DisplayName("#92-S5 신고 ID는 양수여야 한다")
	void rejectsInvalidReportId() {
		var request = read("{\"resolution\":\"RESOLVED\",\"version\":0}");

		assertThatThrownBy(() -> request.toCommand(0, 8))
				.isInstanceOf(InvalidReportIdException.class)
				.hasMessageContaining("신고 ID");
	}

	private ProcessReportRequest read(String body) {
		return mapper.readValue(body, ProcessReportRequest.class);
	}
}
