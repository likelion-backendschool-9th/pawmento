package com.pawmento.postcard.adapter.out.ai;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.pawmento.postcard.application.exception.PostcardGenerationFailedException;
import com.pawmento.postcard.application.port.out.GeneratePostcardImagePort;
import com.pawmento.postcard.application.port.out.GeneratedPostcardImage;
import com.pawmento.postcard.application.port.out.PostcardImageRequest;
import java.net.http.HttpClient;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

// Cloudflare Workers AI의 FLUX.2 klein으로 엽서 그림을 만든다 (docs/postcard/generate.md).
// 요청은 multipart(prompt, width, height, 참조 사진 input_image_0)이고, 응답은 base64 image다.
// 실패는 PostcardGenerationFailedException으로 바꾸고, 메시지에 토큰이나 응답 본문을 넣지 않는다.
@Component
@ConditionalOnProperty(name = "pawmento.postcard.generator", havingValue = "cloudflare")
@EnableConfigurationProperties(CloudflareImageProperties.class)
public class CloudflarePostcardImageAdapter implements GeneratePostcardImagePort {
  // 1K 가로(3:2). 모델이 받는 범위(256~1920) 안이고 16의 배수다
  static final int WIDTH = 1248;
  static final int HEIGHT = 832;
  // 프롬프트가 지나치게 길어지지 않게 일기 글을 자른다
  static final int MAX_CONTENT_LENGTH = 1000;

  private static final ObjectMapper JSON = new ObjectMapper();

  private final CloudflareImageProperties properties;
  private final RestClient restClient;

  @Autowired
  CloudflarePostcardImageAdapter(CloudflareImageProperties properties) {
    this(properties, builderWithTimeouts(properties));
  }

  // 테스트에서 가짜 서버를 연결한 builder를 넣을 수 있게 한다
  CloudflarePostcardImageAdapter(CloudflareImageProperties properties, RestClient.Builder builder) {
    // 생성기를 cloudflare로 정했는데 키가 없으면 요청마다 실패하므로 시작할 때 알린다
    if (!properties.hasCredentials())
      throw new IllegalStateException(
          "pawmento.postcard.generator=cloudflare에는 CLOUDFLARE_ACCOUNT_ID와 CLOUDFLARE_AI_TOKEN이 필요합니다.");
    this.properties = properties;
    this.restClient = builder.baseUrl(properties.baseUrl()).build();
  }

  private static RestClient.Builder builderWithTimeouts(CloudflareImageProperties properties) {
    HttpClient httpClient =
        HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
    var requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(properties.readTimeout());
    return RestClient.builder().requestFactory(requestFactory);
  }

  @Override
  public String name() {
    return "cloudflare";
  }

