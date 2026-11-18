package com.pawmento.media.adapter.out.storage;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("pawmento.media.lifecycle")
public record MediaLifecycleProperties(
    @DefaultValue("10m") Duration sessionTtl,
    @DefaultValue("24h") Duration unattachedTtl,
    @DefaultValue("15m") Duration uploadTimeout,
    @DefaultValue("10m") Duration workLease,
    @DefaultValue("5") int maxOpenUploads) {}
