package com.kiemjob.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "vnpay")
public class VNPayConfig {

    private String tmnCode;

    private String hashSecret;

    private String paymentUrl;

    private String version;

    private String currencyCode;

    private String locale;

    private String orderType;

    private String returnUrl;
}