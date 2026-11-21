package com.pawmento.postcard.adapter.out.storage;

import com.pawmento.postcard.application.exception.PostcardStorageUnavailableException;
import com.pawmento.postcard.application.port.out.StorePostcardFilePort;
import java.io.IOException;
import java.nio.file.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

// 파일 이름에 엽서 ID를 넣어 정리 작업이 DB와 대조할 수 있게 한다.
// 같은 폴더의 *.part로 쓴 뒤 이름을 바꿔 쓰다 만 파일이 보이지 않게 한다.
@Component
@RequiredArgsConstructor
@Slf4j
@EnableConfigurationProperties(PostcardStorageProperties.class)
public class VolumePostcardFileAdapter implements StorePostcardFilePort {
  static final String SOURCES = "sources";
  static final String IMAGES = "images";

  private final PostcardStorageProperties properties;

  @Override
  public String storeSourcePhoto(Long postcardId, byte[] jpeg) {
    String key = postcardId + ".jpg";
    write(SOURCES, key, jpeg);
    return key;
  }

  @Override
  public byte[] readSourcePhoto(String key) {
    try {
      return Files.readAllBytes(properties.imageRoot().resolve(SOURCES).resolve(safe(key)));
    } catch (IOException e) {
      throw new PostcardStorageUnavailableException(e);
    }
  }

  @Override
  public void deleteSourcePhoto(String key) {
    delete(SOURCES, key);
  }

  @Override
  public String storeImage(Long postcardId, byte[] bytes, String contentType) {
    String key = postcardId + "." + extension(contentType);
    write(IMAGES, key, bytes);
    return key;
  }

  @Override
  public byte[] readImage(String key) {
    try {
      return Files.readAllBytes(properties.imageRoot().resolve(IMAGES).resolve(safe(key)));
    } catch (IOException e) {
      throw new PostcardStorageUnavailableException(e);
    }
  }

  @Override
  public void deleteImage(String key) {
    delete(IMAGES, key);
  }

  // 지우지 못한 파일은 정리 작업이 다시 지운다. 정상 경로의 응답을 막지 않는다.
  void delete(String folder, String key) {
    try {
      Files.deleteIfExists(properties.imageRoot().resolve(folder).resolve(safe(key)));
    } catch (IOException e) {
      log.warn("Postcard file {}/{} was not deleted", folder, key);
    }
  }

  static String extension(String contentType) {
    return switch (contentType) {
      case "image/png" -> "png";
      case "image/jpeg" -> "jpg";
      case "image/webp" -> "webp";
      default -> throw new PostcardStorageUnavailableException(null);
    };
  }

  // 파일 이름은 "<엽서 ID>.<확장자>"뿐이다. 다른 경로로 빠져나가지 않게 막는다.
  static String safe(String key) {
    if (key == null || !key.matches("[0-9]{1,19}\\.(png|jpg|webp)"))
      throw new PostcardStorageUnavailableException(null);
    return key;
  }

  void write(String folder, String key, byte[] bytes) {
    try {
      Path dir = Files.createDirectories(properties.imageRoot().resolve(folder));
      Path target = dir.resolve(key);
      Path part = dir.resolve(key + ".part");
      Files.write(part, bytes);
      Files.move(part, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      throw new PostcardStorageUnavailableException(e);
    }
  }
}
