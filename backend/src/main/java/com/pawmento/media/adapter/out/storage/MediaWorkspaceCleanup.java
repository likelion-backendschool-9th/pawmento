package com.pawmento.media.adapter.out.storage;

import java.io.IOException;
import java.nio.file.*;
import java.util.Comparator;
import org.springframework.stereotype.Component;

@Component
public class MediaWorkspaceCleanup {
  public void remove(Path work) throws IOException {
    try (var paths = Files.walk(work)) {
      for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
    }
  }
}
