package com.pawmento.media.adapter.out.storage;

import com.pawmento.media.application.port.out.*;
import com.pawmento.media.domain.*;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class FfmpegMediaValidationAdapter implements ValidateMediaFilePort {
  private final ReadMediaFilePort input;
  private final WriteMediaFilePort output;
  private final MediaStorageProperties storage;
  private final MediaParserProperties parser;
  private final BoundedMediaProcess process;
  private final JsonMapper json;

  @Override
  public VerifiedMedia validate(MediaAsset asset, String outputKey) {
    Path work = null;
    try {
      Path parent = storage.root().toAbsolutePath().normalize().resolve("tmp/work");
      if (!Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS))
        throw new IOException("Invalid workspace");
      work =
          Files.createTempDirectory(
              parent,
              "decode-",
              PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
      Path original = work.resolve("input");
      var digest = MessageDigest.getInstance("SHA-256");
      long total = 0;
      try (var in = input.open(asset.getTemporaryStorageKey(), "upload");
          var out = Files.newOutputStream(original, StandardOpenOption.CREATE_NEW)) {
        byte[] bytes = new byte[8192];
        int n;
        while ((n = in.read(bytes)) != -1) {
          total += n;
          if (total > MediaAsset.MAX_FILE_BYTES) reject("FILE_TOO_LARGE");
          digest.update(bytes, 0, n);
          out.write(bytes, 0, n);
        }
      }
      if (total != asset.getReceivedSizeBytes()
          || !HexFormat.of().formatHex(digest.digest()).equals(asset.getSha256()))
        reject("CONTENT_CHANGED");
      String demux = signature(original, asset.getDeclaredMimeType());
      var info = probe(original, demux);
      var streams = info.path("streams");
      if (streams.size() != 1) reject("UNSUPPORTED_TRACKS");
      var stream = streams.get(0);
      boolean image = asset.getMediaType() == MediaType.IMAGE;
      String codec = stream.path("codec_name").asText();
      String expected =
          switch (asset.getDeclaredMimeType()) {
            case "image/jpeg" -> "mjpeg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "audio/webm", "audio/ogg" -> "opus";
            case "audio/mp4" -> "aac";
            case "audio/mpeg" -> "mp3";
            default -> "";
          };
      if (!expected.equals(codec)
          || !(image ? "video" : "audio").equals(stream.path("codec_type").asText()))
        reject("UNSUPPORTED_CODEC");
      if (image) checkDimensions(stream);
      else {
        double duration = duration(info);
        if (Double.isFinite(duration) && duration > 300) reject("AUDIO_TOO_LONG");
        if (stream.path("channels").asInt() > 2 || stream.path("sample_rate").asInt() > 96000)
          reject("UNSUPPORTED_AUDIO");
      }
      boolean jpeg = asset.getDeclaredMimeType().equals("image/jpeg");
      String format = image ? "image2" : "ipod";
      String mime = image ? (jpeg ? "image/jpeg" : "image/png") : "audio/mp4";
      Path service = work.resolve(image ? (jpeg ? "service.jpg" : "service.png") : "service.m4a");
      var command = decode(original, demux);
      command.addAll(
          List.of("-map", image ? "0:v:0" : "0:a:0", "-map_metadata", "-1", "-map_chapters", "-1"));
      if (image)
        command.addAll(List.of("-frames:v", "1", "-c:v", jpeg ? "mjpeg" : "png", "-update", "1"));
      else
        command.addAll(
            List.of(
                "-vn",
                "-sn",
                "-dn",
                "-c:a",
                "aac",
                "-b:a",
                "128k",
                "-ac",
                "2",
                "-ar",
                "48000",
                "-t",
                "301",
                "-movflags",
                "+faststart",
                "-metadata",
                "encoder="));
      command.addAll(
          List.of(
              "-threads",
              "1",
              "-fs",
              Long.toString(MediaAsset.MAX_FILE_BYTES + 1),
              "-f",
              format,
              service.toString()));
      process.run(command);
      requireSize(service, MediaAsset.MAX_FILE_BYTES);
      String serviceDemux = image ? (jpeg ? "jpeg_pipe" : "png_pipe") : "mov";
      var verified = probe(service, serviceDemux);
      Long durationMs = null;
      Integer width = null, height = null;
      Long thumbnailSize = null;
      if (image) {
        checkDimensions(verified.path("streams").get(0));
        width = verified.path("streams").get(0).path("width").asInt();
        height = verified.path("streams").get(0).path("height").asInt();
        Path thumbnail = work.resolve("thumbnail.jpg");
        var thumb = decode(service, serviceDemux);
        thumb.addAll(
            List.of(
                "-map_metadata",
                "-1",
                "-vf",
                "scale=512:512:force_original_aspect_ratio=decrease",
                "-frames:v",
                "1",
                "-c:v",
                "mjpeg",
                "-q:v",
                "3",
                "-threads",
                "1",
                "-update",
                "1",
                "-f",
                "image2",
                thumbnail.toString()));
        process.run(thumb);
        requireSize(thumbnail, 1024 * 1024);
        thumbnailSize = Files.size(thumbnail);
        try (var in = Files.newInputStream(thumbnail)) {
          output.write(outputKey, "thumbnail", in, 1024 * 1024);
        }
      } else {
        double seconds = duration(verified);
        if (!Double.isFinite(seconds) || seconds <= 0) reject("INVALID_DURATION");
        if (seconds > 300) reject("AUDIO_TOO_LONG");
        durationMs = (long) Math.ceil(seconds * 1000);
      }
      // Decode the entire generated file: truncated output must never be published.
      var verify = decode(service, serviceDemux);
      verify.addAll(List.of("-f", "null", "-"));
      process.run(verify);
      String checksum;
      try (var in = Files.newInputStream(service)) {
        var hash = MessageDigest.getInstance("SHA-256");
        byte[] b = new byte[8192];
        int n;
        while ((n = in.read(b)) != -1) hash.update(b, 0, n);
        checksum = HexFormat.of().formatHex(hash.digest());
      }
      try (var in = Files.newInputStream(service)) {
        output.write(outputKey, "content", in, MediaAsset.MAX_FILE_BYTES);
      }
      return new VerifiedMedia(
          outputKey, mime, Files.size(service), checksum, durationMs, width, height, thumbnailSize);
    } catch (IOException | NoSuchAlgorithmException e) {
      throw new MediaStorageException(false, e);
    } finally {
      if (work != null)
        try (var paths = Files.walk(work)) {
          for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
        } catch (IOException e) {
          throw new MediaStorageException(false, e);
        }
    }
  }

  private List<String> decode(Path file, String demux) {
    return new ArrayList<>(
        List.of(
            parser.ffmpeg(),
            "-nostdin",
            "-v",
            "error",
            "-xerror",
            "-max_alloc",
            "268435456",
            "-threads",
            "1",
            "-filter_threads",
            "1",
            "-filter_complex_threads",
            "1",
            "-protocol_whitelist",
            "file",
            "-err_detect",
            "explode",
            "-f",
            demux,
            "-i",
            file.toString()));
  }

  private JsonNode probe(Path file, String demux) {
    String text =
        process.run(
            List.of(
                parser.ffprobe(),
                "-v",
                "error",
                "-max_alloc",
                "268435456",
                "-threads",
                "1",
                "-protocol_whitelist",
                "file",
                "-f",
                demux,
                "-i",
                file.toString(),
                "-show_entries",
                "stream=codec_type,codec_name,width,height,duration,sample_rate,channels:format=duration",
                "-of",
                "json"));
    try {
      return json.readTree(text);
    } catch (RuntimeException e) {
      throw new MediaValidationException("INVALID_MEDIA");
    }
  }

  private static void checkDimensions(JsonNode stream) {
    long w = stream.path("width").asLong(), h = stream.path("height").asLong();
    if (w <= 0 || h <= 0 || w > 40000000 / h) reject("IMAGE_TOO_LARGE");
  }

  private static double duration(JsonNode info) {
    String value = info.path("format").path("duration").asText();
    if (value.isBlank()) value = info.path("streams").get(0).path("duration").asText();
    try {
      return Double.parseDouble(value);
    } catch (NumberFormatException e) {
      return Double.NaN;
    }
  }

  private static void requireSize(Path p, long max) throws IOException {
    long size = Files.size(p);
    if (size <= 0 || size > max) reject("OUTPUT_TOO_LARGE");
  }

  private static void reject(String reason) {
    throw new MediaValidationException(reason);
  }

  private static String signature(Path path, String mime) throws IOException {
    byte[] b;
    try (var in = Files.newInputStream(path)) {
      b = in.readNBytes(1024);
    }
    boolean valid = false;
    String demux = "";
    switch (mime) {
      case "image/jpeg" -> {
        valid = b.length >= 3 && (b[0] & 255) == 255 && (b[1] & 255) == 216 && (b[2] & 255) == 255;
        demux = "jpeg_pipe";
      }
      case "image/png" -> {
        valid =
            b.length >= 8
                && Arrays.equals(
                    Arrays.copyOf(b, 8), new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10});
        demux = "png_pipe";
      }
      case "image/webp" -> {
        valid = ascii(b, 0, "RIFF") && ascii(b, 8, "WEBP");
        demux = "webp_pipe";
      }
      case "audio/webm" -> {
        valid =
            b.length >= 4
                && Arrays.equals(Arrays.copyOf(b, 4), new byte[] {26, 69, (byte) 223, (byte) 163})
                && new String(b, java.nio.charset.StandardCharsets.ISO_8859_1).contains("webm");
        demux = "matroska";
      }
      case "audio/ogg" -> {
        valid = ascii(b, 0, "OggS");
        demux = "ogg";
      }
      case "audio/mp4" -> {
        valid = ascii(b, 4, "ftyp");
        demux = "mov";
      }
      case "audio/mpeg" -> {
        valid = ascii(b, 0, "ID3") || (b.length >= 2 && (b[0] & 255) == 255 && (b[1] & 224) == 224);
        demux = "mp3";
      }
    }
    if (!valid) reject("MIME_MISMATCH");
    return demux;
  }

  private static boolean ascii(byte[] b, int at, String text) {
    return b.length >= at + text.length()
        && new String(b, at, text.length(), java.nio.charset.StandardCharsets.US_ASCII)
            .equals(text);
  }
}
