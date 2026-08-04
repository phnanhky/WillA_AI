package com.willa.ai.backend.util;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileMagicValidatorTest {

    @Test
    void rejectsTextRenamedAsJpg() {
        byte[] fake = "hello this is a text file pretending to be jpg".getBytes(StandardCharsets.UTF_8);
        assertEquals(FileMagicValidator.Kind.UNKNOWN, FileMagicValidator.detect(fake));
        assertFalse(FileMagicValidator.isImage(fake));
        assertThrows(IllegalArgumentException.class, () -> FileMagicValidator.requireImage(fake, "spoof.jpg"));
        assertThrows(IllegalArgumentException.class, () -> FileMagicValidator.requireChatUpload(fake, "spoof.jpg"));
    }

    @Test
    void acceptsJpegMagic() {
        byte[] jpeg = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0};
        assertEquals(FileMagicValidator.Kind.JPEG, FileMagicValidator.detect(jpeg));
        assertTrue(FileMagicValidator.isImage(jpeg));
        FileMagicValidator.requireImage(jpeg, "photo.jpg");
    }

    @Test
    void acceptsPngMagic() {
        byte[] png = new byte[] {
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0
        };
        assertEquals(FileMagicValidator.Kind.PNG, FileMagicValidator.detect(png));
        FileMagicValidator.requireChatUpload(png, "a.png");
    }

    @Test
    void acceptsPdfMagic() {
        byte[] pdf = "%PDF-1.4 fake".getBytes(StandardCharsets.US_ASCII);
        assertEquals(FileMagicValidator.Kind.PDF, FileMagicValidator.detect(pdf));
        assertEquals(FileMagicValidator.Kind.PDF, FileMagicValidator.requireChatUpload(pdf, "doc.pdf"));
    }
}
