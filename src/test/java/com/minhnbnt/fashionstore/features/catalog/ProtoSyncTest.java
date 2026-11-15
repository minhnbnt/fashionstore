package com.minhnbnt.fashionstore.features.catalog;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Proto phải giống bản canonical bên embed-worker; repo tách rời nên chỉ so khi có checkout. */
class ProtoSyncTest {

    private static final Path OWN = Path.of("src/main/proto/fashion_clip/v1/embedding.proto");
    private static final Path CANONICAL =
        Path.of("../embed-worker/proto/fashion_clip/v1/embedding.proto");

    @Test
    void copyMatchesCanonical() throws Exception {
        assumeTrue(Files.isRegularFile(CANONICAL), "không có checkout embed-worker");
        assertThat(digest(OWN)).isEqualTo(digest(CANONICAL));
    }

    private static String digest(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
}
