package com.example.pstarchive.fingerprint;

import com.example.pstarchive.model.PstFingerprint;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class PstFingerprintService {
    private static final int ONE_MB = 1024 * 1024;

    public PstFingerprint calculate(Path file) throws IOException {
        if (!Files.exists(file)) {
            throw new IOException("PST file does not exist: " + file);
        }
        if (!Files.isRegularFile(file)) {
            throw new IOException("Path is not a regular file: " + file);
        }

        long size = Files.size(file);
        long mtime = Files.getLastModifiedTime(file).toMillis();
        String firstHash = hashSlice(file, 0, Math.min(size, ONE_MB));
        long lastOffset = Math.max(0, size - ONE_MB);
        String lastHash = hashSlice(file, lastOffset, size - lastOffset);
        String fingerprint = sha256Hex(size + "|" + mtime + "|" + firstHash + "|" + lastHash);
        return new PstFingerprint(size, mtime, firstHash, lastHash, fingerprint);
    }

    private String hashSlice(Path file, long offset, long length) throws IOException {
        MessageDigest digest = sha256();
        byte[] buffer = new byte[8192];
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            raf.seek(offset);
            long remaining = length;
            while (remaining > 0) {
                int toRead = (int) Math.min(buffer.length, remaining);
                int read = raf.read(buffer, 0, toRead);
                if (read == -1) {
                    break;
                }
                digest.update(buffer, 0, read);
                remaining -= read;
            }
        }
        return toHex(digest.digest());
    }

    private String sha256Hex(String value) {
        MessageDigest digest = sha256();
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        return toHex(digest.digest());
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }
}