  @Override
  public GeneratedPostcardImage generate(PostcardImageRequest request) {
    var form = new LinkedMultiValueMap<String, Object>();
    form.add("prompt", prompt(request));
    form.add("width", String.valueOf(WIDTH));
    form.add("height", String.valueOf(HEIGHT));
    if (request.photoJpeg() != null) form.add("input_image_0", jpegPart(request.photoJpeg()));

    String body;
    try {
      body =
          restClient
              .post()
              // 모델 ID의 "/"가 %2F로 바뀌지 않게 변수 없이 경로를 만든다
              .uri("/accounts/" + properties.accountId() + "/ai/run/" + properties.model())
              .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiToken())
              .contentType(MediaType.MULTIPART_FORM_DATA)
              .body(form)
              .retrieve()
              .body(String.class);
    } catch (RestClientResponseException e) {
      // 상태와 Cloudflare 오류 번호만 남긴다(3036: 하루 무료 한도 소진 등)
      throw new PostcardGenerationFailedException(
          "cloudflare status " + e.getStatusCode().value() + errorCode(e.getResponseBodyAsString()));
    } catch (RestClientException e) {
      throw new PostcardGenerationFailedException(
          "cloudflare call failed: " + e.getClass().getSimpleName(), e);
    }
    return toImage(body);
  }

  // 프롬프트는 영어 지시문에 사용자의 글을 따옴표로 넣는다. 글자·워터마크가 그림에 들어가지 않게 한다.
  static String prompt(PostcardImageRequest r) {
    var subject = new StringBuilder(animal(r.species()));
    if (r.breed() != null && !r.breed().isBlank()) subject.append(" (breed: ").append(r.breed().strip()).append(")");
    var p = new StringBuilder();
    p.append("A soft hand-painted watercolor illustration for a landscape postcard. ");
    p.append("The main subject is a ").append(subject).append(". ");
    if (r.photoJpeg() != null)
      p.append("Make the animal look like the pet in image 0: same fur color, markings and face. ")
          .append("Do not copy the photo's background. ");
    if (r.content() != null && !r.content().isBlank())
      p.append("Paint this moment from the owner's diary: \"").append(clip(r.content())).append("\". ");
    if (r.note() != null && !r.note().isBlank())
      p.append("More about the moment: \"").append(clip(r.note())).append("\". ");
    p.append("Warm gentle light, paper texture, cozy and heartfelt mood. ");
    p.append("No text, no letters, no watermark, no border.");
    return p.toString();
  }

  private static String animal(String species) {
    if ("DOG".equals(species)) return "dog";
    if ("CAT".equals(species)) return "cat";
    return "pet animal";
  }

  private static String clip(String text) {
    String s = text.strip().replace('"', '\'');
    return s.length() <= MAX_CONTENT_LENGTH ? s : s.substring(0, MAX_CONTENT_LENGTH);
  }

  // 파일 이름이 있어야 multipart에서 파일 부분으로 보낸다
  private static Object jpegPart(byte[] jpeg) {
    var resource =
        new ByteArrayResource(jpeg) {
          @Override
          public String getFilename() {
            return "photo.jpg";
          }
        };
    var headers = new HttpHeaders();
    headers.setContentType(MediaType.IMAGE_JPEG);
    return new HttpEntity<>(resource, headers);
  }

  // REST 응답은 {"result":{"image":"<base64>"},"success":true}다. 봉투 없이 image만 와도 받는다
  static GeneratedPostcardImage toImage(String body) {
    JsonNode root;
    try {
      root = JSON.readTree(body == null ? "" : body);
    } catch (Exception e) {
      throw new PostcardGenerationFailedException("cloudflare response is not json");
    }
    if (root == null) throw new PostcardGenerationFailedException("cloudflare response is empty");
    JsonNode image = root.path("result").path("image");
    if (!image.isString()) image = root.path("image");
    if (!image.isString() || image.asString().isBlank())
      throw new PostcardGenerationFailedException("cloudflare response has no image" + errorCode(body));
    byte[] bytes;
    try {
      bytes = Base64.getDecoder().decode(stripDataUrl(image.asString()));
    } catch (IllegalArgumentException e) {
      throw new PostcardGenerationFailedException("cloudflare image is not base64");
    }
    String contentType = contentType(bytes);
    if (contentType == null)
      throw new PostcardGenerationFailedException("cloudflare image format is unknown");
    return new GeneratedPostcardImage(bytes, contentType);
  }

  private static String stripDataUrl(String value) {
    int comma = value.indexOf(',');
    return value.startsWith("data:") && comma > 0 ? value.substring(comma + 1) : value.strip();
  }

  // 모델이 어떤 형식으로 주는지 문서에 없어 첫 바이트로 정한다
  static String contentType(byte[] b) {
    if (b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G')
      return "image/png";
    if (b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF)
      return "image/jpeg";
    if (b.length > 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
        && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P')
      return "image/webp";
    return null;
  }

  // 오류 응답 {"errors":[{"code":3036,"message":"..."}]}에서 번호만 꺼낸다
  private static String errorCode(String body) {
    try {
      JsonNode code = JSON.readTree(body == null ? "" : body).path("errors").path(0).path("code");
      return code.isNumber() ? " code " + code.asInt() : "";
    } catch (Exception e) {
      return "";
    }
  }
}
