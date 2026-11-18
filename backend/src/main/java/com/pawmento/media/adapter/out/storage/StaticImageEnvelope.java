package com.pawmento.media.adapter.out.storage;

import com.pawmento.media.application.exception.MediaValidationFailedException;
import java.io.*;
import java.nio.file.*;
import java.util.zip.CRC32;

final class StaticImageEnvelope {
  private StaticImageEnvelope() {}

  static void check(Path file, String mime) throws IOException {
    long size = Files.size(file);
    if (mime.equals("image/jpeg")) {
      try (var channel = java.nio.channels.FileChannel.open(file)) {
        if (size < 4) reject();
        channel.position(size - 2);
        var end = java.nio.ByteBuffer.allocate(2);
        while (end.hasRemaining()) if (channel.read(end) < 0) reject();
        if (end.get(0) != (byte) 255 || end.get(1) != (byte) 217) reject();
      }
    } else if (mime.equals("image/png")) checkPng(file, size);
    else checkWebp(file, size);
  }

  private static void checkPng(Path file, long size) throws IOException {
    try (var in = new DataInputStream(Files.newInputStream(file))) {
      in.skipNBytes(8);
      long offset = 8;
      boolean first = true;
      while (offset < size) {
        long length = Integer.toUnsignedLong(in.readInt());
        byte[] type = in.readNBytes(4);
        if (type.length != 4 || length > size - offset - 12) reject();
        String name = new String(type, java.nio.charset.StandardCharsets.US_ASCII);
        if (first && (!name.equals("IHDR") || length != 13)) reject();
        first = false;
        if (name.equals("acTL") || name.equals("fcTL") || name.equals("fdAT")) reject();
        var crc = new CRC32();
        crc.update(type);
        long left = length;
        byte[] buffer = new byte[8192];
        while (left > 0) {
          int n = in.read(buffer, 0, (int) Math.min(left, buffer.length));
          if (n < 0) reject();
          crc.update(buffer, 0, n);
          left -= n;
        }
        if (crc.getValue() != Integer.toUnsignedLong(in.readInt())) reject();
        offset += length + 12;
        if (name.equals("IEND")) {
          if (length != 0 || offset != size) reject();
          return;
        }
      }
      reject();
    } catch (EOFException e) {
      reject();
    }
  }

  private static void checkWebp(Path file, long size) throws IOException {
    try (var in = new DataInputStream(Files.newInputStream(file))) {
      in.skipNBytes(4);
      long declared = Integer.toUnsignedLong(Integer.reverseBytes(in.readInt()));
      in.skipNBytes(4);
      if (declared + 8 != size) reject();
      long offset = 12;
      while (offset < size) {
        byte[] type = in.readNBytes(4);
        if (type.length != 4) reject();
        String name = new String(type, java.nio.charset.StandardCharsets.US_ASCII);
        long length = Integer.toUnsignedLong(Integer.reverseBytes(in.readInt()));
        long padded = length + (length & 1);
        if (padded > size - offset - 8 || name.equals("ANIM") || name.equals("ANMF")) reject();
        in.skipNBytes(padded);
        offset += 8 + padded;
      }
    } catch (EOFException e) {
      reject();
    }
  }

  private static void reject() {
    throw new MediaValidationFailedException("INVALID_STATIC_IMAGE");
  }
}
