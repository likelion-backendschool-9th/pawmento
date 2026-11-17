package com.pawmento.media.adapter.out.storage;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.pawmento.media.application.exception.*;
import com.pawmento.media.application.port.out.*;
import com.pawmento.media.domain.*;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class FfmpegFailurePreservationTest {
  @TempDir Path root;
  final ReadMediaFilePort input = mock(ReadMediaFilePort.class);
  final WriteMediaFilePort output = mock(WriteMediaFilePort.class);
  final MediaWorkspaceCleanup cleanup = mock(MediaWorkspaceCleanup.class);
  final BoundedMediaProcess process = mock(BoundedMediaProcess.class);
  final JsonMapper json = mock(JsonMapper.class);
  FfmpegMediaValidationAdapter adapter;

  @BeforeEach
  void setup() throws Exception {
    Files.createDirectories(root.resolve("tmp/work"));
    adapter =
        new FfmpegMediaValidationAdapter(
            input,
            output,
            new MediaStorageProperties(root),
            new MediaParserProperties("ffmpeg", "ffprobe", 30),
            process,
            json,
            cleanup);
  }

  MediaAsset asset(byte[] data) throws Exception {
    var a =
        MediaAsset.create(
            1L,
            2L,
            "DIARY_ATTACHMENT",
            "a.jpg",
            "image/jpeg",
            data.length,
            UUID.randomUUID().toString(),
            Instant.now());
    a.begin(Instant.now());
    a.received(
        new StoredUpload(
            data.length,
            HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data))),
        Instant.now());
    return a;
  }

  @Test
  @DisplayName("#33-S16 검증 거부와 정리가 함께 실패해도 최초 실패와 suppressed 원인을 보존한다")
  void rejectedAndCleanup() throws Exception {
    byte[] data = {1, 2, 3};
    var a = asset(data);
    when(input.open(anyString(), eq("upload"))).thenReturn(new ByteArrayInputStream(data));
    var removal = new IOException("cleanup failed");
    doThrow(removal).when(cleanup).remove(any());
    assertThatThrownBy(() -> adapter.validate(a, UUID.randomUUID().toString()))
        .isInstanceOf(MediaValidationFailedException.class)
        .satisfies(
            e -> {
              assertThat(((MediaValidationFailedException) e).reason()).isEqualTo("MIME_MISMATCH");
              assertThat(e.getSuppressed()).hasSize(1);
              assertThat(e.getSuppressed()[0]).hasCause(removal);
            });
  }

  @Test
  @DisplayName("#33-S16 원본 읽기와 정리가 함께 실패해도 첫 I/O 원인을 보존한다")
  void readAndCleanup() throws Exception {
    var a = asset(new byte[] {1, 2, 3});
    var read = new IOException("input failed");
    when(input.open(anyString(), anyString()))
        .thenThrow(new MediaStorageUnavailableException(read));
    var removal = new IOException("cleanup failed");
    doThrow(removal).when(cleanup).remove(any());
    assertThatThrownBy(() -> adapter.validate(a, UUID.randomUUID().toString()))
        .hasCause(read)
        .satisfies(e -> assertThat(e.getSuppressed()[0]).hasCause(removal));
  }

  @Test
  @DisplayName("#33-S3 JSON 매퍼의 프로그래밍 오류를 INVALID_MEDIA로 바꾸지 않는다")
  void mapperBug() throws Exception {
    byte[] data;
    try (var in = getClass().getResourceAsStream("/media/sample.jpg")) {
      data = in.readAllBytes();
    }
    var a = asset(data);
    when(input.open(anyString(), anyString())).thenReturn(new ByteArrayInputStream(data));
    when(process.run(anyList())).thenReturn("{}");
    var bug = new IllegalStateException("mapper bug");
    when(json.readTree(anyString())).thenThrow(bug);
    assertThatThrownBy(() -> adapter.validate(a, UUID.randomUUID().toString())).isSameAs(bug);
  }
}
