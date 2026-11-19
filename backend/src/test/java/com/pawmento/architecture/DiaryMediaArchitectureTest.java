package com.pawmento.architecture;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.*;

class DiaryMediaArchitectureTest {
  @Test
  @DisplayName("#32-S9 diary/media 핵심은 다른 도메인·JPA·웹·출력 어댑터에 의존하지 않는다")
  void coreBoundaries() throws Exception {
    var imports = Pattern.compile("(?m)^import\\s+(?:static\\s+)?([^;]+);");
    for (String module : List.of("diary", "media")) {
      var root = Path.of("src/main/java/com/pawmento", module);
      try (var paths = Files.walk(root)) {
        for (var path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
          String name = root.relativize(path).toString();
          var matcher = imports.matcher(Files.readString(path));
          while (matcher.find()) {
            String dependency = matcher.group(1);
            if (name.startsWith("domain/")) {
              assertThat(dependency)
                  .as("%s -> %s", path, dependency)
                  .matches(
                      "java\\..*|lombok\\.Getter|com\\.pawmento\\."
                          + module
                          + "\\.domain\\..*|com\\.pawmento\\.global\\.exception\\..*");
            } else if (name.startsWith("application/")) {
              assertThat(dependency)
                  .as("%s -> %s", path, dependency)
                  .doesNotContain(
                      ".adapter.",
                      "jakarta.persistence",
                      "org.springframework.data",
                      "org.springframework.http",
                      "org.springframework.jdbc",
                      "org.springframework.web");
              if (dependency.startsWith("com.pawmento."))
                assertThat(dependency).matches("com\\.pawmento\\.(" + module + "|global)\\..*");
            } else if (name.startsWith("adapter/in/web/")) {
              assertThat(dependency)
                  .as("%s -> %s", path, dependency)
                  .doesNotContain(
                      ".adapter.out.", ".application.service.", ".domain.", "jakarta.persistence");
            }
          }
        }
      }
    }
  }
}
