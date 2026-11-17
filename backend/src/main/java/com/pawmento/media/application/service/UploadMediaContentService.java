package com.pawmento.media.application.service;

import com.pawmento.media.application.port.in.*;
import com.pawmento.media.application.port.out.*;
import com.pawmento.media.domain.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 파일 I/O 전체를 DB 트랜잭션으로 감싸지 않는다. 상태 전이는 별도 빈에서 처리한다.
@Service
@RequiredArgsConstructor
public class UploadMediaContentService implements UploadMediaContentUseCase {
  private final MediaUploadTransactions transactions;
  private final StoreMediaUploadPort storage;
  private final DeleteMediaUploadPort deletion;

  @Override
  public MediaUploadResult upload(UploadMediaContentCommand c) {
    MediaAsset asset = transactions.claim(c.memberId(), c.petId(), c.assetId());
    try {
      StoredUpload receipt =
          storage.store(asset.getTemporaryStorageKey(), c.content(), MediaAsset.MAX_FILE_BYTES);
      return transactions.complete(c.memberId(), c.petId(), c.assetId(), receipt);
    } catch (RuntimeException failure) {
      try {
        // 커밋 결과가 불명확하거나 이미 PROCESSING이면 파일을 지우지 않는다.
        if (transactions.reject(c.assetId())) {
          deletion.delete(asset.getTemporaryStorageKey());
          transactions.cleaned(c.assetId());
        }
      } catch (RuntimeException cleanup) {
        failure.addSuppressed(cleanup);
      }
      if (failure instanceof MediaStorageException e)
        throw new MediaUploadException(
            e.tooLarge() ? MediaUploadException.Kind.TOO_LARGE : MediaUploadException.Kind.STORAGE,
            e.tooLarge() ? "파일은 15MiB까지 업로드할 수 있어요." : "파일을 저장하지 못했어요. 잠시 후 다시 시도해 주세요.");
      throw failure;
    }
  }
}
