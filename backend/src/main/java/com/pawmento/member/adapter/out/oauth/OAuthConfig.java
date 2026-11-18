package com.pawmento.member.adapter.out.oauth;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;


@Configuration (proxyBeanMethods = false) 
@EnableConfigurationProperties(GoogleOAuthProperties.class)
public class OAuthConfig {

    
}
