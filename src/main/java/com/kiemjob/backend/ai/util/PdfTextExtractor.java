package com.kiemjob.backend.ai.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Trích xuất và làm sạch nội dung văn bản từ file PDF của ứng viên bằng Apache PDFBox 3.x.
 * - Giới hạn tối đa 10 trang
 * - Kiểm tra quyền trích xuất nội dung (AccessPermission)
 * - Chuẩn hóa Unicode tiếng Việt NFC, bỏ ký tự rác và điều khiển
 * - Che thông tin cá nhân nhạy cảm (email, số điện thoại) trước khi gửi AI
 * - Không log nội dung CV (chỉ log độ dài)
 */
@Slf4j
@Component
public class PdfTextExtractor {

    private static final int MAX_PAGES = 10;

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "(?i)[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}"
    );

    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "(?<![0-9a-zA-Z])(?:\\+84|84|0)(?:[.\\s-]?[0-9]){8,10}(?![0-9a-zA-Z])"
    );

    private static final Pattern CONTROL_CHARS_PATTERN = Pattern.compile(
            "[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]"
    );

    private static final Pattern MULTI_SPACES_PATTERN = Pattern.compile(
            "[^\\S\\r\\n]+"
    );

    private static final Pattern LINE_TRIM_PATTERN = Pattern.compile(
            "(?m)^[ \\t]+|[ \\t]+$"
    );

    private static final Pattern EXCESS_NEWLINES_PATTERN = Pattern.compile(
            "(\\r?\\n){3,}"
    );

    /**
     * Trích xuất văn bản từ byte array của file PDF và làm sạch.
     */
    public String extractText(byte[] pdfBytes) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Dữ liệu file PDF trống."
            );
        }

        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            if (document.getCurrentAccessPermission() != null
                    && !document.getCurrentAccessPermission().canExtractContent()) {
                throw new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "File PDF bị khóa quyền trích xuất nội dung."
                );
            }

            int totalPages = document.getNumberOfPages();
            int pagesToExtract = Math.min(totalPages, MAX_PAGES);

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setStartPage(1);
            stripper.setEndPage(pagesToExtract);

            String rawText = stripper.getText(document);
            String cleanedText = cleanText(rawText);

            log.info("Đã trích xuất và làm sạch {} ký tự từ file PDF (Tổng số trang: {}, đã trích xuất: {} trang)",
                    cleanedText.length(), totalPages, pagesToExtract);

            return cleanedText;
        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (IOException ex) {
            log.error("Lỗi khi đọc file PDF: {}", ex.getMessage());
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Không thể đọc nội dung file PDF. File có thể bị hỏng hoặc sai định dạng: " + ex.getMessage(),
                    ex
            );
        }
    }

    /**
     * Làm sạch text:
     * - Bỏ \u0000, \uFFFD và các ký tự điều khiển
     * - Chuẩn hóa Unicode NFC cho tiếng Việt
     * - Gộp khoảng trắng và dòng trống thừa
     * - Che email và số điện thoại
     */
    public String cleanText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        // 1. Loại bỏ null byte và Unicode replacement character
        String result = text.replace("\u0000", "").replace("\uFFFD", "");

        // 2. Loại bỏ các ký tự điều khiển (ngoại trừ newline, carriage return, tab)
        result = CONTROL_CHARS_PATTERN.matcher(result).replaceAll("");

        // 3. Chuẩn hóa Unicode NFC (quan trọng đối với tiếng Việt có dấu)
        result = Normalizer.normalize(result, Normalizer.Form.NFC);

        // 4. Gộp khoảng trắng thừa trong cùng 1 dòng
        result = MULTI_SPACES_PATTERN.matcher(result).replaceAll(" ");

        // 5. Trim khoảng trắng đầu/cuối mỗi dòng
        result = LINE_TRIM_PATTERN.matcher(result).replaceAll("");

        // 6. Gộp các dòng trống liên tiếp (tối đa 2 dấu xuống dòng)
        result = EXCESS_NEWLINES_PATTERN.matcher(result).replaceAll("\n\n");

        // 7. Che email và số điện thoại để bảo vệ thông tin cá nhân
        result = maskSensitiveInfo(result);

        return result.trim();
    }

    /**
     * Che email và số điện thoại (hỗ trợ đầu số VN 0xxx, +84) thành [email] và [phone].
     */
    public String maskSensitiveInfo(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        // Thay email trước
        String masked = EMAIL_PATTERN.matcher(text).replaceAll("[email]");
        // Thay số điện thoại sau
        masked = PHONE_PATTERN.matcher(masked).replaceAll("[phone]");
        return masked;
    }
}

