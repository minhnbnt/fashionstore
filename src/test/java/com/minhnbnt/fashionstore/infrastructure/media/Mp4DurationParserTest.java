package com.minhnbnt.fashionstore.infrastructure.media;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class Mp4DurationParserTest {

    private byte[] createSampleMp4(long timescale, long duration, int version) throws IOException {
        var baos = new ByteArrayOutputStream();
        var dos = new DataOutputStream(baos);

        // ftyp box (dummy 16 bytes)
        dos.writeInt(16);
        dos.writeBytes("ftyp");
        dos.writeBytes("isom");
        dos.writeInt(512);

        // moov box containing mvhd
        var mvhdBaos = new ByteArrayOutputStream();
        var mvhdDos = new DataOutputStream(mvhdBaos);

        mvhdDos.writeByte(version); // version
        mvhdDos.writeByte(0); // flags
        mvhdDos.writeByte(0);
        mvhdDos.writeByte(0);

        if (version == 1) {
            mvhdDos.writeLong(0); // creation_time
            mvhdDos.writeLong(0); // modification_time
            mvhdDos.writeInt((int) timescale);
            mvhdDos.writeLong(duration);
        } else {
            mvhdDos.writeInt(0); // creation_time
            mvhdDos.writeInt(0); // modification_time
            mvhdDos.writeInt((int) timescale);
            mvhdDos.writeInt((int) duration);
        }
        // write some extra trailing bytes in mvhd
        mvhdDos.write(new byte[80]);

        byte[] mvhdPayload = mvhdBaos.toByteArray();
        int mvhdBoxSize = 8 + mvhdPayload.length;

        int moovBoxSize = 8 + mvhdBoxSize;

        // write moov box
        dos.writeInt(moovBoxSize);
        dos.writeBytes("moov");

        // write mvhd box
        dos.writeInt(mvhdBoxSize);
        dos.writeBytes("mvhd");
        dos.write(mvhdPayload);

        return baos.toByteArray();
    }

    @Test
    void parseDuration_version0() throws IOException {
        // 45 seconds at 1000 timescale = 45000
        byte[] mp4Bytes = createSampleMp4(1000, 45000, 0);
        var duration = Mp4DurationParser.parseDurationSeconds(new ByteArrayInputStream(mp4Bytes));

        assertThat(duration).isPresent();
        assertThat(duration.getAsDouble()).isEqualTo(45.0);
    }

    @Test
    void parseDuration_version1() throws IOException {
        // 75 seconds at 600 timescale = 45000
        byte[] mp4Bytes = createSampleMp4(600, 45000, 1);
        var duration = Mp4DurationParser.parseDurationSeconds(new ByteArrayInputStream(mp4Bytes));

        assertThat(duration).isPresent();
        assertThat(duration.getAsDouble()).isEqualTo(75.0);
    }

    @Test
    void parseDuration_emptyOrGarbage_returnsEmpty() {
        var duration = Mp4DurationParser.parseDurationSeconds(new ByteArrayInputStream(new byte[]{1, 2, 3, 4}));
        assertThat(duration).isEmpty();
    }
}
