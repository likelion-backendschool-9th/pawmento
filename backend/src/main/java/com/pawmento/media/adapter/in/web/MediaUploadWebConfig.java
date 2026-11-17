package com.pawmento.media.adapter.in.web;

import com.pawmento.media.application.port.in.CheckMediaUploadUseCase;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.web.servlet.HandlerExceptionResolver;

@Configuration(proxyBeanMethods = false)
class MediaUploadWebConfig {
  @Bean
  FilterRegistrationBean<MediaUploadGuardFilter> mediaUploadGuard(
      CheckMediaUploadUseCase check,
      @Qualifier("handlerExceptionResolver") HandlerExceptionResolver errors) {
    var registration = new FilterRegistrationBean<>(new MediaUploadGuardFilter(check, errors));
    registration.setOrder(-90); // Spring Security 기본 -100 뒤에서 인증된 요청만 검사한다.
    return registration;
  }
}
