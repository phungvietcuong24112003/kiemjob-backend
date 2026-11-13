package com.kiemjob.backend.security;

import com.kiemjob.backend.entity.Account;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey secretKey;
    private final long expirationMs;

    public JwtService(
            @Value("${app.jwt.secret-base64}") String secret,
            @Value("${app.jwt.expiration-ms:1800000}") long expirationMs
    ) {
        this.secretKey = Keys.hmacShaKeyFor(
                Decoders.BASE64.decode(secret)
        );
        this.expirationMs = expirationMs;
    }

    public String generateToken(Account account) {
        Instant now = Instant.now();

        return Jwts.builder()
                .subject(account.getEmail())
                .claim("accountId", account.getId())
                .claim("role", account.getRole())
                .issuedAt(Date.from(now))
                .expiration(Date.from(
                        now.plusMillis(expirationMs)
                ))
                .signWith(secretKey, Jwts.SIG.HS512)
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long getExpirationMs() {
        return expirationMs;
    }
}