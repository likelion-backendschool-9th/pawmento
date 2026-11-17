package com.pawmento.media.adapter.out.storage;

import static org.assertj.core.api.Assertions.*;

import com.pawmento.media.application.exception.MediaContentLimitExceededException;
import com.pawmento.media.application.exception.MediaStorageUnavailableException;
import com.pawmento.media.domain.MediaAsset;
import java.io.*;
import java.nio.file.*;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class VolumeMediaUploadAdapterTest {
  @TempDir Path root;
  VolumeMediaUploadAdapter storage;

  @BeforeEach
  void init() throws Exception {
    storage = new VolumeMediaUploadAdapter(new MediaStorageProperties(root), key -> true);
    storage.initialize();
  }

  Path uploads() {
    return root.resolve("tmp/uploads");
  }

  @Test
  @DisplayName("#33-S15 실제 전송도 15MiB 경계만 받고 초과 파일은 남기지 않는다")
  void actualLimit() throws Exception {
    String key = UUID.randomUUID().toString();
    byte[] bytes = new byte[(int) MediaAsset.MAX_FILE_BYTES];
    assertThat(
            storage
                .store(key, new ByteArrayInputStream(bytes), MediaAsset.MAX_FILE_BYTES)
                .sizeBytes())
        .isEqualTo(bytes.length);
    String rejected = UUID.randomUUID().toString();
    assertThatThrownBy(
            () ->
                storage.store(
                    rejected,
                    new ByteArrayInputStream(new byte[bytes.length + 1]),
                    MediaAsset.MAX_FILE_BYTES))
        .isInstanceOf(MediaContentLimitExceededException.class);
    assertThat(uploads().resolve(rejected + ".upload")).doesNotExist();
  }

  @Test
  @DisplayName("#33-S17 경로 순회·파일/디렉터리 심볼릭 링크로 volume 밖에 쓰지 않는다")
  void paths() throws Exception {
    assertThatThrownBy(() -> storage.store("../escape", new ByteArrayInputStream(new byte[1]), 10))
        .isInstanceOf(IllegalArgumentException.class);
    Path outside = Files.createDirectory(root.resolve("outside"));
    Path target = Files.writeString(outside.resolve("kept"), "original");
    String key = UUID.randomUUID().toString();
    Files.createSymbolicLink(uploads().resolve(key + ".upload"), target);
    assertThatThrownBy(() -> storage.store(key, new ByteArrayInputStream(new byte[] {1}), 10))
        .isInstanceOf(MediaStorageUnavailableException.class);
    assertThat(Files.readString(target)).isEqualTo("original");
    Files.delete(uploads().resolve(key + ".upload"));
    Files.delete(uploads());
    Files.createSymbolicLink(uploads(), outside);
    assertThatThrownBy(
            () ->
                storage.store(
                    UUID.randomUUID().toString(), new ByteArrayInputStream(new byte[] {1}), 10))
        .isInstanceOf(MediaStorageUnavailableException.class);
    try (var files = Files.list(outside)) {
      assertThat(files.map(Path::getFileName).toList()).containsExactly(Path.of("kept"));
    }
  }

  @Test
  @DisplayName("#33-S4 기존 임시 파일을 덮어쓰지 않고 체크섬을 기록한다")
  void immutable() throws Exception {
    String key = UUID.randomUUID().toString();
    var receipt = storage.store(key, new ByteArrayInputStream("abc".getBytes()), 10);
    assertThat(receipt.sha256())
        .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    assertThatThrownBy(() -> storage.store(key, new ByteArrayInputStream("def".getBytes()), 10))
        .isInstanceOf(MediaStorageUnavailableException.class);
    assertThat(Files.readString(uploads().resolve(key + ".upload"))).isEqualTo("abc");
    storage.delete(key);
    storage.delete(key);
  }

  @Test
  @DisplayName("#33-S16 전송 스트림 실패 시 부분 파일을 제거한다")
  void interrupted() throws Exception {
    String key = UUID.randomUUID().toString();
    InputStream broken =
        new InputStream() {
          int calls;

          @Override
          public int read(byte[] b, int off, int len) throws IOException {
            if (calls++ == 0) {
              b[off] = 1;
              return 1;
            }
            throw new IOException("interrupted");
          }

          @Override
          public int read() throws IOException {
            throw new IOException("interrupted");
          }
        };
    assertThatThrownBy(() -> storage.store(key, broken, 10))
        .isInstanceOf(MediaStorageUnavailableException.class);
    assertThat(uploads().resolve(key + ".upload")).doesNotExist();
  }

  @Test
  @DisplayName("#33-S17 서비스본과 썸네일도 경로·심볼릭 링크·덮어쓰기를 거부한다")
  void protectedServiceFiles() throws Exception {
    String key = UUID.randomUUID().toString();
    storage.write(key, "content", new ByteArrayInputStream("safe".getBytes()), 10);
    assertThatThrownBy(
            () -> storage.write(key, "content", new ByteArrayInputStream("bad".getBytes()), 10))
        .isInstanceOf(MediaStorageUnavailableException.class);
    try (var in = storage.open(key, "content")) {
      assertThat(new String(in.readAllBytes())).isEqualTo("safe");
    }
    var files = uploads().getParent().getParent().resolve("files");
    Path target = files.resolve("target");
    Files.writeString(target, "private");
    String linked = UUID.randomUUID().toString();
    Files.createSymbolicLink(files.resolve(linked + ".media"), target);
    assertThatThrownBy(() -> storage.open(linked, "content"))
        .isInstanceOf(MediaStorageUnavailableException.class);
    assertThatThrownBy(() -> storage.open("../escape", "content"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("#33-S16 재시작 뒤 남은 오래된 파서 작업·multipart 파일을 안전하게 제거한다")
  void abandonedWorkspace() throws Exception {
    Path mediaRoot = uploads().getParent().getParent();
    Path workspace = mediaRoot.resolve("tmp/work/decode-abandoned");
    Files.createDirectory(workspace);
    Path outside = mediaRoot.resolve("keep");
    Files.writeString(outside, "keep");
    Files.createSymbolicLink(workspace.resolve("link"), outside);
    Files.writeString(workspace.resolve("input"), "abandoned");
    Path multipart = mediaRoot.resolve("tmp/multipart/old.tmp");
    Files.writeString(multipart, "stale");
    var old = java.nio.file.attribute.FileTime.from(java.time.Instant.now().minusSeconds(172800));
    Files.setLastModifiedTime(workspace, old);
    Files.setLastModifiedTime(multipart, old);
    storage.collect(java.time.Instant.now().minusSeconds(86400));
    assertThat(workspace).doesNotExist();
    assertThat(multipart).doesNotExist();
    assertThat(Files.readString(outside)).isEqualTo("keep");
  }
}
