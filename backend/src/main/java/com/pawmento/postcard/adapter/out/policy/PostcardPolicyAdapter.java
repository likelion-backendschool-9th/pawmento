package com.pawmento.postcard.adapter.out.policy;

import com.pawmento.postcard.application.port.out.LoadPostcardPolicyPort;
import com.pawmento.postcard.domain.PostcardPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(PostcardLimitProperties.class)
public class PostcardPolicyAdapter implements LoadPostcardPolicyPort {
  private final PostcardLimitProperties properties;

  @Override
  public PostcardPolicy load() {
    return new PostcardPolicy(
        properties.beta(), properties.dailyLimitPerMember(), properties.dailyLimitTotal());
  }
}
