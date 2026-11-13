package com.kiemjob.backend.service;

import com.kiemjob.backend.config.VNPayConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class VnPayService {

    private final VNPayConfig vnPayConfig;

    public VnPayService() {
        this.vnPayConfig = null;
    }

    public VnPayService(VNPayConfig vnPayConfig) {
        this.vnPayConfig = vnPayConfig;
    }

    @Value("${vnpay.tmn-code:RPG3R6C5}")
    private String fallbackTmnCode;

    @Value("${vnpay.hash-secret:KXYUHSDBVAKZVXPFSZSJXBJQZXNHLICS}")
    private String fallbackHashSecret;

    @Value("${vnpay.payment-url:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}")
    private String fallbackPayUrl;

    @Value("${vnpay.return-url:http://localhost:8080/api/payments/vnpay/return}")
    private String fallbackReturnUrl;

    private static final DateTimeFormatter VNPAY_DATE_FMT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    public String getTmnCode() {
        return (vnPayConfig != null && vnPayConfig.getTmnCode() != null && !vnPayConfig.getTmnCode().isBlank())
                ? vnPayConfig.getTmnCode() : fallbackTmnCode;
    }

    public String getHashSecret() {
        return (vnPayConfig != null && vnPayConfig.getHashSecret() != null && !vnPayConfig.getHashSecret().isBlank())
                ? vnPayConfig.getHashSecret() : fallbackHashSecret;
    }

    public String getPayUrl() {
        return (vnPayConfig != null && vnPayConfig.getPaymentUrl() != null && !vnPayConfig.getPaymentUrl().isBlank())
                ? vnPayConfig.getPaymentUrl() : fallbackPayUrl;
    }

    public String getReturnUrl() {
        return (vnPayConfig != null && vnPayConfig.getReturnUrl() != null && !vnPayConfig.getReturnUrl().isBlank())
                ? vnPayConfig.getReturnUrl() : fallbackReturnUrl;
    }

    /**
     * Tạo URL thanh toán VNPAY tuân thủ chuẩn VNPAY 2.1.0
     *
     * @param orderId     mã đơn hàng nội bộ
     * @param amount      số tiền (VND)
     * @param orderInfo   mô tả đơn hàng
     * @param ipAddress   IP client
     * @return URL thanh toán VNPAY đầy đủ
     */
    public String createPaymentUrl(Long orderId, Long amount, String orderInfo, String ipAddress) {
        // Chuẩn hóa IP
        String clientIp = ipAddress;
        if (clientIp == null || clientIp.isBlank() || clientIp.contains(":") || "0:0:0:0:0:0:0:1".equals(clientIp)) {
            clientIp = "127.0.0.1";
        }

        // Tạo mã giao dịch duy nhất cho mỗi lần gọi để tránh lỗi "Mã GD đã tồn tại" trong Sandbox
        String txnRef = orderId + "_" + System.currentTimeMillis();

        // Chuẩn hóa orderInfo (loại bỏ ký tự đặc biệt có thể gây xung đột URL)
        String cleanOrderInfo = (orderInfo != null && !orderInfo.isBlank())
                ? orderInfo.replaceAll("[^a-zA-Z0-9\\s_-]", " ").trim().replaceAll("\\s+", " ")
                : "Thanh toan don hang " + orderId;

        Map<String, String> vnpParams = new TreeMap<>();
        vnpParams.put("vnp_Version", "2.1.0");
        vnpParams.put("vnp_Command", "pay");
        vnpParams.put("vnp_TmnCode", getTmnCode());
        vnpParams.put("vnp_Amount", String.valueOf(amount * 100)); // VNPAY yêu cầu nhân 100
        vnpParams.put("vnp_CurrCode", "VND");
        vnpParams.put("vnp_TxnRef", txnRef);
        vnpParams.put("vnp_OrderInfo", cleanOrderInfo);
        vnpParams.put("vnp_OrderType", "other");
        vnpParams.put("vnp_Locale", "vn");
        vnpParams.put("vnp_ReturnUrl", getReturnUrl());
        vnpParams.put("vnp_IpAddr", clientIp);

        LocalDateTime now = LocalDateTime.now();
        vnpParams.put("vnp_CreateDate", now.format(VNPAY_DATE_FMT));
        vnpParams.put("vnp_ExpireDate", now.plusMinutes(15).format(VNPAY_DATE_FMT));

        StringBuilder hashData = new StringBuilder();
        StringBuilder query = new StringBuilder();

        Iterator<Map.Entry<String, String>> itr = vnpParams.entrySet().iterator();
        while (itr.hasNext()) {
            Map.Entry<String, String> entry = itr.next();
            String key = entry.getKey();
            String val = entry.getValue();
            if (val != null && !val.isEmpty()) {
                String encKey = URLEncoder.encode(key, StandardCharsets.US_ASCII);
                String encVal = URLEncoder.encode(val, StandardCharsets.US_ASCII);

                hashData.append(encKey).append('=').append(encVal);
                query.append(encKey).append('=').append(encVal);

                if (itr.hasNext()) {
                    hashData.append('&');
                    query.append('&');
                }
            }
        }

        String secureHash = hmacSHA512(getHashSecret(), hashData.toString());
        query.append("&vnp_SecureHash=").append(secureHash);

        return getPayUrl() + "?" + query.toString();
    }

    /**
     * Xác minh chữ ký từ VNPAY callback (IPN / Return URL)
     *
     * @param params tất cả tham số từ VNPAY
     * @return true nếu chữ ký hợp lệ
     */
    public boolean verifySignature(Map<String, String> params) {
        String receivedHash = params.get("vnp_SecureHash");
        if (receivedHash == null || receivedHash.isEmpty()) {
            return false;
        }

        Map<String, String> sortedParams = new TreeMap<>(params);
        sortedParams.remove("vnp_SecureHash");
        sortedParams.remove("vnp_SecureHashType");

        StringBuilder hashData = new StringBuilder();
        Iterator<Map.Entry<String, String>> iterator = sortedParams.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, String> entry = iterator.next();
            String key = entry.getKey();
            String val = entry.getValue();
            if (val != null && !val.isEmpty()) {
                String encKey = URLEncoder.encode(key, StandardCharsets.US_ASCII);
                String encVal = URLEncoder.encode(val, StandardCharsets.US_ASCII);

                hashData.append(encKey).append('=').append(encVal);
                if (iterator.hasNext()) {
                    hashData.append('&');
                }
            }
        }

        String expectedHash = hmacSHA512(getHashSecret(), hashData.toString());
        return expectedHash.equalsIgnoreCase(receivedHash);
    }

    /**
     * HMAC-SHA512
     */
    public String hmacSHA512(String key, String data) {
        try {
            Mac hmac512 = Mac.getInstance("HmacSHA512");
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
            hmac512.init(secretKey);
            byte[] result = hmac512.doFinal(data.getBytes(StandardCharsets.UTF_8));

            StringBuilder sb = new StringBuilder();
            for (byte b : result) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Lỗi tạo chữ ký HMAC-SHA512", e);
        }
    }
}
