package com.pawmento.media.adapter.out.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({MediaStorageProperties.class, MediaParserProperties.class})
class MediaStorageConfig {}
