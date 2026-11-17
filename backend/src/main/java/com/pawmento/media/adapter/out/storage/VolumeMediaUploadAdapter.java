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
import org.springframework.stereotype.Component;

// 파일 이름은 오직 서버가 발급한 키다. Linux volume의 SecureDirectoryStream으로 심볼릭 링크를 따라가지 않는다.
@Component
public class VolumeMediaUploadAdapter
    implements StoreMediaUploadPort,
        DeleteMediaUploadPort,
        ReadMediaFilePort,
        WriteMediaFilePort,
        DeleteMediaFilesPort,
        CollectMediaOrphansPort {
  private final MediaStorageProperties properties;
  private final CheckMediaStorageReferencePort references;

  public VolumeMediaUploadAdapter(MediaStorageProperties properties) {
    this(properties, key -> true);
  }

  @org.springframework.beans.factory.annotation.Autowired
  public VolumeMediaUploadAdapter(
      MediaStorageProperties properties, CheckMediaStorageReferencePort references) {
    this.properties = properties;
    this.references = references;
  }

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
    ensureDirectory(root.resolve("tmp/work"));
    ensureDirectory(root.resolve("files"));
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
    return directory("tmp/uploads");
  }

  private SecureDirectoryStream<Path> directory(String folder) throws IOException {
    Path target = root().resolve(folder);
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

  private static Path serviceName(String key, String variant) {
    filename(key);
    return Path.of(
        key
            + switch (variant) {
              case "content" -> ".media";
              case "thumbnail" -> ".thumb";
              default -> throw new IllegalArgumentException("variant");
            });
  }

  @Override
  public InputStream open(String key, String variant) {
    Path name = variant.equals("upload") ? filename(key) : serviceName(key, variant);
    try (var dir = directory(variant.equals("upload") ? "tmp/uploads" : "files")) {
      var channel =
          dir.newByteChannel(name, Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS));
      return java.nio.channels.Channels.newInputStream(channel);
    } catch (IOException e) {
      throw new MediaStorageException(false, e);
    }
  }

  @Override
  public void write(String key, String variant, InputStream input, long limit) {
    Path name = serviceName(key, variant);
    boolean created = false;
    try (var dir = directory("files")) {
      try (var out =
          dir.newByteChannel(
              name,
              Set.of(
                  StandardOpenOption.CREATE_NEW,
                  StandardOpenOption.WRITE,
                  LinkOption.NOFOLLOW_LINKS),
              PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))) {
        created = true;
        long total = 0;
        byte[] buffer = new byte[8192];
        int n;
        while ((n = input.read(buffer)) != -1) {
          total += n;
          if (total > limit) throw new MediaStorageException(true, null);
          var bytes = ByteBuffer.wrap(buffer, 0, n);
          while (bytes.hasRemaining()) out.write(bytes);
        }
      } catch (IOException | RuntimeException e) {
        if (created)
          try {
            dir.deleteFile(name);
          } catch (IOException cleanup) {
            e.addSuppressed(cleanup);
          }
        throw new MediaStorageException(false, e);
      }
    } catch (IOException e) {
      throw new MediaStorageException(false, e);
    }
  }

  @Override
  public void deleteAll(String temporaryKey, String storageKey) {
    delete(temporaryKey);
    if (storageKey == null) return;
    try (var dir = directory("files")) {
      for (String variant : List.of("content", "thumbnail"))
        try {
          dir.deleteFile(serviceName(storageKey, variant));
        } catch (NoSuchFileException ignored) {
        }
    } catch (IOException e) {
      throw new MediaStorageException(false, e);
    }
  }

  private void collectWork(java.time.Instant cutoff) throws IOException {
    try (var dir = directory("tmp/work")) {
      for (Path entry : dir) {
        Path name = entry.getFileName();
        var attrs =
            dir.getFileAttributeView(
                    name,
                    java.nio.file.attribute.BasicFileAttributeView.class,
                    LinkOption.NOFOLLOW_LINKS)
                .readAttributes();
        if (!name.toString().startsWith("decode-")
            || !attrs.isDirectory()
            || !attrs.lastModifiedTime().toInstant().isBefore(cutoff)) continue;
        try (var child = dir.newDirectoryStream(name, LinkOption.NOFOLLOW_LINKS)) {
          // Workspaces contain only private regular files, never nested directories.
          for (Path file : child) child.deleteFile(file.getFileName());
        }
        dir.deleteDirectory(name);
      }
    }
    try (var dir = directory("tmp/multipart")) {
      for (Path entry : dir) {
        var name = entry.getFileName();
        var attrs =
            dir.getFileAttributeView(
                    name,
                    java.nio.file.attribute.BasicFileAttributeView.class,
                    LinkOption.NOFOLLOW_LINKS)
                .readAttributes();
        if (attrs.isRegularFile() && attrs.lastModifiedTime().toInstant().isBefore(cutoff))
          dir.deleteFile(name);
      }
    }
  }

  @Override
  public void collect(java.time.Instant cutoff) {
    try {
      collectWork(cutoff);
    } catch (IOException e) {
      throw new MediaStorageException(false, e);
    }
    for (String folder : List.of("tmp/uploads", "files")) {
      try (var dir = directory(folder)) {
        for (Path entry : dir) {
          Path name = entry.getFileName();
          String value = name.toString();
          if (!value.matches("[0-9a-f-]{36}\\.(upload|media|thumb)")) continue;
          var attrs =
              dir.getFileAttributeView(
                      name,
                      java.nio.file.attribute.BasicFileAttributeView.class,
                      LinkOption.NOFOLLOW_LINKS)
                  .readAttributes();
          if (attrs.isRegularFile()
              && attrs.lastModifiedTime().toInstant().isBefore(cutoff)
              && !references.contains(value.substring(0, 36)))
            try {
              dir.deleteFile(name);
            } catch (NoSuchFileException ignored) {
            }
        }
      } catch (IOException e) {
        throw new MediaStorageException(false, e);
      }
    }
  }
}
