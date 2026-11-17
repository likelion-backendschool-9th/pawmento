package com.pawmento.media.adapter.out.storage;

import com.pawmento.media.application.exception.*;
import com.pawmento.media.application.exception.MediaValidationFailedException;
import com.pawmento.media.application.port.out.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BoundedMediaProcess {
  private final MediaParserProperties properties;

  public String run(List<String> args) {
    // util-linux prlimit caps the child and all of its decoder threads. No shell or user options.
    var command =
        new ArrayList<>(
            List.of(
                "prlimit", "--as=1073741824", "--cpu=25", "--fsize=16777216", "--nofile=64", "--"));
    command.addAll(args);
    Process process;
    try {
      process = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
    } catch (IOException e) {
      throw new MediaStorageUnavailableException(e);
    }
    var pool = Executors.newVirtualThreadPerTaskExecutor();
    try {
      var output =
          pool.submit(
              () -> {
                try (var in = process.getInputStream();
                    var bytes = new ByteArrayOutputStream()) {
                  byte[] buffer = new byte[4096];
                  int n, total = 0;
                  while ((n = in.read(buffer)) != -1) {
                    total += n;
                    if (total > 65536) {
                      process.destroyForcibly();
                      throw new IOException("Parser output limit");
                    }
                    bytes.write(buffer, 0, n);
                  }
                  return bytes.toString(StandardCharsets.UTF_8);
                }
              });
      if (!process.waitFor(properties.timeoutSeconds(), TimeUnit.SECONDS)) {
        process.destroyForcibly();
        throw new MediaValidationFailedException("PARSER_TIMEOUT");
      }
      String result = output.get(2, TimeUnit.SECONDS);
      if (process.exitValue() == 127)
        throw new MediaStorageUnavailableException(new IOException("Media tools unavailable"));
      if (process.exitValue() != 0) throw new MediaValidationFailedException("INVALID_MEDIA");
      return result;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new MediaStorageUnavailableException(e);
    } catch (ExecutionException | TimeoutException e) {
      throw new MediaValidationFailedException("PARSER_OUTPUT_LIMIT");
    } finally {
      if (process.isAlive()) process.destroyForcibly();
      pool.shutdownNow();
    }
  }
}
