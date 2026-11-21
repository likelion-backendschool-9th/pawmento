package com.pawmento.postcard.adapter.out.ai;

import com.pawmento.postcard.application.exception.PostcardGenerationFailedException;
import com.pawmento.postcard.application.port.out.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Objects;
import javax.imageio.ImageIO;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

// 외부 AI 없이 쓰는 생성기. 재료로 색을 정해 1K 가로(3:2) 수채화풍 바탕을 그린다.
// 실제 생성기를 설정하기 전(로컬, 테스트)의 기본값이다.
@Component
@ConditionalOnProperty(name = "pawmento.postcard.generator", havingValue = "fake", matchIfMissing = true)
public class FakePostcardImageAdapter implements GeneratePostcardImagePort {
  static final int WIDTH = 1248;
  static final int HEIGHT = 832;

  @Override
  public String name() {
    return "fake";
  }

  @Override
  public GeneratedPostcardImage generate(PostcardImageRequest r) {
    int seed = Objects.hash(r.content(), r.note(), r.species(), r.breed());
    var image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = image.createGraphics();
    try {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setPaint(new GradientPaint(0, 0, tint(seed, 250), 0, HEIGHT, tint(seed >>> 8, 225)));
      g.fillRect(0, 0, WIDTH, HEIGHT);
      for (int i = 0; i < 14; i++) {
        int s = seed * 31 + i * 7919;
        g.setColor(withAlpha(tint(s, 180), 60));
        int size = 160 + Math.floorMod(s, 260);
        g.fillOval(Math.floorMod(s >>> 3, WIDTH) - size / 2, Math.floorMod(s >>> 11, HEIGHT) - size / 2, size, size);
      }
    } finally {
      g.dispose();
    }
    try (var out = new ByteArrayOutputStream()) {
      ImageIO.write(image, "png", out);
      return new GeneratedPostcardImage(out.toByteArray(), "image/png");
    } catch (IOException e) {
      throw new PostcardGenerationFailedException("fake image encoding failed", e);
    }
  }

  private static Color tint(int seed, int base) {
    int spread = 255 - base;
    return new Color(
        base + Math.floorMod(seed, spread + 1),
        base + Math.floorMod(seed >>> 5, spread + 1),
        base + Math.floorMod(seed >>> 10, spread + 1));
  }

  private static Color withAlpha(Color c, int alpha) {
    return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
  }
}
