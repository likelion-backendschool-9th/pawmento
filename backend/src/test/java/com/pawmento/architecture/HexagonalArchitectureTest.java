package com.pawmento.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 현재 수정 범위 member/pet/postcard/family의 import 경계를 고정한다. 동작 테스트를 대체하지 않는다.
class HexagonalArchitectureTest {
	private static final Path ROOT = Path.of("src/main/java/com/pawmento");
	private static final Pattern IMPORT = Pattern.compile("(?m)^import\\s+(?:static\\s+)?([^;]+);");

	@Test
	@DisplayName("#52-S9 member/pet/postcard/family의 핵심은 JPA·웹·어댑터에 의존하지 않는다")
	void coreDoesNotDependOnTechnology() throws IOException {
		for (String domain : List.of("member", "pet", "postcard", "family")) {
			try (var paths = Files.walk(ROOT.resolve(domain))) {
				for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
					String relative = ROOT.relativize(path).toString();
					var imports = IMPORT.matcher(Files.readString(path));
					while (imports.find()) {
						String dependency = imports.group(1);
						if (relative.contains("/domain/")) {
							String allowed = "java\\..*|lombok\\.Getter|com\\.pawmento\\." + domain + "\\.domain\\..*";
							if (domain.equals("pet")) {
								allowed += "|com\\.pawmento\\.global\\.exception\\.(RuleViolationException|PermissionDeniedException)";
							}
							if (domain.equals("postcard")) {
								allowed += "|com\\.pawmento\\.global\\.exception\\.(RuleViolationException|StateConflictException)";
							}
							if (domain.equals("member")) {
								allowed += "|com\\.pawmento\\.global\\.exception\\.(RuleViolationException|StateConflictException)";
							}
							assertThat(dependency).as("%s -> %s", relative, dependency).matches(allowed);
						} else if (relative.contains("/application/")) {
							if (relative.startsWith("pet/application/exception/")) {
								assertThat(dependency).as("%s -> %s", relative, dependency)
									.doesNotContain("org.springframework.http", "global.error");
							}
							assertThat(dependency).as("%s -> %s", relative, dependency).doesNotContain(".adapter.",
									"jakarta.persistence", "org.springframework.data", "org.springframework.security",
									"global.security");
						} else if (relative.contains("/adapter/in/web/")) {
							assertThat(dependency).as("%s -> %s", relative, dependency).doesNotContain(".domain.",
									".application.service.", ".adapter.out.", "org.springframework.orm",
									"org.springframework.data", "jakarta.persistence");
						} else if (relative.contains("/adapter/out/persistence/")) {
							assertThat(dependency).as("%s -> %s", relative, dependency)
									.doesNotContain(".application.service.", ".application.port.in.", ".adapter.in.");
						}
					}
				}
			}
		}
	}
	@Test
	@DisplayName("#44-S6 diary 어댑터 전체는 지도 입력 포트만 참조하고 지도 입력 계약은 도메인을 노출하지 않는다")
	void mapBoundaryUsesInputContracts() throws IOException {
		try (var paths = Files.walk(ROOT.resolve("diary/adapter"))) {
			for (var path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
				var source = Files.readString(path);
				var imports = IMPORT.matcher(source);
				while (imports.find()) {
					String dependency = imports.group(1);
					if (dependency.startsWith("com.pawmento.map.")) {
						assertThat(dependency).as("%s -> %s", path, dependency)
							.startsWith("com.pawmento.map.application.port.in.");
					}
				}
				// import 없이 쓴 완전 수식 참조도 직접 의존이다.
				assertThat(source).as("%s", path).doesNotContain("com.pawmento.map.domain.");
			}
		}
		try (var paths = Files.walk(ROOT.resolve("map/application/port/in"))) {
			for (var path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
				assertThat(Files.readString(path)).as("%s", path).doesNotContain("com.pawmento.map.domain.");
			}
		}
	}

}
