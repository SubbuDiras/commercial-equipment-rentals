package com.marketplace.payment;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class HttpRazorpayGateway implements RazorpayGateway {

    private final RestClient restClient;

    public HttpRazorpayGateway(RestClient razorpayRestClient) {
        this.restClient = razorpayRestClient;
    }

    @Override
    public RazorpayOrder createOrder(long amountInPaise, String receipt, String currency) {
        JsonNode response = restClient.post()
                .uri("/orders")
                .body(Map.of("amount", amountInPaise, "currency", currency, "receipt", receipt))
                .retrieve()
                .body(JsonNode.class);
        return new RazorpayOrder(response.path("id").asText(), response.path("amount").asLong(),
                response.path("currency").asText());
    }

    @Override
    public RazorpayPayment getPayment(String paymentId) {
        JsonNode response = restClient.get()
                .uri("/payments/{paymentId}", paymentId)
                .retrieve()
                .body(JsonNode.class);
        return new RazorpayPayment(response.path("id").asText(), response.path("order_id").asText(),
                response.path("amount").asLong(), response.path("currency").asText(),
                response.path("status").asText());
    }

    @Override
    public RazorpayTransfer createHeldTransfer(
            String paymentId,
            String linkedAccountId,
            long amountInPaise,
            long platformCommissionInPaise,
            long holdUntilEpochSeconds
    ) {
        JsonNode response = restClient.post()
                .uri("/payments/{paymentId}/transfers", paymentId)
                .body(Map.of("transfers", List.of(Map.of(
                        "account", linkedAccountId,
                        "amount", amountInPaise,
                        "currency", "INR",
                        "on_hold", true,
                        "on_hold_until", holdUntilEpochSeconds,
                        "notes", Map.of("platform_commission_paise", platformCommissionInPaise)))))
                .retrieve()
                .body(JsonNode.class);
        JsonNode transfer = response.path("items").isArray() && !response.path("items").isEmpty()
                ? response.path("items").get(0)
                : response;
        return new RazorpayTransfer(transfer.path("id").asText(), transfer.path("status").asText());
    }

    @Override
    public void releaseTransfer(String transferId) {
        restClient.post()
                .uri("/transfers/{transferId}/release", transferId)
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public String refund(String paymentId, long amountInPaise, String receipt) {
        JsonNode response = restClient.post()
                .uri("/payments/{paymentId}/refund", paymentId)
                .body(Map.of("amount", amountInPaise, "receipt", receipt))
                .retrieve()
                .body(JsonNode.class);
        return response.path("id").asText();
    }
}
