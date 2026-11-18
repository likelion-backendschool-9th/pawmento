package com.pawmento.media.adapter.out.storage;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("pawmento.media")
public record MediaStorageProperties(@DefaultValue(".local/media") Path root) {}
