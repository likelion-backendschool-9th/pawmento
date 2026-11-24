package com.pawmento.postcard.adapter.out.ai;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Cloudflare Workers AI 이미지 생성 설정 (application.yml의 pawmento.postcard.cloudflare).
 *
 * @param accountId Cloudflare 계정 ID. 환경 변수 CLOUDFLARE_ACCOUNT_ID로 받는다
 * @param apiToken Workers AI 권한만 준 API 토큰. 환경 변수 CLOUDFLARE_AI_TOKEN으로만 받는다. 저장소에 두지 않는다
 * @param model 모델 ID. S7에서 9b와 비교해 4b로 정했다(사진 닮기, 속도, 비용)
 * @param baseUrl 호출 주소
 * @param connectTimeout 연결 제한 시간
 * @param readTimeout 응답 제한 시간. 이 시간이 지나면 실패로 처리한다
 */
@ConfigurationProperties("pawmento.postcard.cloudflare")
public record CloudflareImageProperties(
    @DefaultValue("") String accountId,
    @DefaultValue("") String apiToken,
    @DefaultValue("@cf/black-forest-labs/flux-2-klein-4b") String model,
    @DefaultValue("https://api.cloudflare.com/client/v4") String baseUrl,
    @DefaultValue("5s") Duration connectTimeout,
    @DefaultValue("60s") Duration readTimeout) {

  boolean hasCredentials() {
    return !accountId.isBlank() && !apiToken.isBlank();
  }
}
