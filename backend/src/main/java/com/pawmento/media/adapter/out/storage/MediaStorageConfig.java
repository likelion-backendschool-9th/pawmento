package com.pawmento.media.adapter.out.storage;

import com.pawmento.media.domain.MediaPolicy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
  MediaStorageProperties.class,
  MediaParserProperties.class,
  MediaLifecycleProperties.class
})
class MediaStorageConfig {
  @Bean
  MediaPolicy mediaPolicy(MediaLifecycleProperties p) {
    return new MediaPolicy(
        p.sessionTtl(), p.unattachedTtl(), p.uploadTimeout(), p.workLease(), p.maxOpenUploads());
  }
}
