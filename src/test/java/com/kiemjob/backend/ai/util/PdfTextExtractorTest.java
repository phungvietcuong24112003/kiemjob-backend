package com.kiemjob.backend.ai.util;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.Normalizer;

import static org.junit.jupiter.api.Assertions.*;

class PdfTextExtractorTest {

    private PdfTextExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new PdfTextExtractor();
    }

    private byte[] createSamplePdf(String... pageTexts) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            for (String text : pageTexts) {
                PDPage page = new PDPage();
                doc.addPage(page);
                if (text != null && !text.isBlank()) {
                    try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                        cs.beginText();
                        cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                        cs.newLineAtOffset(50, 700);
                        cs.showText(text);
                        cs.endText();
                    }
                }
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    @Test
    @DisplayName("1. Trích xuất text từ PDF mẫu và che đúng Email, Số điện thoại")
    void testExtractText_SuccessAndMasking() throws IOException {
        String content = "Candidate Profile: Developer email hr@kiemjob.com phone 0912345678";
        byte[] pdfBytes = createSamplePdf(content);

        String result = extractor.extractText(pdfBytes);

        assertNotNull(result);
        assertTrue(result.contains("Candidate Profile: Developer"));
        assertTrue(result.contains("[email]"));
        assertTrue(result.contains("[phone]"));
        assertFalse(result.contains("hr@kiemjob.com"));
        assertFalse(result.contains("0912345678"));
    }

    @Test
    @DisplayName("2. Kiểm tra che các định dạng số điện thoại Việt Nam (+84, 0xxx, có dấu chấm/khoảng trắng)")
    void testMaskSensitiveInfo_VietnamesePhoneFormats() {
        String input = """
                Nguyen Van A
                Email: nguyen.van.a@gmail.com
                Mobile 1: 0987654321
                Mobile 2: +84912345678
                Mobile 3: 0912 345 678
                Mobile 4: 0912.345.678
                Landline: 024.3852.1234
                Year: 2024
                Total: 1000000 VND
                """;

        String masked = extractor.cleanText(input);

        // Email masked
        assertTrue(masked.contains("Email: [email]"));
        assertFalse(masked.contains("nguyen.van.a@gmail.com"));

        // Phones masked
        assertTrue(masked.contains("Mobile 1: [phone]"));
        assertTrue(masked.contains("Mobile 2: [phone]"));
        assertTrue(masked.contains("Mobile 3: [phone]"));
        assertTrue(masked.contains("Mobile 4: [phone]"));
        assertTrue(masked.contains("Landline: [phone]"));

        // Non-phone numbers preserved
        assertTrue(masked.contains("Year: 2024"));
        assertTrue(masked.contains("Total: 1000000 VND"));
    }

    @Test
    @DisplayName("3. Chuẩn hóa tiếng Việt có dấu về dạng Unicode NFC và làm sạch ký tự điều khiển")
    void testCleanText_VietnameseUnicodeNfcAndControlChars() {
        // Chuỗi tiếng Việt dạng Decomposed (NFD): "Nguye" + U+0302 (^) + U+0303 (~) + "n Va" + U+0306 (breve) + "n"
        String nfdText = "Nguye\u0302\u0303n Va\u0306n A \u0000 \uFFFD \u0007 Lap trinh Java";
        String cleaned = extractor.cleanText(nfdText);

        // Đảm bảo là NFC
        assertTrue(Normalizer.isNormalized(cleaned, Normalizer.Form.NFC));
        assertTrue(cleaned.contains("Nguyễn Văn A"));
        // Loại bỏ null byte và control chars
        assertFalse(cleaned.contains("\u0000"));
        assertFalse(cleaned.contains("\uFFFD"));
        assertFalse(cleaned.contains("\u0007"));
    }

    @Test
    @DisplayName("4. Giới hạn tối đa 10 trang: PDF 12 trang chỉ trích xuất từ trang 1 đến 10")
    void testExtractText_Max10PagesLimit() throws IOException {
        String[] pages = new String[12];
        for (int i = 0; i < 12; i++) {
            pages[i] = "PageSection_" + (i + 1);
        }
        byte[] pdfBytes = createSamplePdf(pages);

        String result = extractor.extractText(pdfBytes);

        // Trang 1 và trang 10 có trong kết quả
        assertTrue(result.contains("PageSection_1"));
        assertTrue(result.contains("PageSection_10"));
        // Trang 11 và 12 bị bỏ qua
        assertFalse(result.contains("PageSection_11"));
        assertFalse(result.contains("PageSection_12"));
    }

    @Test
    @DisplayName("5. File PDF rỗng hoặc mảng byte null -> Ném ResponseStatusException 422")
    void testExtractText_EmptyBytes_Throws422() {
        ResponseStatusException ex1 = assertThrows(ResponseStatusException.class, () ->
                extractor.extractText(null)
        );
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex1.getStatusCode());

        ResponseStatusException ex2 = assertThrows(ResponseStatusException.class, () ->
                extractor.extractText(new byte[0])
        );
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex2.getStatusCode());
    }

    @Test
    @DisplayName("6. File không phải định dạng PDF hợp lệ -> Ném ResponseStatusException 422")
    void testExtractText_InvalidPdfFormat_Throws422() {
        byte[] invalidBytes = "This is definitely not a PDF file content".getBytes();
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                extractor.extractText(invalidBytes)
        );
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatusCode());
    }

    @Test
    @DisplayName("7. PDF bị khóa quyền trích xuất nội dung -> Ném ResponseStatusException 422")
    void testExtractText_LockedPermission_Throws422() throws IOException {
        byte[] lockedPdfBytes;
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 700);
                cs.showText("Protected confidential content");
                cs.endText();
            }

            AccessPermission ap = new AccessPermission();
            ap.setCanExtractContent(false);
            StandardProtectionPolicy spp = new StandardProtectionPolicy("ownerPass123", "", ap);
            doc.protect(spp);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            lockedPdfBytes = baos.toByteArray();
        }

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                extractor.extractText(lockedPdfBytes)
        );
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatusCode());
        assertTrue(ex.getReason() != null && ex.getReason().contains("khóa quyền"));
    }
}
