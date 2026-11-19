package com.pawmento.media.application.port.out;

import java.util.List;

public interface LoadMediaOwnershipPort {
  List<MediaOwnership> loadOwnership(List<Long> assetIds);
}
