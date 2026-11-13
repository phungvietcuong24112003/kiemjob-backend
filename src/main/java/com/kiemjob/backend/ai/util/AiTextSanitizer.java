package com.kiemjob.backend.ai.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class AiTextSanitizer {

    private AiTextSanitizer() {}

    /**
     * Che các thông tin cá nhân nhạy cảm (email, số điện thoại) trong văn bản trước khi gửi đến AI.
     */
    public static String maskPii(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        // Che email
        String result = text.replaceAll("(?i)[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}", "[EMAIL]");
        // Che số điện thoại Việt Nam (10-11 chữ số bắt đầu bằng 0 hoặc +84)
        result = result.replaceAll("(\\+84|0)(3[2-9]|5[6|8|9]|7[0|6-9]|8[1-9]|9[0-9])[0-9]{7}", "[PHONE]");
        result = result.replaceAll("(\\+84|0)[0-9\\s.-]{9,13}", "[PHONE]");
        return result;
    }

    /**
     * Loại bỏ các thẻ HTML nhưng bảo toàn cấu trúc phân dòng và đoạn văn.
     */
    public static String stripHtmlPreservingBreaks(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        return html.replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</p>", "\n\n")
                .replaceAll("(?i)</li>", "\n")
                .replaceAll("(?i)</div>", "\n")
                .replaceAll("<[^>]*>", "")
                .replaceAll("&nbsp;", " ")
                .replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("&quot;", "\"")
                .replaceAll("\\r\\n", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    /**
     * Tính toán chuỗi mã băm SHA-256 hex.
     */
    public static String sha256Hex(String input) {
        if (input == null) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Thuật toán SHA-256 không khả dụng", e);
        }
    }
}

