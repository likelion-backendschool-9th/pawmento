package com.pawmento.postcard.application.port.out;

import java.time.Instant;
import java.util.List;

public interface CollectPostcardFilesPort {
  // 유예 시간이 지난 파일만 돌려준다. 저장 직후 DB 반영을 기다리는 파일을 지우지 않기 위해서다.
  // 저장 폴더(image-root)의 images/, sources/ 바로 아래만 본다.
  List<PostcardFile> settledFiles(Instant now);

  void delete(PostcardFile file);
}
