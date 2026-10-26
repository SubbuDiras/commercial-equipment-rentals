package com.marketplace.payment;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class RazorpaySignatureVerifier {

    public boolean verify(String message, String suppliedSignature, String secret) {
        if (message == null || suppliedSignature == null || secret == null || secret.isBlank()) {
            return false;
        }
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = hmac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            byte[] supplied = decodeHex(suppliedSignature);
            return supplied != null && MessageDigest.isEqual(expected, supplied);
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to verify Razorpay signature.", exception);
        }
    }

    public String sign(String message, String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("Razorpay signature secret is not configured.");
        }
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = hmac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            StringBuilder signature = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                signature.append(String.format("%02x", value));
            }
            return signature.toString();
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to sign Razorpay request.", exception);
        }
    }

    private static byte[] decodeHex(String value) {
        if ((value.length() & 1) != 0) {
            return null;
        }
        byte[] output = new byte[value.length() / 2];
        for (int index = 0; index < value.length(); index += 2) {
            int high = Character.digit(value.charAt(index), 16);
            int low = Character.digit(value.charAt(index + 1), 16);
            if (high < 0 || low < 0) {
                return null;
            }
            output[index / 2] = (byte) ((high << 4) + low);
        }
        return output;
    }
}
