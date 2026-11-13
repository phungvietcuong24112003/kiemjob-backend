package com.kiemjob.backend.service;

import com.kiemjob.backend.config.VNPayConfig;
import org.junit.jupiter.api.Test;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

public class VnPayServiceTest {

    @Test
    public void testHashMatchesNodeJsVnPayPackage() {
        String tmnCode = "09OJ9J27";
        String hashSecret = "NQA0XKHWWDP6OFJHN6H0TT4C8BGMEZNW";

        Map<String, String> vnpParams = new TreeMap<>();
        vnpParams.put("vnp_Version", "2.1.0");
        vnpParams.put("vnp_Command", "pay");
        vnpParams.put("vnp_TmnCode", tmnCode);
        vnpParams.put("vnp_Amount", "5000000");
        vnpParams.put("vnp_CurrCode", "VND");
        vnpParams.put("vnp_TxnRef", "123");
        vnpParams.put("vnp_OrderInfo", "Thanh toan don hang 123");
        vnpParams.put("vnp_OrderType", "other");
        vnpParams.put("vnp_Locale", "vn");
        vnpParams.put("vnp_ReturnUrl", "http://localhost:8080/api/vnpay/return");
        vnpParams.put("vnp_IpAddr", "127.0.0.1");
        vnpParams.put("vnp_CreateDate", "20261030223000");
        vnpParams.put("vnp_ExpireDate", "20261030224500");

        StringBuilder hashData = new StringBuilder();
        StringBuilder query = new StringBuilder();

        var itr = vnpParams.entrySet().iterator();
        while (itr.hasNext()) {
            var entry = itr.next();
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

        VNPayConfig config = new VNPayConfig();
        config.setTmnCode(tmnCode);
        config.setHashSecret(hashSecret);

        VnPayService service = new VnPayService(config);
        String calculatedHash = service.hmacSHA512(hashSecret, hashData.toString());

        String expectedHash = "c3909f5d298f6e03da84ef99b156fa34b9445e0e7698d49a771399d280e0aabad9b4a692d4e898ea5ce6e992d40e23d6688e10cb0cf590639e5c24b6a4b131bd";

        System.out.println("Calculated: " + calculatedHash);
        System.out.println("Expected:   " + expectedHash);
        assertEquals(expectedHash, calculatedHash);
    }
}
