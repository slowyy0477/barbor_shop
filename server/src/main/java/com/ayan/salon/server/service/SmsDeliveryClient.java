package com.ayan.salon.server.service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;

/**
 * Sends one verification message through a free-tier provider.
 *
 * <p>Only provider credentials are read from configuration; the message text and
 * the customer number are never persisted or logged by this class. Three
 * providers are supported so a salon in Pakistan can start on a free trial and
 * move to a local gateway later without touching the app:
 *
 * <ul>
 *   <li>{@code TWILIO} - Twilio Programmable SMS (free trial credit)</li>
 *   <li>{@code WHATSAPP_CLOUD} - Meta WhatsApp Cloud API (free service tier)</li>
 *   <li>{@code WEBHOOK} - any provider that accepts JSON {to, message}</li>
 * </ul>
 *
 * <p>The transport is injectable so tests prove the exact request without a
 * network call.
 */
public final class SmsDeliveryClient {
    public enum Provider { NONE, TWILIO, WHATSAPP_CLOUD, WEBHOOK }

    public record Config(String twilioAccountSid, String twilioAuthToken, String twilioFromNumber,
                         String whatsappToken, String whatsappPhoneNumberId,
                         String webhookUrl, String webhookToken) {
        public static Config from(String sid, String token, String from, String waToken, String waPhoneId,
                                  String webhookUrl, String webhookToken) {
            return new Config(clean(sid), clean(token), clean(from), clean(waToken), clean(waPhoneId),
                    clean(webhookUrl), clean(webhookToken));
        }

        private static String clean(String value) { return value == null ? "" : value.trim(); }
    }

    public record HttpResult(int status, String body) {}

    @FunctionalInterface
    public interface Transport {
        HttpResult send(HttpRequest request) throws IOException, InterruptedException;
    }

    /** Delivery failed at the provider; the outbox keeps the message for a retry. */
    public static class SmsDeliveryException extends RuntimeException {
        public SmsDeliveryException(String message) { super(message); }
    }

    private static final Transport JVM_TRANSPORT = request -> {
        HttpResponse<String> response = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build()
                .send(request, HttpResponse.BodyHandlers.ofString());
        return new HttpResult(response.statusCode(), response.body());
    };

    private final Provider provider;
    private final Config config;
    private final Transport transport;

    public SmsDeliveryClient(Provider provider, Config config, Transport transport) {
        this.provider = provider == null ? Provider.NONE : provider;
        this.config = config == null ? Config.from("", "", "", "", "", "", "") : config;
        this.transport = transport == null ? JVM_TRANSPORT : transport;
    }

    public static SmsDeliveryClient from(String providerName, Config config) {
        return new SmsDeliveryClient(parseProvider(providerName), config, JVM_TRANSPORT);
    }

    public static Provider parseProvider(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return switch (normalized) {
            case "TWILIO" -> Provider.TWILIO;
            case "WHATSAPP", "WHATSAPP_CLOUD", "META" -> Provider.WHATSAPP_CLOUD;
            case "WEBHOOK", "HTTP", "GENERIC" -> Provider.WEBHOOK;
            default -> Provider.NONE;
        };
    }

    public Provider provider() { return provider; }

    public String providerName() { return provider.name(); }

    /** True when the selected provider has every value it needs. */
    public boolean isConfigured() {
        return switch (provider) {
            case TWILIO -> !config.twilioAccountSid().isEmpty() && !config.twilioAuthToken().isEmpty()
                    && !config.twilioFromNumber().isEmpty();
            case WHATSAPP_CLOUD -> !config.whatsappToken().isEmpty() && !config.whatsappPhoneNumberId().isEmpty();
            case WEBHOOK -> !config.webhookUrl().isEmpty();
            case NONE -> false;
        };
    }

    /**
     * Normalises a Pakistani mobile number to E.164. Twilio and WhatsApp both
     * reject local formats, and a wrongly formatted number is a silent lost
     * customer, so this is deliberately strict.
     */
    public static String toE164(String raw) {
        String digits = raw == null ? "" : raw.replaceAll("[^0-9+]", "");
        if (digits.startsWith("+")) digits = digits.substring(1);
        if (digits.startsWith("0092")) digits = digits.substring(4);
        else if (digits.startsWith("00")) digits = digits.substring(2);
        if (digits.startsWith("92")) digits = digits.substring(2);
        if (digits.startsWith("0")) digits = digits.substring(1);
        if (digits.length() < 9 || digits.length() > 12) {
            throw new IllegalArgumentException("Mobile number cannot be normalised to an international format");
        }
        return "+92" + digits;
    }

    public void send(String phone, String message) {
        if (!isConfigured()) {
            throw new IllegalStateException("SMS provider " + provider + " is not configured; message was not sent");
        }
        String to = toE164(phone);
        HttpResult result = switch (provider) {
            case TWILIO -> deliverTwilio(to, message);
            case WHATSAPP_CLOUD -> deliverWhatsApp(to, message);
            case WEBHOOK -> deliverWebhook(to, message);
            case NONE -> throw new IllegalStateException("No SMS provider is selected");
        };
        if (result.status() < 200 || result.status() >= 300) {
            // The body is provider diagnostics; it never contains the customer code
            // because the code is only inside the request we sent.
            throw new SmsDeliveryException("SMS provider " + provider + " rejected the message with HTTP "
                    + result.status() + ": " + trim(result.body()));
        }
    }

    private HttpResult deliverTwilio(String to, String message) {
        String endpoint = "https://api.twilio.com/2010-04-01/Accounts/" + config.twilioAccountSid() + "/Messages.json";
        String form = "To=" + encode(to) + "&From=" + encode(config.twilioFromNumber()) + "&Body=" + encode(message);
        String basic = Base64.getEncoder()
                .encodeToString((config.twilioAccountSid() + ":" + config.twilioAuthToken()).getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Basic " + basic)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                .build();
        return call(request);
    }

    private HttpResult deliverWhatsApp(String to, String message) {
        String endpoint = "https://graph.facebook.com/v21.0/" + config.whatsappPhoneNumberId() + "/messages";
        String body = "{\"messaging_product\":\"whatsapp\",\"recipient_type\":\"individual\",\"to\":\""
                + json(to.replace("+", "")) + "\",\"type\":\"text\",\"text\":{\"preview_url\":false,\"body\":\""
                + json(message) + "\"}}";
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + config.whatsappToken())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        return call(request);
    }

    private HttpResult deliverWebhook(String to, String message) {
        String body = "{\"to\":\"" + json(to) + "\",\"message\":\"" + json(message) + "\"}";
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(config.webhookUrl()))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json");
        if (!config.webhookToken().isEmpty()) builder.header("Authorization", "Bearer " + config.webhookToken());
        return call(builder.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build());
    }

    private HttpResult call(HttpRequest request) {
        try {
            return transport.send(request);
        } catch (IOException failure) {
            throw new SmsDeliveryException("SMS provider " + provider + " could not be reached: " + failure.getMessage());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new SmsDeliveryException("SMS delivery was interrupted");
        }
    }

    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }

    private static String json(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    private static String trim(String value) {
        if (value == null) return "";
        String clean = value.strip();
        return clean.length() > 300 ? clean.substring(0, 300) : clean;
    }
}
