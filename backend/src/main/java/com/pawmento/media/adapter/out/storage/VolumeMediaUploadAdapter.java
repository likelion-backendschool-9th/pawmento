package com.pawmento.media.adapter.out.storage;

import com.pawmento.media.application.port.out.*;
import com.pawmento.media.domain.StoredUpload;
import jakarta.annotation.PostConstruct;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 파일 이름은 오직 서버가 발급한 키다. Linux volume의 SecureDirectoryStream으로 심볼릭 링크를 따라가지 않는다.
@Component
@RequiredArgsConstructor
public class VolumeMediaUploadAdapter implements StoreMediaUploadPort, DeleteMediaUploadPort {
  private final MediaStorageProperties properties;

  @PostConstruct
  void initialize() throws IOException {
    Path root = root();
    Path current = root.getRoot();
    for (Path part : root) {
      current = current.resolve(part);
      ensureDirectory(current);
    }
    ensureDirectory(root.resolve("tmp"));
    ensureDirectory(root.resolve("tmp/uploads"));
    ensureDirectory(root.resolve("tmp/multipart"));
    try (var ignored = uploads()) {
      /* 지원되지 않는 파일 시스템에서는 닫힌 상태로 실패한다. */
    }
  }

  private static void ensureDirectory(Path path) throws IOException {
    if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS))
      Files.createDirectory(
          path, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
    if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
      throw new IOException("미디어 폴더가 올바르지 않습니다.");
  }

  private Path root() {
    return properties.root().toAbsolutePath().normalize();
  }

  private SecureDirectoryStream<Path> uploads() throws IOException {
    Path target = root().resolve("tmp/uploads");
    DirectoryStream<Path> first = Files.newDirectoryStream(target.getRoot());
    if (!(first instanceof SecureDirectoryStream<Path> stream)) {
      first.close();
      throw new IOException("안전한 파일 저장을 지원하지 않습니다.");
    }
    SecureDirectoryStream<Path> current = stream;
    try {
      for (Path part : target) {
        SecureDirectoryStream<Path> next =
            current.newDirectoryStream(part, LinkOption.NOFOLLOW_LINKS);
        current.close();
        current = next;
      }
      return current;
    } catch (IOException | RuntimeException e) {
      current.close();
      throw e;
    }
  }

  private static Path filename(String key) {
    if (key == null || !key.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
      throw new MediaStorageException(false, new IOException("잘못된 저장 키"));
    return Path.of(key + ".upload");
  }

  @Override
  public StoredUpload store(String key, InputStream input, long maxBytes) {
    Path file = filename(key);
    boolean created = false;
    try (SecureDirectoryStream<Path> directory = uploads()) {
      try (SeekableByteChannel output =
          directory.newByteChannel(
              file,
              Set.of(
                  StandardOpenOption.CREATE_NEW,
                  StandardOpenOption.WRITE,
                  LinkOption.NOFOLLOW_LINKS),
              PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))) {
        created = true;
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        long total = 0;
        byte[] buffer = new byte[8192];
        int n;
        while ((n = input.read(buffer)) != -1) {
          total += n;
          if (total > maxBytes) throw new MediaStorageException(true, null);
          digest.update(buffer, 0, n);
          ByteBuffer bytes = ByteBuffer.wrap(buffer, 0, n);
          while (bytes.hasRemaining()) output.write(bytes);
        }
        return new StoredUpload(total, HexFormat.of().formatHex(digest.digest()));
      } catch (IOException | NoSuchAlgorithmException | RuntimeException e) {
        if (created)
          try {
            directory.deleteFile(file);
          } catch (IOException cleanup) {
            e.addSuppressed(cleanup);
          }
        if (e instanceof MediaStorageException storage) throw storage;
        throw new MediaStorageException(false, e);
      }
    } catch (IOException e) {
      throw new MediaStorageException(false, e);
    }
  }

  @Override
  public void delete(String key) {
    Path file = filename(key);
    try (var directory = uploads()) {
      try {
        directory.deleteFile(file);
      } catch (NoSuchFileException ignored) {
      }
    } catch (IOException e) {
      throw new MediaStorageException(false, e);
    }
  }
}
