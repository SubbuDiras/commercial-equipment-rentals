package com.rental.Marketplace;

import com.marketplace.payment.RazorpaySignatureVerifier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RazorpaySignatureVerifierTests {

    private final RazorpaySignatureVerifier verifier = new RazorpaySignatureVerifier();

    @Test
    void verifiesRazorpayHmacAndRejectsTamperedOrMalformedSignatures() {
        String signature = verifier.sign("{\"event\":\"payment.captured\"}", "test-webhook-secret");

        assertThat(verifier.verify("{\"event\":\"payment.captured\"}", signature, "test-webhook-secret")).isTrue();
        assertThat(verifier.verify("{\"event\":\"payment.failed\"}", signature, "test-webhook-secret")).isFalse();
        assertThat(verifier.verify("{\"event\":\"payment.captured\"}", "not-hex", "test-webhook-secret")).isFalse();
        assertThat(verifier.verify("{\"event\":\"payment.captured\"}", signature, "")).isFalse();
    }
}
