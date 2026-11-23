package com.pawmento.question;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QuestionArchitectureTest {
  @Test
  @DisplayName("#31-S29 사용자·관리자 컨트롤러 경계를 유지하고 일반 질문 영속화에 JDBC를 추가하지 않는다")
  void preservesControllerAndJpaBoundaries() throws Exception {
    var root = Path.of("src/main/java/com/pawmento/question");
    try (var paths = Files.walk(root.resolve("adapter/in/web"))) {
      var controllers = new java.util.ArrayList<String>();
      for (var path : paths.filter(p -> p.toString().endsWith("Controller.java")).toList()) {
        if (Files.readString(path).contains("@RestController"))
          controllers.add(path.getFileName().toString());
      }
      assertThat(controllers.stream().filter(name -> !name.startsWith("Admin"))).hasSize(1);
      assertThat(controllers.stream().filter(name -> name.startsWith("Admin")))
          .isNotEmpty().allMatch(name -> name.matches("Admin[A-Z].*Controller\\.java"));
    }
    var schemaFiles = java.util.Set.of("adapter/out/persistence/QuestionSeedConfiguration.java",
        "adapter/out/persistence/QuestionSeedInitializer.java",
        "adapter/out/persistence/QuestionProgressSchemaConfiguration.java");
    try (var paths = Files.walk(root)) {
      for (var path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
        if (!schemaFiles.contains(root.relativize(path).toString().replace('\\', '/'))) {
          assertThat(Files.readString(path)).as("일반 질문 저장 경계: %s", path)
              .doesNotContain("JdbcTemplate", "java.sql.Connection", "javax.sql.DataSource");
        }
      }
    }
  }

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
          } else if (relative.startsWith("application/service/")) {
            assertThat(dependency)
                .as("%s -> %s", path, dependency)
                .matches(
                    "java\\..*|lombok\\..*|org\\.springframework\\.(stereotype|transaction)\\..*|com\\.pawmento\\.question\\.(domain|application)\\..*|com\\.pawmento\\.global\\.exception\\..*");
          } else if (relative.startsWith("adapter/in/web/")) {
            assertThat(dependency)
                .as("%s -> %s", path, dependency)
                .doesNotContain(".domain.", ".application.service.", ".adapter.out.");
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
