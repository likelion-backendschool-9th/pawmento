package com.pawmento.media.application.port.in;

import java.util.List;

public interface LoadMediaAttachmentsUseCase {
  List<MediaAttachmentMetadata> load(LoadMediaAttachmentsQuery query);
}
