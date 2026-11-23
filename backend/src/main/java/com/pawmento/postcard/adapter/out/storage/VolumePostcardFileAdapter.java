package com.pawmento.postcard.adapter.out.storage;

import com.pawmento.postcard.application.exception.PostcardStorageUnavailableException;
import com.pawmento.postcard.application.port.out.StorePostcardFilePort;
import java.io.IOException;
import java.nio.file.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

// 파일 이름에 엽서 ID를 넣어 정리 작업이 DB와 대조할 수 있게 한다.
// 같은 폴더의 *.part로 쓴 뒤 이름을 바꿔 쓰다 만 파일이 보이지 않게 한다.
@Component
@RequiredArgsConstructor
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
