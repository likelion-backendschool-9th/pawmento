package com.pawmento.question;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QuestionArchitectureTest {
  @Test
  @DisplayName("#31-S21 질문 핵심은 프레임워크·다른 도메인에 의존하지 않고 영속화는 출력 포트만 구현한다")
  void respectsHexagonalBoundaries() throws Exception {
    var root = Path.of("src/main/java/com/pawmento/question");
    var imports = Pattern.compile("(?m)^import\\s+(?:static\\s+)?([^;]+);");
    try (var paths = Files.walk(root)) {
      for (var path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
        var relative = root.relativize(path).toString();
        var matcher = imports.matcher(Files.readString(path));
        while (matcher.find()) {
          String dependency = matcher.group(1);
          if (relative.startsWith("domain/")) {
            assertThat(dependency)
                .as("%s -> %s", path, dependency)
                .matches(
                    "java\\..*|lombok\\.Getter|com\\.pawmento\\.question\\.domain\\..*|com\\.pawmento\\.global\\.exception\\..*");
          } else if (relative.startsWith("application/")) {
            assertThat(dependency)
                .as("%s -> %s", path, dependency)
                .matches(
                    "java\\..*|com\\.pawmento\\.question\\.(domain|application\\.port)\\..*|com\\.pawmento\\.global\\.exception\\..*");
          } else if (relative.startsWith("adapter/out/persistence/")) {
            assertThat(dependency)
                .as("%s -> %s", path, dependency)
                .doesNotContain(".application.service.", ".application.port.in.", ".adapter.in.");
          }
        }
      }
    }
  }
}
