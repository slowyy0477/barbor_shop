package com.ayan.salon.server.service;

import org.junit.jupiter.api.Test;

import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Proves the exact request each free-tier provider receives without any network
 * call. The transport is the only thing replaced; the real request building,
 * number normalisation and failure handling are all exercised.
 */
class SmsDeliveryClientTest {
    private final List<HttpRequest> captured = new ArrayList<>();
    private final SmsDeliveryClient.Transport recorder = request -> {
        captured.add(request);
        return new SmsDeliveryClient.HttpResult(201, "{\"ok\":true}");
    };
    private final SmsDeliveryClient.Transport failing =
            request -> new SmsDeliveryClient.HttpResult(500, "provider exploded");

    private SmsDeliveryClient.Config twilioConfig() {
        return SmsDeliveryClient.Config.from("AC123", "secret-token", "+12025550123", "", "", "", "");
    }

    @Test
    void pakistaniNumbersAreNormalisedToE164() {
        assertEquals("+923001234567", SmsDeliveryClient.toE164("0300 1234567"));
        assertEquals("+923001234567", SmsDeliveryClient.toE164("03001234567"));
        assertEquals("+923001234567", SmsDeliveryClient.toE164("+92 300 1234567"));
        assertEquals("+923001234567", SmsDeliveryClient.toE164("00923001234567"));
        assertThrows(IllegalArgumentException.class, () -> SmsDeliveryClient.toE164("12345"));
    }

    @Test
    void twilioRequestUsesBasicAuthFormBodyAndInternationalNumber() throws Exception {
        SmsDeliveryClient client = new SmsDeliveryClient(SmsDeliveryClient.Provider.TWILIO, twilioConfig(), recorder);

        client.send("03001234567", "Ayan Salon: your sign-in code is 424242");

        HttpRequest request = captured.get(0);
        assertEquals("https://api.twilio.com/2010-04-01/Accounts/AC123/Messages.json", request.uri().toString());
        String expected = "Basic " + Base64.getEncoder()
                .encodeToString("AC123:secret-token".getBytes(StandardCharsets.UTF_8));
        assertEquals(expected, request.headers().firstValue("Authorization").orElse(""));
        assertTrue(request.headers().firstValue("Content-Type").orElse("").startsWith("application/x-www-form-urlencoded"));
        String body = bodyOf(request);
        assertTrue(body.contains("To=%2B923001234567"), body);
        assertTrue(body.contains("From=%2B12025550123"), body);
        assertTrue(body.contains("424242"), body);
    }

    @Test
    void whatsappRequestUsesBearerTokenAndJsonBody() throws Exception {
        SmsDeliveryClient.Config config = SmsDeliveryClient.Config.from("", "", "", "wa-token", "555000111", "", "");
        SmsDeliveryClient client = new SmsDeliveryClient(SmsDeliveryClient.Provider.WHATSAPP_CLOUD, config, recorder);

        client.send("+92 300 1234567", "Ayan Salon: your sign-in code is 111222");

        HttpRequest request = captured.get(0);
        assertEquals("https://graph.facebook.com/v21.0/555000111/messages", request.uri().toString());
        assertEquals("Bearer wa-token", request.headers().firstValue("Authorization").orElse(""));
        String body = bodyOf(request);
        assertTrue(body.contains("\"messaging_product\":\"whatsapp\""), body);
        assertTrue(body.contains("\"to\":\"923001234567\""), body);
        assertTrue(body.contains("111222"), body);
    }

    @Test
    void webhookRequestSendsToAndMessageWithBearerToken() throws Exception {
        SmsDeliveryClient.Config config = SmsDeliveryClient.Config.from("", "", "", "", "",
                "https://sms.example.pk/send", "local-token");
        SmsDeliveryClient client = new SmsDeliveryClient(SmsDeliveryClient.Provider.WEBHOOK, config, recorder);

        client.send("03001234567", "Ayan Salon: your sign-in code is 909090");

        HttpRequest request = captured.get(0);
        assertEquals("https://sms.example.pk/send", request.uri().toString());
        assertEquals("Bearer local-token", request.headers().firstValue("Authorization").orElse(""));
        String body = bodyOf(request);
        assertEquals("{\"to\":\"+923001234567\",\"message\":\"Ayan Salon: your sign-in code is 909090\"}", body);
    }

    @Test
    void unfinishedConfigurationFailsInsteadOfPretendingToSend() {
        SmsDeliveryClient halfConfigured = new SmsDeliveryClient(SmsDeliveryClient.Provider.TWILIO,
                SmsDeliveryClient.Config.from("AC123", "", "", "", "", "", ""), recorder);

        assertFalse(halfConfigured.isConfigured());
        assertThrows(IllegalStateException.class, () -> halfConfigured.send("03001234567", "code"));
        assertTrue(captured.isEmpty(), "nothing may be sent from an unfinished provider");
    }

    @Test
    void providerErrorIsReportedSoTheOutboxCanRetry() {
        SmsDeliveryClient client = new SmsDeliveryClient(SmsDeliveryClient.Provider.TWILIO, twilioConfig(), failing);

        SmsDeliveryClient.SmsDeliveryException failure = assertThrows(SmsDeliveryClient.SmsDeliveryException.class,
                () -> client.send("03001234567", "Ayan Salon: your sign-in code is 123123"));

        assertTrue(failure.getMessage().contains("500"), failure.getMessage());
        assertFalse(failure.getMessage().contains("123123"), "the customer code must never appear in an error message");
    }

    @Test
    void providerNamesFromConfigurationAreUnderstood() {
        assertEquals(SmsDeliveryClient.Provider.TWILIO, SmsDeliveryClient.parseProvider("twilio"));
        assertEquals(SmsDeliveryClient.Provider.WHATSAPP_CLOUD, SmsDeliveryClient.parseProvider("WhatsApp"));
        assertEquals(SmsDeliveryClient.Provider.WEBHOOK, SmsDeliveryClient.parseProvider("webhook"));
        assertEquals(SmsDeliveryClient.Provider.NONE, SmsDeliveryClient.parseProvider(""));
        assertEquals(SmsDeliveryClient.Provider.NONE, SmsDeliveryClient.parseProvider("local"));
    }

    private static String bodyOf(HttpRequest request) {
        return request.bodyPublisher()
                .map(publisher -> {
                    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                    try {
                        publisher.subscribe(new java.util.concurrent.Flow.Subscriber<>() {
                            public void onSubscribe(java.util.concurrent.Flow.Subscription subscription) {
                                subscription.request(Long.MAX_VALUE);
                            }
                            public void onNext(java.nio.ByteBuffer item) {
                                byte[] chunk = new byte[item.remaining()];
                                item.get(chunk);
                                out.writeBytes(chunk);
                            }
                            public void onError(Throwable throwable) { }
                            public void onComplete() { }
                        });
                    } catch (RuntimeException ignored) { }
                    return out.toString(StandardCharsets.UTF_8);
                })
                .orElse("");
    }
}
