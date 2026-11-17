package com.pawmento.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 현재 수정 범위 member/pet의 import 경계를 고정한다. 동작 테스트를 대체하지 않는다.
class HexagonalArchitectureTest {
	private static final Path ROOT = Path.of("src/main/java/com/pawmento");
	private static final Pattern IMPORT = Pattern.compile("(?m)^import\\s+(?:static\\s+)?([^;]+);");

	@Test
	@DisplayName("#52-S9 member/pet의 핵심은 JPA·웹·어댑터에 의존하지 않는다")
	void coreDoesNotDependOnTechnology() throws IOException {
		for (String domain : List.of("member", "pet")) {
			try (var paths = Files.walk(ROOT.resolve(domain))) {
				for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
					String relative = ROOT.relativize(path).toString();
					var imports = IMPORT.matcher(Files.readString(path));
					while (imports.find()) {
						String dependency = imports.group(1);
						if (relative.contains("/domain/")) {
							assertThat(dependency).as("%s -> %s", relative, dependency)
									.matches("java\\..*|lombok\\.Getter|com\\.pawmento\\." + domain + "\\.domain\\..*");
						} else if (relative.contains("/application/")) {
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
}
