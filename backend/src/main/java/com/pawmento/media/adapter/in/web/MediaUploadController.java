package com.pawmento.media.adapter.in.web;

import com.pawmento.global.security.LoginMember;
import com.pawmento.media.adapter.in.web.exception.*;
import com.pawmento.media.application.port.in.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

@RestController
@RequestMapping("/media")
@RequiredArgsConstructor
public class MediaUploadController {
  private final CreateMediaUploadUseCase create;
  private final GetMediaUploadUseCase get;
  private final UploadMediaContentUseCase upload;
  private final GetMediaPreviewUseCase preview;
  private final DeleteMediaUploadUseCase deletion;

  @PostMapping(path = "/uploads", consumes = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  MediaUploadResponse create(
      @LoginMember Long memberId,
      @Valid @ModelAttribute MediaPetRequest pet,
      @Valid @RequestBody CreateMediaUploadRequest r) {
    boolean consent =
        r.locationConsent() != null && Boolean.TRUE.equals(r.locationConsent().granted());
    return MediaUploadResponse.from(
        create.create(
            new CreateMediaUploadCommand(
                memberId,
                pet.petId(),
                r.purpose(),
                r.filename(),
                r.mimeType(),
                r.sizeBytes(),
                consent)));
  }

  @GetMapping("/{assetId}")
  MediaUploadResponse get(
      @LoginMember Long memberId,
      @Valid @ModelAttribute MediaPetRequest pet,
      @PathVariable Long assetId) {
    return MediaUploadResponse.from(
        get.get(new GetMediaUploadQuery(memberId, pet.petId(), assetId)));
  }

  @PostMapping(path = "/{assetId}/content", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.ACCEPTED)
  MediaUploadResponse content(
      @LoginMember Long memberId,
      @Valid @ModelAttribute MediaPetRequest pet,
      @PathVariable Long assetId,
      MultipartHttpServletRequest request) {
    var files = request.getMultiFileMap();
    if (files.size() != 1
        || !files.containsKey("file")
        || files.get("file").size() != 1
        || !request.getParameterMap().keySet().equals(java.util.Set.of("petId"))
        || request.getParameterValues("petId").length != 1)
      throw new InvalidMediaRequestException(
          InvalidMediaRequestException.Reason.SINGLE_FILE_REQUIRED);
    MultipartFile file = files.get("file").getFirst();
    try (var input = new MultipartMediaContent(file)) {
      return MediaUploadResponse.from(
          upload.upload(new UploadMediaContentCommand(memberId, pet.petId(), assetId, input)));
    }
  }

  @GetMapping({"/{assetId}/preview", "/{assetId}/thumbnail"})
  ResponseEntity<org.springframework.core.io.Resource> preview(
      @LoginMember Long memberId,
      @Valid @ModelAttribute MediaPetRequest pet,
      @PathVariable Long assetId,
      jakarta.servlet.http.HttpServletRequest request) {
    boolean thumbnail = request.getRequestURI().endsWith("/thumbnail");
    var r = preview.get(new GetMediaPreviewQuery(memberId, pet.petId(), assetId, thumbnail));
    var response =
        ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(r.mimeType()))
            .header("X-Content-Type-Options", "nosniff")
            .header("Cache-Control", "private, no-store")
            .header("Accept-Ranges", "bytes")
            .header(
                "Content-Disposition",
                ContentDisposition.inline().filename(r.filename()).build().toString());
    if (request.getMethod().equals("HEAD")) return response.contentLength(r.sizeBytes()).build();
    return response.body(new MediaPreviewResource(r));
  }

  @DeleteMapping("/{assetId}")
  @ResponseStatus(HttpStatus.ACCEPTED)
  void delete(
      @LoginMember Long memberId,
      @Valid @ModelAttribute MediaPetRequest pet,
      @PathVariable Long assetId) {
    deletion.delete(new DeleteMediaUploadCommand(memberId, pet.petId(), assetId));
  }
}
