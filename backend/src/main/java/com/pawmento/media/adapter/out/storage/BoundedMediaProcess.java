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
      process = new ProcessBuilder(command).start();
    } catch (IOException e) {
      throw new MediaStorageUnavailableException(e);
    }
    var pool = Executors.newVirtualThreadPerTaskExecutor();
    try {
      var errors = pool.submit(() -> diagnostic(process.getErrorStream()));
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
                      throw new OutputLimitException();
                    }
                    bytes.write(buffer, 0, n);
                  }
                  return bytes.toString(StandardCharsets.UTF_8);
                }
              });
      if (!process.waitFor(properties.timeoutSeconds(), TimeUnit.SECONDS)) {
        process.destroyForcibly();
        throw new MediaStorageUnavailableException(new TimeoutException("Media process timed out"));
      }
      String result = output.get(2, TimeUnit.SECONDS);
      String stderr = errors.get(2, TimeUnit.SECONDS);
      if (process.exitValue() != 0) {
        var cause = new IOException("Media process exited " + process.exitValue() + ": " + stderr);
        // Positive identification only: unknown errors are infrastructure failures, never a reason
        // to delete input.
        String error = stderr.toLowerCase(Locale.ROOT);
        boolean ioFailure =
            List.of(
                    "no space left",
                    "input/output error",
                    "permission denied",
                    "read-only file system",
                    "cannot allocate memory",
                    "too many open files",
                    "resource temporarily unavailable",
                    "file too large",
                    "disk quota",
                    "error opening output",
                    "error writing",
                    "error closing")
                .stream()
                .anyMatch(error::contains);
        boolean invalid =
            List.of(
                    "invalid data found",
                    "error while decoding",
                    "invalid png",
                    "invalid jpeg",
                    "no jpeg data",
                    "invalid chunk",
                    "crc mismatch",
                    "corrupt",
                    "invalid frame",
                    "invalid nal",
                    "end of file")
                .stream()
                .anyMatch(error::contains);
        if (process.exitValue() != 127 && invalid && !ioFailure)
          throw new MediaValidationFailedException("INVALID_MEDIA", cause);
        throw new MediaStorageUnavailableException(cause);
      }
      return result;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new MediaStorageUnavailableException(e);
    } catch (ExecutionException e) {
      if (e.getCause() instanceof OutputLimitException)
        throw new MediaValidationFailedException("PARSER_OUTPUT_LIMIT", e.getCause());
      throw new MediaStorageUnavailableException(e.getCause());
    } catch (TimeoutException e) {
      throw new MediaStorageUnavailableException(e);
    } finally {
      if (process.isAlive()) process.destroyForcibly();
      pool.shutdownNow();
    }
  }

  private static final class OutputLimitException extends IOException {}

  private static String diagnostic(InputStream input) throws IOException {
    try (input;
        var bytes = new ByteArrayOutputStream()) {
      byte[] buffer = new byte[4096];
      int n;
      while ((n = input.read(buffer)) != -1) {
        int keep = Math.min(n, 16384 - bytes.size());
        if (keep > 0) bytes.write(buffer, 0, keep);
      }
      return bytes.toString(StandardCharsets.UTF_8);
    }
  }
}
