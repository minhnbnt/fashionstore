package com.minhnbnt.fashionstore.infrastructure.media;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.OptionalDouble;

/**
 * Phân tích thời lượng video chuẩn ISO BMFF (MP4, M4V, MOV) bằng cách đọc
 * Movie Header Box (`moov` -> `mvhd`) trực tiếp từ luồng byte mà không cần
 * tải toàn bộ thân video dung lượng lớn.
 */
@Slf4j
public final class Mp4DurationParser {

    private Mp4DurationParser() {
    }

    /**
     * Đọc thời lượng (tính bằng giây) từ InputStream của file MP4.
     *
     * @param inputStream Luồng byte của video (từ S3 hoặc file)
     * @return Thời lượng tính theo giây, hoặc empty nếu không tìm thấy atom mvhd
     */
    public static OptionalDouble parseDurationSeconds(@NonNull InputStream inputStream) {
        var dis = new DataInputStream(inputStream);
        try {
            return searchBox(dis, -1);
        } catch (EOFException e) {
            log.debug("Đạt cuối luồng MP4 trước khi tìm thấy mvhd");
            return OptionalDouble.empty();
        } catch (Exception e) {
            log.warn("Lỗi khi phân tích thời lượng MP4: {}", e.getMessage());
            return OptionalDouble.empty();
        }
    }

    private static OptionalDouble searchBox(DataInputStream dis, long limit) throws IOException {
        long bytesRead = 0;
        while (limit < 0 || bytesRead + 8 <= limit) {
            int sizeInt;
            try {
                sizeInt = dis.readInt();
            } catch (EOFException e) {
                break;
            }
            bytesRead += 4;

            byte[] typeBytes = new byte[4];
            dis.readFully(typeBytes);
            bytesRead += 4;
            String type = new String(typeBytes, StandardCharsets.ISO_8859_1);

            long boxSize = Integer.toUnsignedLong(sizeInt);
            long headerSize = 8;
            if (boxSize == 1) {
                // 64-bit largesize
                long largeSize = dis.readLong();
                bytesRead += 8;
                boxSize = largeSize;
                headerSize = 16;
            } else if (boxSize == 0) {
                // Box kéo dài đến hết file
                boxSize = limit > 0 ? (limit - bytesRead + headerSize) : Long.MAX_VALUE;
            }

            long payloadSize = boxSize - headerSize;

            if ("moov".equals(type)) {
                // moov là container chứa mvhd
                var result = searchBox(dis, payloadSize);
                if (result.isPresent()) {
                    return result;
                }
                bytesRead += payloadSize;
            } else if ("mvhd".equals(type)) {
                return parseMvhd(dis, payloadSize);
            } else {
                // Bỏ qua payload của các box khác (ví dụ ftyp, mdat, free)
                skipFully(dis, payloadSize);
                bytesRead += payloadSize;
            }
        }
        return OptionalDouble.empty();
    }

    private static OptionalDouble parseMvhd(DataInputStream dis, long payloadSize) throws IOException {
        if (payloadSize < 20) {
            skipFully(dis, payloadSize);
            return OptionalDouble.empty();
        }

        int version = dis.readUnsignedByte();
        // Bỏ qua 3 bytes flags
        skipFully(dis, 3);

        long timescale;
        long duration;

        if (version == 1) {
            // Version 1: 64-bit timestamps
            // creation_time (8) + modification_time (8)
            skipFully(dis, 16);
            timescale = Integer.toUnsignedLong(dis.readInt());
            duration = dis.readLong();
            skipFully(dis, payloadSize - 1 - 3 - 16 - 4 - 8);
        } else {
            // Version 0: 32-bit timestamps
            // creation_time (4) + modification_time (4)
            skipFully(dis, 8);
            timescale = Integer.toUnsignedLong(dis.readInt());
            duration = Integer.toUnsignedLong(dis.readInt());
            skipFully(dis, payloadSize - 1 - 3 - 8 - 4 - 4);
        }

        if (timescale <= 0 || duration <= 0) {
            return OptionalDouble.empty();
        }

        double durationSeconds = (double) duration / timescale;
        return OptionalDouble.of(durationSeconds);
    }

    private static void skipFully(DataInputStream dis, long n) throws IOException {
        long remaining = n;
        while (remaining > 0) {
            long skipped = dis.skip(remaining);
            if (skipped <= 0) {
                // Đọc bỏ từng byte nếu skip không hiệu quả
                if (dis.read() == -1) {
                    throw new EOFException("Hết luồng khi skip");
                }
                remaining--;
            } else {
                remaining -= skipped;
            }
        }
    }
}
