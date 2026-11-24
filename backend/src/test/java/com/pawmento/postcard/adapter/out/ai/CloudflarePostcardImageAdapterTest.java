package com.pawmento.postcard.adapter.out.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.pawmento.postcard.application.exception.PostcardGenerationFailedException;
import com.pawmento.postcard.application.port.out.PostcardImageRequest;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import javax.imageio.ImageIO;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.pawmento.postcard.application.port.out.GeneratePostcardImagePort;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

// 시나리오는 docs/postcard/generate.md에 있다. #103-S7은 실제 서비스로 직접 확인하고,
// 이 파일은 그중 요청 형식과 실패 변환을 가짜 서버로 확인한다. 진짜 토큰으로 하는 호출은 넣지 않는다
class CloudflarePostcardImageAdapterTest {
  private static final String BASE_URL = "http://cloudflare.test";
  private static final String TOKEN = "secret-token";
  private static final String URL =
      BASE_URL + "/accounts/acc-1/ai/run/@cf/black-forest-labs/flux-2-klein-9b";
  private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};
  private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2};

  private static CloudflareImageProperties properties(String accountId, String token) {
    return new CloudflareImageProperties(
        accountId,
        token,
        "@cf/black-forest-labs/flux-2-klein-9b",
        BASE_URL,
        Duration.ofSeconds(1),
        Duration.ofSeconds(1));
  }

  private record Fixture(CloudflarePostcardImageAdapter adapter, MockRestServiceServer server) {}

  private static Fixture fixture() {
    var builder = RestClient.builder();
    var server = MockRestServiceServer.bindTo(builder).build();
    return new Fixture(new CloudflarePostcardImageAdapter(properties("acc-1", TOKEN), builder), server);
  }

  private static String imageJson(byte[] bytes) {
    return "{\"result\":{\"image\":\"" + Base64.getEncoder().encodeToString(bytes)
        + "\"},\"success\":true,\"errors\":[],\"messages\":[]}";
  }

  @Test
  @DisplayName("#103-S7 사진이 있으면 글·설명·사진을 1K 가로 크기로 보내고 받은 PNG를 돌려준다")
  void sendsPhotoAndReturnsPng() {
    var f = fixture();
    f.server()
        .expect(requestTo(URL))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Authorization", "Bearer " + TOKEN))
        .andExpect(header("Content-Type", containsString("multipart/form-data")))
        .andExpect(
            content()
                .string(
                    allOf(
                        containsString("name=\"prompt\""),
                        containsString("바닷가에서 노을을 보며 산책했다"),
                        containsString("빨간 목줄"),
                        containsString("image 0"),
                        containsString("name=\"width\""),
                        containsString("1248"),
                        containsString("name=\"height\""),
                        containsString("832"),
                        containsString("name=\"input_image_0\""),
                        containsString("Content-Type: image/jpeg"))))
        .andRespond(withSuccess(imageJson(PNG), MediaType.APPLICATION_JSON));

    var image =
        f.adapter()
            .generate(new PostcardImageRequest("바닷가에서 노을을 보며 산책했다", "빨간 목줄", "DOG", "말티즈", JPEG));

    assertThat(image.contentType()).isEqualTo("image/png");
    assertThat(image.bytes()).isEqualTo(PNG);
    f.server().verify();
  }

  @Test
  @DisplayName("#103-S7 사진이 없으면 참조 사진 없이 종·품종으로 그리게 하고, JPEG 응답은 형식을 그대로 알린다")
  void withoutPhotoUsesBreed() {
    var f = fixture();
    f.server()
        .expect(requestTo(URL))
        .andExpect(
            content()
                .string(
                    allOf(
                        containsString("cat (breed: 코리안 숏헤어)"),
                        not(containsString("input_image_0")),
                        not(containsString("image 0")))))
        .andRespond(withSuccess(imageJson(JPEG), MediaType.APPLICATION_JSON));

    var image = f.adapter().generate(new PostcardImageRequest(null, "창가에서 낮잠", "CAT", "코리안 숏헤어", null));

    assertThat(image.contentType()).isEqualTo("image/jpeg");
    f.server().verify();
  }

  @Test
  @DisplayName("#103-S7 한도 소진 같은 오류 응답은 상태와 오류 번호만 담은 생성 실패로 바꾼다")
  void errorResponseBecomesFailureWithoutSecrets() {
    var f = fixture();
    f.server()
        .expect(requestTo(URL))
        .andRespond(
            withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"success\":false,\"errors\":[{\"code\":3036,\"message\":\"daily free allocation used up\"}]}"));

    assertThatThrownBy(() -> f.adapter().generate(new PostcardImageRequest("산책", null, "DOG", null, null)))
        .isInstanceOf(PostcardGenerationFailedException.class)
        .hasMessageContaining("429")
        .hasMessageContaining("3036")
        .hasMessageNotContaining(TOKEN)
        .hasMessageNotContaining("allocation");
  }

  @Test
  @DisplayName("#103-S7 이미지가 없거나 알 수 없는 형식이면 생성 실패로 바꾼다")
  void missingOrUnknownImageFails() {
    var f = fixture();
    f.server()
        .expect(requestTo(URL))
        .andRespond(withSuccess("{\"result\":{},\"success\":true}", MediaType.APPLICATION_JSON));
    f.server()
        .expect(requestTo(URL))
        .andRespond(withSuccess(imageJson(new byte[] {1, 2, 3, 4, 5}), MediaType.APPLICATION_JSON));
    var request = new PostcardImageRequest("산책", null, "DOG", null, null);

    assertThatThrownBy(() -> f.adapter().generate(request))
        .isInstanceOf(PostcardGenerationFailedException.class)
        .hasMessageContaining("no image");
    assertThatThrownBy(() -> f.adapter().generate(request))
        .isInstanceOf(PostcardGenerationFailedException.class)
        .hasMessageContaining("format");
  }

  @Test
  @DisplayName("#103-S7 생성기를 cloudflare로 정했는데 계정 ID나 토큰이 없으면 시작할 때 막는다")
  void missingCredentialsFailAtStartup() {
    assertThatThrownBy(() -> new CloudflarePostcardImageAdapter(properties("", TOKEN), RestClient.builder()))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> new CloudflarePostcardImageAdapter(properties("acc-1", ""), RestClient.builder()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageNotContaining(TOKEN);
  }

  @Test
  @DisplayName("#103-S7 설정 generator=cloudflare이면 가짜 대신 Cloudflare 생성기를 쓴다")
  void generatorSettingPicksCloudflare() {
    new ApplicationContextRunner()
        .withUserConfiguration(FakePostcardImageAdapter.class, CloudflarePostcardImageAdapter.class)
        .withPropertyValues(
            "pawmento.postcard.generator=cloudflare",
            "pawmento.postcard.cloudflare.account-id=acc-1",
            "pawmento.postcard.cloudflare.api-token=" + TOKEN)
        .run(
            context ->
                assertThat(context.getBean(GeneratePostcardImagePort.class).name()).isEqualTo("cloudflare"));
  }

  private static byte[] jpeg(int width, int height) throws Exception {
    var out = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "jpg", out);
    return out.toByteArray();
  }

  @Test
  @DisplayName("#103-S7 참조 사진은 512x512보다 작게 줄여 보내고, 이미 작은 사진은 그대로 보낸다")
  void photoIsShrunkBelow512() throws Exception {
    var square = ImageIO.read(new ByteArrayInputStream(CloudflarePostcardImageAdapter.fitPhoto(jpeg(512, 512))));
    assertThat(square.getWidth()).isLessThan(512);
    assertThat(square.getHeight()).isLessThan(512);

    var wide = ImageIO.read(new ByteArrayInputStream(CloudflarePostcardImageAdapter.fitPhoto(jpeg(512, 384))));
    assertThat(wide.getWidth()).isEqualTo(511);
    assertThat(wide.getHeight()).isEqualTo(383);

    byte[] small = jpeg(400, 300);
    assertThat(CloudflarePostcardImageAdapter.fitPhoto(small)).isSameAs(small);
  }

  @Test
  @DisplayName("#103-S7 긴 일기 글은 프롬프트에서 자르되 두 칸 문자를 반으로 나누지 않는다")
  void longContentIsClippedSafely() {
    String content = "a".repeat(CloudflarePostcardImageAdapter.MAX_CONTENT_LENGTH - 1) + "🐶" + "b".repeat(10);
    String prompt =
        CloudflarePostcardImageAdapter.prompt(new PostcardImageRequest(content, null, "DOG", null, null));

    assertThat(prompt).doesNotContain("bbb").contains("aaa\"");
    assertThat(prompt.chars().filter(c -> Character.isSurrogate((char) c)).count()).isZero();
  }
}
