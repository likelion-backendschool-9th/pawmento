package com.pawmento.postcard.adapter.out.policy;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// 결제 상황에 따라 코드 변경 없이 바꾼다.
@ConfigurationProperties("pawmento.postcard")
public record PostcardLimitProperties(
    @DefaultValue("true") boolean beta,
    @DefaultValue("3") int dailyLimitPerMember,
    @DefaultValue("100") int dailyLimitTotal) {}
