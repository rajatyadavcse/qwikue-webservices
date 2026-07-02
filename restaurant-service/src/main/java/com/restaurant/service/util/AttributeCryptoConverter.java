package com.restaurant.service.util;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Converter
public class AttributeCryptoConverter implements AttributeConverter<String, String> {

    private static final Logger log = LoggerFactory.getLogger(AttributeCryptoConverter.class);
    private static final String ALGORITHM = "AES";
    private final SecretKeySpec keySpec;

    public AttributeCryptoConverter() {
        // Read key from environment variable, fallback to default if not configured
        String encryptionKey = System.getenv("APP_ENCRYPTION_KEY");
        if (encryptionKey == null || encryptionKey.isBlank()) {
            log.warn("APP_ENCRYPTION_KEY environment variable is not set. Using fallback default key.");
            encryptionKey = "DefaultSecKey123"; // exactly 16 bytes fallback
        }

        byte[] keyBytes = encryptionKey.getBytes(StandardCharsets.UTF_8);
        byte[] finalKey = new byte[16]; // Use AES-128 (16 bytes)
        System.arraycopy(keyBytes, 0, finalKey, 0, Math.min(keyBytes.length, 16));
        
        // Pad with zeros if shorter than 16 bytes
        if (keyBytes.length < 16) {
            for (int i = keyBytes.length; i < 16; i++) {
                finalKey[i] = 0;
            }
        }
        
        this.keySpec = new SecretKeySpec(finalKey, ALGORITHM);
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null || attribute.isBlank()) {
            return null;
        }
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            byte[] encrypted = cipher.doFinal(attribute.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            log.error("Encryption failed for attribute", e);
            throw new IllegalStateException("Failed to encrypt attribute: " + e.getMessage(), e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(dbData));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Decryption failed for database value", e);
            throw new IllegalStateException("Failed to decrypt database value: " + e.getMessage(), e);
        }
    }
}
