package com.pawmento.postcard.adapter.out.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.pawmento.postcard.application.port.out.PostcardImageRequest;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FakePostcardImageAdapterTest {
  @Test
  @DisplayName("#103-S4 가짜 생성기는 외부 호출 없이 1K 가로(3:2) PNG를 만든다")
  void drawsLandscapePng() throws Exception {
    var image =
        new FakePostcardImageAdapter()
            .generate(new PostcardImageRequest("바닷가 산책", null, "DOG", "말티즈", null));

    assertThat(image.contentType()).isEqualTo("image/png");
    var decoded = ImageIO.read(new ByteArrayInputStream(image.bytes()));
    assertThat(decoded.getWidth()).isEqualTo(1248);
    assertThat(decoded.getHeight()).isEqualTo(832);
  }
}
