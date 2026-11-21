package com.pawmento.postcard.adapter.out.storage;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// media 정리 작업은 tmp/ 아래만 지우므로 media 볼륨 아래 postcards/를 같이 써도 겹치지 않는다.
@ConfigurationProperties("pawmento.postcard")
public record PostcardStorageProperties(
    @DefaultValue(".local/media/postcards") Path imageRoot,
    @DefaultValue("1h") Duration cleanupGrace) {}
